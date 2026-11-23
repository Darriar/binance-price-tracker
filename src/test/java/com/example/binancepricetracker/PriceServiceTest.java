package com.example.binancepricetracker;

import com.example.binancepricetracker.entity.PriceRecord;
import com.example.binancepricetracker.exception.PriceNotFoundException;
import com.example.binancepricetracker.repository.PriceRecordRepository;
import com.example.binancepricetracker.service.PriceService;
import com.example.binancepricetracker.service.PriceTrend;
import com.example.binancepricetracker.service.Trend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-30T23:59:59Z");
    private static final Sort SORT_ASC = Sort.by("fetchedAt").ascending();

    @Mock
    private PriceRecordRepository priceRecordRepository;

    private PriceService priceService;

    @BeforeEach
    void setUp() {
        priceService = new PriceService(priceRecordRepository, "BNBUSDT");
    }

    @Test
    void getLatestPriceUsesDefaultSymbolWhenBlank() {
        PriceRecord expected = priceRecord("BNBUSDT", "612.45", TO);
        when(priceRecordRepository.findFirstBySymbolOrderByFetchedAtDesc("BNBUSDT"))
                .thenReturn(Optional.of(expected));

        PriceRecord actual = priceService.getLatestPrice("  ");

        assertThat(actual).isSameAs(expected);
        verify(priceRecordRepository).findFirstBySymbolOrderByFetchedAtDesc("BNBUSDT");
    }

    @Test
    void getLatestPriceNormalizesSymbolToUpperCase() {
        PriceRecord expected = priceRecord("BTCUSDT", "95000.50", TO);
        when(priceRecordRepository.findFirstBySymbolOrderByFetchedAtDesc("BTCUSDT"))
                .thenReturn(Optional.of(expected));

        PriceRecord actual = priceService.getLatestPrice(" btcusdt ");

        assertThat(actual.getSymbol()).isEqualTo("BTCUSDT");
    }

    @Test
    void getLatestPriceThrowsWhenNoRecordsExist() {
        when(priceRecordRepository.findFirstBySymbolOrderByFetchedAtDesc("BNBUSDT"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> priceService.getLatestPrice(null))
                .isInstanceOf(PriceNotFoundException.class);
    }

    @Test
    void getAveragePriceCalculatesExactBigDecimalAndStripsTrailingZeros() {
        List<PriceRecord> records = List.of(
                priceRecord("BNBUSDT", "10.10", FROM),
                priceRecord("BNBUSDT", "10.30", TO)
        );
        when(priceRecordRepository.findBySymbolAndFetchedAtBetween("BNBUSDT", FROM, TO, SORT_ASC))
                .thenReturn(records);

        BigDecimal average = priceService.getAveragePrice(null, FROM, TO);

        assertThat(average).isEqualByComparingTo("10.2");
        assertThat(average.toPlainString()).isEqualTo("10.2");
    }

    @Test
    void getAveragePriceRoundsRepeatingDecimalHalfUp() {
        List<PriceRecord> records = List.of(
                priceRecord("BNBUSDT", "10.00", FROM),
                priceRecord("BNBUSDT", "10.00", FROM.plusSeconds(60)),
                priceRecord("BNBUSDT", "10.01", TO)
        );
        when(priceRecordRepository.findBySymbolAndFetchedAtBetween("BNBUSDT", FROM, TO, SORT_ASC))
                .thenReturn(records);

        BigDecimal average = priceService.getAveragePrice("BNBUSDT", FROM, TO);

        assertThat(average).isEqualByComparingTo("10.00333333");
    }

    @Test
    void getAveragePriceHandlesHighPrecisionDecimalsAndRoundsHalfUp() {
        List<PriceRecord> records = List.of(
                priceRecord("BNBUSDT", "612.1234567810", FROM),
                priceRecord("BNBUSDT", "612.1234567890", TO)
        );
        when(priceRecordRepository.findBySymbolAndFetchedAtBetween("BNBUSDT", FROM, TO, SORT_ASC))
                .thenReturn(records);

        BigDecimal average = priceService.getAveragePrice("BNBUSDT", FROM, TO);

        assertThat(average).isEqualByComparingTo("612.12345679");
        assertThat(average.toPlainString()).isEqualTo("612.12345679");
    }

    @Test
    void getAveragePriceThrowsWhenPeriodHasNoRecords() {
        when(priceRecordRepository.findBySymbolAndFetchedAtBetween("BNBUSDT", FROM, TO, SORT_ASC))
                .thenReturn(List.of());

        assertThatThrownBy(() -> priceService.getAveragePrice("BNBUSDT", FROM, TO))
                .isInstanceOf(PriceNotFoundException.class);
    }

    @Test
    void getAveragePriceRejectsReversedPeriod() {
        assertThatThrownBy(() -> priceService.getAveragePrice("BNBUSDT", TO, FROM))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(priceRecordRepository);
    }

    @Test
    void getPriceHistoryReturnsPageFromRepository() {
        Pageable pageable = PageRequest.of(0, 20, SORT_ASC);
        Page<PriceRecord> expectedPage = new PageImpl<>(
                List.of(priceRecord("BNBUSDT", "605.10", FROM)),
                pageable,
                1
        );
        when(priceRecordRepository.findBySymbolAndFetchedAtBetween("BNBUSDT", FROM, TO, pageable))
                .thenReturn(expectedPage);

        Page<PriceRecord> actual = priceService.getPriceHistory(null, FROM, TO, pageable);

        assertThat(actual).isSameAs(expectedPage);
    }

    @Test
    void getPriceHistoryRejectsReversedPeriod() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> priceService.getPriceHistory("BNBUSDT", TO, FROM, pageable))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(priceRecordRepository);
    }

    @ParameterizedTest
    @CsvSource({
            "600.00, 625.50, UPWARD",
            "625.50, 600.00, DOWNWARD",
            "600.00, 600.00, FLAT"
    })
    void calculateTrendDetectsDirection(String firstPrice, String lastPrice, Trend expectedTrend) {
        List<PriceRecord> records = List.of(
                priceRecord("BNBUSDT", firstPrice, FROM),
                priceRecord("BNBUSDT", "610.00", FROM.plusSeconds(3600)),
                priceRecord("BNBUSDT", lastPrice, TO)
        );
        when(priceRecordRepository.findBySymbolAndFetchedAtBetween("BNBUSDT", FROM, TO, SORT_ASC))
                .thenReturn(records);

        PriceTrend result = priceService.calculateTrend(null, FROM, TO);

        assertThat(result.symbol()).isEqualTo("BNBUSDT");
        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(result.trend()).isEqualTo(expectedTrend);
    }

    @Test
    void calculateTrendThrowsWhenPeriodHasNoRecords() {
        when(priceRecordRepository.findBySymbolAndFetchedAtBetween("BNBUSDT", FROM, TO, SORT_ASC))
                .thenReturn(List.of());

        assertThatThrownBy(() -> priceService.calculateTrend("BNBUSDT", FROM, TO))
                .isInstanceOf(PriceNotFoundException.class);
    }

    private PriceRecord priceRecord(String symbol, String price, Instant fetchedAt) {
        return new PriceRecord(symbol, new BigDecimal(price), fetchedAt, fetchedAt.toEpochMilli());
    }
}