package com.payu.mdr.repository;

import com.payu.mdr.entity.DailyAccounting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyAccountingRepository extends JpaRepository<DailyAccounting, Long> {
}