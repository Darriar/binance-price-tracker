package com.example.binancepricetracker.client;

import com.example.binancepricetracker.exception.BinanceApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class BinanceClient {

    private final RestClient binanceRestClient;

    public BinanceAvgPriceDto fetchAvgPrice(String symbol) {
        try {
            BinanceAvgPriceDto response = binanceRestClient.get()
                    .uri("/api/v3/avgPrice?symbol={symbol}", symbol)
                    .retrieve()
                    .body(BinanceAvgPriceDto.class);

            if (response == null || response.price() == null || response.closeTime() == null) {
                throw new BinanceApiException("Binance returned incomplete response for " + symbol);
            }
            if (response.price().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BinanceApiException("Binance returned non-positive price for " + symbol);
            }
            return response;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            Long retryAfter = parseRetryAfter(ex.getResponseHeaders());
            throw new BinanceApiException(
                    "Binance API error (HTTP " + status + ") for " + symbol,
                    status,
                    retryAfter,
                    ex
            );
        } catch (RestClientException ex) {
            throw new BinanceApiException("Failed to call Binance API for " + symbol, ex);
        }
    }

    private Long parseRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds > 0 ? seconds : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}