package com.payu.mdr.repository;

import com.payu.mdr.entity.RawTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RawTransactionRepository extends JpaRepository<RawTransaction, String> {

    Optional<RawTransaction> findFirstByDedupKeyHashAndIsDuplicateFalseAndCreatedAtAfter(
        String dedupKeyHash, LocalDateTime after
    );
}