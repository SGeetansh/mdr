package com.payu.mdr.service;

import com.payu.mdr.entity.MdrPricingRule;
import com.payu.mdr.entity.RawTransaction;
import com.payu.mdr.repository.MdrPricingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MdrRuleEngineService {

    private final MdrPricingRuleRepository ruleRepository;

    // ─────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────

    /**
     * Given a transaction, finds the best matching rule and
     * returns the computed MDR amount.
     * Never returns null — falls back to a 0.00 amount if
     * no rule exists at all.
     */
    public RuleResult applyRule(RawTransaction txn) {
        List<MdrPricingRule> activeRules = ruleRepository.findByIsActiveTrue();

        MdrPricingRule bestRule = findBestRule(activeRules, txn);

        if (bestRule == null) {
            log.warn("No MDR rule found for txn={} merchant={}", txn.getTxnId(), txn.getMerchantId());
            return new RuleResult(null, BigDecimal.ZERO);
        }

        BigDecimal mdrAmount = txn.getTxnAmount()
                .multiply(bestRule.getMdrRatePercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        log.debug("Rule matched: ruleId={} score={} mdrAmount={}",
                bestRule.getRuleId(), scoreRule(bestRule, txn), mdrAmount);

        return new RuleResult(bestRule.getRuleId(), mdrAmount);
    }

    // ─────────────────────────────────────────────
    // CORE SCORING LOGIC
    // ─────────────────────────────────────────────

    /**
     * Scores every active, date-valid rule against the transaction.
     * Higher score = more specific match = wins.
     *
     * Scoring:
     *   +2 points if merchant_id matches  (most specific — merchant-level rule)
     *   +1 point each for payment_mode, card_type, card_scheme, ibibo_code
     *
     * A rule with NULL in a field is treated as a wildcard — it doesn't
     * add points but also doesn't disqualify the rule.
     *
     * A rule with a non-null field that does NOT match the transaction
     * is disqualified entirely (score = -1).
     */
    MdrPricingRule findBestRule(List<MdrPricingRule> rules, RawTransaction txn) {
        MdrPricingRule bestRule = null;
        int bestScore = -1;

        for (MdrPricingRule rule : rules) {
            if (!isDateValid(rule)) continue;

            int score = scoreRule(rule, txn);
            if (score < 0) continue;  // disqualified — a field didn't match

            if (score > bestScore) {
                bestScore = score;
                bestRule = rule;
            }
        }

        return bestRule;
    }

    int scoreRule(MdrPricingRule rule, RawTransaction txn) {
        int score = 0;

        // merchant_id — worth 2 points (highest specificity)
        if (rule.getMerchantId() != null) {
            if (!rule.getMerchantId().equals(txn.getMerchantId())) return -1;
            score += 2;
        }

        // payment_mode
        if (rule.getPaymentMode() != null) {
            if (!rule.getPaymentMode().equalsIgnoreCase(txn.getPaymentMode())) return -1;
            score += 1;
        }

        // card_type
        if (rule.getCardType() != null) {
            if (!rule.getCardType().equalsIgnoreCase(txn.getCardType())) return -1;
            score += 1;
        }

        // card_scheme
        if (rule.getCardScheme() != null) {
            if (!rule.getCardScheme().equalsIgnoreCase(txn.getCardScheme())) return -1;
            score += 1;
        }

        // ibibo_code
        if (rule.getIbiboCode() != null) {
            if (!rule.getIbiboCode().equalsIgnoreCase(txn.getIbiboCode())) return -1;
            score += 1;
        }

        return score;  // 0 = catch-all default rule, >0 = specific match
    }

    boolean isDateValid(MdrPricingRule rule) {
        LocalDate today = LocalDate.now();
        return !today.isBefore(rule.getEffectiveFrom())
                && !today.isAfter(rule.getEffectiveTo());
    }

    // ─────────────────────────────────────────────
    // RESULT RECORD
    // ─────────────────────────────────────────────

    public record RuleResult(Long ruleId, BigDecimal mdrAmount) {}
}