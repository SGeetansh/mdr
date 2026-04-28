package com.payu.mdr;

import com.payu.mdr.entity.MdrPricingRule;
import com.payu.mdr.entity.RawTransaction;
import com.payu.mdr.repository.MdrPricingRuleRepository;
import com.payu.mdr.service.MdrRuleEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MdrRuleEngineTest {

    @Mock
    private MdrPricingRuleRepository ruleRepository;

    @InjectMocks
    private MdrRuleEngineService ruleEngineService;

    private MdrPricingRule defaultRule;
    private MdrPricingRule ccVisaRule;
    private MdrPricingRule merchantSpecificRule;
    private MdrPricingRule expiredRule;

    @BeforeEach
    void setUp() {
        // catch-all default rule — score 0
        defaultRule = makeRule(null, null, null, null, null, 2.00);

        // CC + VISA rule — score 2
        ccVisaRule = makeRule(null, "CC", "CREDIT", "VISA", null, 1.80);

        // merchant-specific CC + VISA rule — score 4 (highest)
        merchantSpecificRule = makeRule("22137", "CC", "CREDIT", "VISA", null, 1.50);

        // expired rule — should never match
        expiredRule = makeRule(null, "CC", "CREDIT", "MASTERCARD", null, 3.00);
        expiredRule.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        expiredRule.setEffectiveTo(LocalDate.of(2021, 12, 31));
    }

    // ── Test 1: exact merchant-specific rule wins ──────────────────────────

    @Test
    void whenMerchantSpecificRuleExists_thenItWinsOverGeneric() {
        when(ruleRepository.findByIsActiveTrue())
                .thenReturn(List.of(defaultRule, ccVisaRule, merchantSpecificRule));

        RawTransaction txn = makeTxn("22137", "CC", "CREDIT", "VISA", null, "1000.00");
        MdrRuleEngineService.RuleResult result = ruleEngineService.applyRule(txn);

        // merchant-specific rate is 1.50% of 1000 = 15.00
        assertThat(result.mdrAmount()).isEqualByComparingTo("15.00");
    }

    // ── Test 2: generic rule wins when no merchant-specific rule ───────────

    @Test
    void whenNoMerchantSpecificRule_thenGenericRuleApplies() {
        when(ruleRepository.findByIsActiveTrue())
                .thenReturn(List.of(defaultRule, ccVisaRule));

        RawTransaction txn = makeTxn("99999", "CC", "CREDIT", "VISA", null, "1000.00");
        MdrRuleEngineService.RuleResult result = ruleEngineService.applyRule(txn);

        // generic CC+VISA rate is 1.80% of 1000 = 18.00
        assertThat(result.mdrAmount()).isEqualByComparingTo("18.00");
    }

    // ── Test 3: fallback to default when no specific rule matches ─────────

    @Test
    void whenNoSpecificRuleMatches_thenDefaultFallbackApplies() {
        when(ruleRepository.findByIsActiveTrue())
                .thenReturn(List.of(defaultRule, ccVisaRule));

        // UPI transaction — only default rule matches
        RawTransaction txn = makeTxn("22137", "UPI", null, null, null, "500.00");
        MdrRuleEngineService.RuleResult result = ruleEngineService.applyRule(txn);

        // default rate is 2.00% of 500 = 10.00
        assertThat(result.mdrAmount()).isEqualByComparingTo("10.00");
    }

    // ── Test 4: expired rule is never picked ──────────────────────────────

    @Test
    void whenRuleIsExpired_thenItIsNotSelected() {
        when(ruleRepository.findByIsActiveTrue())
                .thenReturn(List.of(defaultRule, expiredRule));

        // MASTERCARD txn — expired rule exists but should not be picked
        RawTransaction txn = makeTxn("22137", "CC", "CREDIT", "MASTERCARD", null, "1000.00");
        MdrRuleEngineService.RuleResult result = ruleEngineService.applyRule(txn);

        // falls back to default: 2.00% of 1000 = 20.00
        assertThat(result.mdrAmount()).isEqualByComparingTo("20.00");
    }

    // ── Test 5: no rules at all returns zero ──────────────────────────────

    @Test
    void whenNoRulesExist_thenMdrAmountIsZero() {
        when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of());

        RawTransaction txn = makeTxn("22137", "CC", "CREDIT", "VISA", null, "1000.00");
        MdrRuleEngineService.RuleResult result = ruleEngineService.applyRule(txn);

        assertThat(result.mdrAmount()).isEqualByComparingTo("0.00");
        assertThat(result.ruleId()).isNull();
    }

    // ── Test 6: MDR calculation precision ─────────────────────────────────

    @Test
    void mdrAmountIsRoundedToTwoDecimalPlaces() {
        MdrPricingRule oddRule = makeRule(null, "CC", "CREDIT", "VISA", null, 1.85);
        when(ruleRepository.findByIsActiveTrue()).thenReturn(List.of(oddRule));

        // 1.85% of 1234.56 = 22.8394 → rounds to 22.84
        RawTransaction txn = makeTxn("22137", "CC", "CREDIT", "VISA", null, "1234.56");
        MdrRuleEngineService.RuleResult result = ruleEngineService.applyRule(txn);

        assertThat(result.mdrAmount()).isEqualByComparingTo("22.84");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private MdrPricingRule makeRule(String merchantId, String paymentMode,
                                    String cardType, String cardScheme,
                                    String ibiboCode, double rate) {
        MdrPricingRule rule = new MdrPricingRule();
        rule.setRuleId((long) (Math.random() * 1000));
        rule.setMerchantId(merchantId);
        rule.setPaymentMode(paymentMode);
        rule.setCardType(cardType);
        rule.setCardScheme(cardScheme);
        rule.setIbiboCode(ibiboCode);
        rule.setMdrRatePercent(BigDecimal.valueOf(rate));
        rule.setIsActive(true);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2027, 12, 31));
        return rule;
    }

    private RawTransaction makeTxn(String merchantId, String paymentMode,
                                    String cardType, String cardScheme,
                                    String ibiboCode, String amount) {
        RawTransaction txn = new RawTransaction();
        txn.setMerchantId(merchantId);
        txn.setPaymentMode(paymentMode);
        txn.setCardType(cardType);
        txn.setCardScheme(cardScheme);
        txn.setIbiboCode(ibiboCode);
        txn.setTxnAmount(new BigDecimal(amount));
        return txn;
    }
}