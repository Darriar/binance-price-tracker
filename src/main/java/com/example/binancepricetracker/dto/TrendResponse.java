package com.example.binancepricetracker.dto;

import com.example.binancepricetracker.service.PriceTrend;
import com.example.binancepricetracker.service.Trend;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record TrendResponse(
        String symbol,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        Trend trend
) {
    public static TrendResponse from(PriceTrend priceTrend) {
        return new TrendResponse(
                priceTrend.symbol(),
                priceTrend.startDate(),
                priceTrend.endDate(),
                priceTrend.trend()
        );
    }
}