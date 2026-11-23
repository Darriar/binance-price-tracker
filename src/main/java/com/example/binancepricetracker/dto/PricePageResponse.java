package com.example.binancepricetracker.dto;

import com.example.binancepricetracker.entity.PriceRecord;
import org.springframework.data.domain.Page;

import java.util.List;

public record PricePageResponse(
        List<PriceResponse> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
    public static PricePageResponse from(Page<PriceRecord> pricePage) {
        List<PriceResponse> items = pricePage.getContent().stream()
                .map(PriceResponse::from)
                .toList();
        return new PricePageResponse(
                items,
                pricePage.getNumber(),
                pricePage.getSize(),
                pricePage.getTotalElements(),
                pricePage.hasNext()
        );
    }
}