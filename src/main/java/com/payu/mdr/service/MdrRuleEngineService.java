package com.payu.mdr.service;

import com.payu.mdr.entity.MdrPricingRule;
import com.payu.mdr.dto.TransactionRequest;
import com.payu.mdr.repository.MdrPricingRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MdrRuleEngineService {

    private final MdrPricingRuleRepository repository;

}