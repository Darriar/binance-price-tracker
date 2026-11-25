package com.example.binancepricetracker.dto;

import com.example.binancepricetracker.entity.PriceRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record PriceResponse(
        String symbol,
        BigDecimal price,
        Instant fetchedAt
) {
    public static PriceResponse from(PriceRecord record) {
        return new PriceResponse(
                record.getSymbol(),
                record.getPrice(),
                record.getFetchedAt().truncatedTo(ChronoUnit.SECONDS)
        );
    }
}