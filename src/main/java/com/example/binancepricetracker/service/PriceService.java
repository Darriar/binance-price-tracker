package com.example.binancepricetracker.service;

import com.example.binancepricetracker.entity.PriceRecord;
import com.example.binancepricetracker.exception.PriceNotFoundException;
import com.example.binancepricetracker.repository.PriceRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class PriceService {

    private static final int PRICE_SCALE = 8;
    private static final Sort SORT_BY_FETCHED_AT_ASC = Sort.by("fetchedAt").ascending();

    private final PriceRecordRepository priceRecordRepository;
    private final String defaultSymbol;

    public PriceService(
            PriceRecordRepository priceRecordRepository,
            @Value("${binance.symbol}") String defaultSymbol
    ) {
        this.priceRecordRepository = priceRecordRepository;
        this.defaultSymbol = defaultSymbol;
    }

    public PriceRecord getLatestPrice(String symbol) {
        String targetSymbol = resolveSymbol(symbol);
        return priceRecordRepository.findFirstBySymbolOrderByFetchedAtDesc(targetSymbol)
                .orElseThrow(() -> new PriceNotFoundException("No price records found for " + targetSymbol));
    }

    public BigDecimal getAveragePrice(String symbol, Instant from, Instant to) {
        validatePeriod(from, to);
        String targetSymbol = resolveSymbol(symbol);

        List<PriceRecord> records = priceRecordRepository.findBySymbolAndFetchedAtBetween(
                targetSymbol, from, to, SORT_BY_FETCHED_AT_ASC
        );
        if (records.isEmpty()) {
            throw new PriceNotFoundException("No price records found for " + targetSymbol + " in the given period");
        }

        BigDecimal sum = records.stream()
                .map(PriceRecord::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(BigDecimal.valueOf(records.size()), PRICE_SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    public Page<PriceRecord> getPriceHistory(String symbol, Instant from, Instant to, Pageable pageable) {
        validatePeriod(from, to);
        String targetSymbol = resolveSymbol(symbol);
        return priceRecordRepository.findBySymbolAndFetchedAtBetween(targetSymbol, from, to, pageable);
    }

    public PriceTrend calculateTrend(String symbol, Instant from, Instant to) {
        validatePeriod(from, to);
        String targetSymbol = resolveSymbol(symbol);

        List<PriceRecord> records = priceRecordRepository.findBySymbolAndFetchedAtBetween(
                targetSymbol, from, to, SORT_BY_FETCHED_AT_ASC
        );
        if (records.isEmpty()) {
            throw new PriceNotFoundException("No price records found for " + targetSymbol + " in the given period");
        }

        BigDecimal firstPrice = records.get(0).getPrice();
        BigDecimal lastPrice = records.get(records.size() - 1).getPrice();
        Trend trend = detectTrend(firstPrice, lastPrice);

        return new PriceTrend(
                targetSymbol,
                LocalDate.ofInstant(from, ZoneOffset.UTC),
                LocalDate.ofInstant(to, ZoneOffset.UTC),
                trend
        );
    }

    private Trend detectTrend(BigDecimal firstPrice, BigDecimal lastPrice) {
        int comparison = lastPrice.compareTo(firstPrice);
        if (comparison > 0) {
            return Trend.UPWARD;
        }
        if (comparison < 0) {
            return Trend.DOWNWARD;
        }
        return Trend.FLAT;
    }

    private String resolveSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return defaultSymbol;
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private void validatePeriod(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("'from' and 'to' parameters are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
    }
}