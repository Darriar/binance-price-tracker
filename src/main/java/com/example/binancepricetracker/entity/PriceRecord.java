package com.example.binancepricetracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_records")
@Getter
@Setter
@NoArgsConstructor
public class PriceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal price;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "close_time", nullable = false)
    private Long closeTime;

    public PriceRecord(String symbol, BigDecimal price, Instant fetchedAt, Long closeTime) {
        this.symbol = symbol;
        this.price = price;
        this.fetchedAt = fetchedAt;
        this.closeTime = closeTime;
    }
}