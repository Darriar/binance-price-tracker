package com.example.binancepricetracker.scheduler;

import com.example.binancepricetracker.client.BinanceApiException;
import com.example.binancepricetracker.client.BinanceAvgPriceDto;
import com.example.binancepricetracker.client.BinanceClient;
import com.example.binancepricetracker.entity.PriceRecord;
import com.example.binancepricetracker.repository.PriceRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class PricePollingScheduler {

    private static final long DEFAULT_BACKOFF_SECONDS = 60;

    private final BinanceClient binanceClient;
    private final PriceRecordRepository priceRecordRepository;
    private final String symbol;

    private volatile Instant pausedUntil = Instant.EPOCH;

    public PricePollingScheduler(
            BinanceClient binanceClient,
            PriceRecordRepository priceRecordRepository,
            @Value("${binance.symbol}") String symbol
    ) {
        this.binanceClient = binanceClient;
        this.priceRecordRepository = priceRecordRepository;
        this.symbol = symbol;
    }

    @Scheduled(
            initialDelayString = "${binance.initial-delay:PT0S}",
            fixedDelayString = "${binance.poll-interval}"
    )
    public void pollPrice() {
        Instant now = Instant.now();
        if (now.isBefore(pausedUntil)) {
            log.warn("Polling paused until {} due to Binance rate limit", pausedUntil);
            return;
        }

        try {
            BinanceAvgPriceDto response = binanceClient.fetchAvgPrice(symbol);
            saveIfNotExists(response);
        } catch (BinanceApiException ex) {
            handleBinanceError(ex);
        } catch (Exception ex) {
            log.error("Unexpected error while polling price for {}", symbol, ex);
        }
    }

    private void saveIfNotExists(BinanceAvgPriceDto response) {
        if (priceRecordRepository.existsBySymbolAndCloseTime(symbol, response.closeTime())) {
            log.debug("Price snapshot for {} with closeTime {} already exists, skipping",
                    symbol, response.closeTime());
            return;
        }

        PriceRecord record = new PriceRecord(
                symbol,
                response.price(),
                Instant.now(),
                response.closeTime()
        );

        try {
            priceRecordRepository.save(record);
            log.info("Saved {} price: {} (closeTime={})", symbol, response.price(), response.closeTime());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Duplicate price snapshot for {} (closeTime={}), skipping", symbol, response.closeTime());
        }
    }

    private void handleBinanceError(BinanceApiException ex) {
        if (ex.isRateLimited()) {
            long waitSeconds = ex.getRetryAfterSeconds() != null
                    ? ex.getRetryAfterSeconds()
                    : DEFAULT_BACKOFF_SECONDS;
            pausedUntil = Instant.now().plusSeconds(waitSeconds);
            log.warn("Binance rate limit (HTTP {}). Pausing polls for {}s until {}",
                    ex.getStatusCode(), waitSeconds, pausedUntil);
            return;
        }
        log.error("Failed to fetch price for {}: {}", symbol, ex.getMessage());
    }
}