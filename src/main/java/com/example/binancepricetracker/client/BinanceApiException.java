package com.example.binancepricetracker.client;

import lombok.Getter;

@Getter
public class BinanceApiException extends RuntimeException {

    private final Integer statusCode;
    private final Long retryAfterSeconds;

    public BinanceApiException(String message) {
        super(message);
        this.statusCode = null;
        this.retryAfterSeconds = null;
    }

    public BinanceApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
        this.retryAfterSeconds = null;
    }

    public BinanceApiException(String message, int statusCode, Long retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public boolean isRateLimited() {
        return statusCode != null && (statusCode == 429 || statusCode == 418);
    }
}