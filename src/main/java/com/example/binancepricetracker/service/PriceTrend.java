package com.example.binancepricetracker.service;

import java.time.LocalDate;

public record PriceTrend(
        String symbol,
        LocalDate startDate,
        LocalDate endDate,
        Trend trend
) {
}