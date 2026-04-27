package com.payu.mdr.repository;

import com.payu.mdr.entity.DailyMdrAgg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyMdrAggRepository extends JpaRepository<DailyMdrAgg, Long> {
}