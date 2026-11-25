package com.example.binancepricetracker.client;


import java.math.BigDecimal;

public record BinanceAvgPriceDto(
        Integer mins,
        BigDecimal price,
        Long closeTime
) {
}