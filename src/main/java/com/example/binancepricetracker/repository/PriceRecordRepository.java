package com.example.binancepricetracker.repository;

import com.example.binancepricetracker.entity.PriceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PriceRecordRepository extends JpaRepository<PriceRecord, Long> {

    boolean existsBySymbolAndCloseTime(String symbol, Long closeTime);

    Optional<PriceRecord> findFirstBySymbolOrderByFetchedAtDesc(String symbol);

    Page<PriceRecord> findBySymbolAndFetchedAtBetween(
            String symbol,
            Instant from,
            Instant to,
            Pageable pageable
    );

    List<PriceRecord> findBySymbolAndFetchedAtBetween(
            String symbol,
            Instant from,
            Instant to,
            Sort sort
    );
}