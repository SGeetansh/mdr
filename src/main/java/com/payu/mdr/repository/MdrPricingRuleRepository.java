package com.payu.mdr.repository;

import com.payu.mdr.entity.MdrPricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MdrPricingRuleRepository extends JpaRepository<MdrPricingRule, Long> {

    List<MdrPricingRule> findByIsActiveTrue();
}