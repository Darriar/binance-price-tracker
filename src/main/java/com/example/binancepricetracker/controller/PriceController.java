package com.example.binancepricetracker.controller;

import com.example.binancepricetracker.dto.AveragePriceResponse;
import com.example.binancepricetracker.dto.PricePageResponse;
import com.example.binancepricetracker.dto.PriceResponse;
import com.example.binancepricetracker.dto.TrendResponse;
import com.example.binancepricetracker.service.PriceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@Validated
@RestController
@RequestMapping("/api/v1/prices")
@RequiredArgsConstructor
public class PriceController {

    private final PriceService priceService;

    @GetMapping("/latest")
    public PriceResponse getLatestPrice(@RequestParam(required = false) String symbol) {
        return PriceResponse.from(priceService.getLatestPrice(symbol));
    }

    @GetMapping("/average")
    public AveragePriceResponse getAveragePrice(
            @RequestParam(required = false) String symbol,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to
    ) {
        return new AveragePriceResponse(
                priceService.getAveragePrice(symbol, from.toInstant(), to.toInstant())
        );
    }

    @GetMapping
    public PricePageResponse getPriceHistory(
            @RequestParam(required = false) String symbol,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("fetchedAt").ascending());
        return PricePageResponse.from(
                priceService.getPriceHistory(symbol, from.toInstant(), to.toInstant(), pageable)
        );
    }

    @GetMapping("/trend")
    public TrendResponse getPriceTrend(
            @RequestParam(required = false) String symbol,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to
    ) {
        return TrendResponse.from(
                priceService.calculateTrend(symbol, from.toInstant(), to.toInstant())
        );
    }
}