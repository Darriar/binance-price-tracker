package com.example.binancepricetracker;

import com.example.binancepricetracker.entity.PriceRecord;
import com.example.binancepricetracker.repository.PriceRecordRepository;
import com.example.binancepricetracker.scheduler.PricePollingScheduler;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "binance.initial-delay=PT1H",
        "binance.poll-interval=PT1H"
})
@AutoConfigureMockMvc
@Testcontainers
class PriceTrackerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("binance.base-url", wireMock::baseUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PricePollingScheduler pricePollingScheduler;

    @Autowired
    private PriceRecordRepository priceRecordRepository;

    @BeforeEach
    void setUp() {
        priceRecordRepository.deleteAll();
    }

    @Test
    void pollPriceSavesSnapshotSkipsDuplicatesAndExposesLatestEndpoint() throws Exception {
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v3/avgPrice"))
                .withQueryParam("symbol", WireMock.equalTo("BNBUSDT"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "mins": 5,
                                  "price": "612.45000000",
                                  "closeTime": 1762000000000
                                }
                                """)));

        pricePollingScheduler.pollPrice();
        pricePollingScheduler.pollPrice();

        assertThat(priceRecordRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/prices/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BNBUSDT"))
                .andExpect(jsonPath("$.price").value(612.45))
                .andExpect(jsonPath("$.fetchedAt").value(
                        matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$")));
    }

    @Test
    void pollPriceRecoversAfterBinanceErrorWithoutLosingData() {
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v3/avgPrice"))
                .withQueryParam("symbol", WireMock.equalTo("BNBUSDT"))
                .willReturn(WireMock.serverError()));

        pricePollingScheduler.pollPrice();
        assertThat(priceRecordRepository.count()).isZero();

        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v3/avgPrice"))
                .withQueryParam("symbol", WireMock.equalTo("BNBUSDT"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "mins": 5,
                                  "price": "615.00000000",
                                  "closeTime": 1762000060000
                                }
                                """)));

        pricePollingScheduler.pollPrice();
        assertThat(priceRecordRepository.count()).isEqualTo(1);
    }

    @Test
    void analyticsEndpointsReturnAverageHistoryAndTrend() throws Exception {
        priceRecordRepository.saveAll(List.of(
                new PriceRecord("BNBUSDT", new BigDecimal("600.00"), Instant.parse("2026-09-01T10:00:00Z"), 1L),
                new PriceRecord("BNBUSDT", new BigDecimal("615.00"), Instant.parse("2026-09-15T10:00:00Z"), 2L),
                new PriceRecord("BNBUSDT", new BigDecimal("630.00"), Instant.parse("2026-09-30T10:00:00Z"), 3L)
        ));

        String from = "2026-09-01T00:00:00Z";
        String to = "2026-09-30T23:59:59Z";

        mockMvc.perform(get("/api/v1/prices/average")
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averagePrice").value(615));

        mockMvc.perform(get("/api/v1/prices")
                        .param("from", from)
                        .param("to", to)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(get("/api/v1/prices/trend")
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BNBUSDT"))
                .andExpect(jsonPath("$.start_date").value("2026-09-01"))
                .andExpect(jsonPath("$.end_date").value("2026-09-30"))
                .andExpect(jsonPath("$.trend").value("UPWARD"));
    }

    @Test
    void endpointsReturnExpectedErrorStatuses() throws Exception {
        mockMvc.perform(get("/api/v1/prices/latest"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/prices/average")
                        .param("from", "2026-09-30T00:00:00Z")
                        .param("to", "2026-09-01T00:00:00Z"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/prices")
                        .param("from", "2026-09-01T00:00:00Z")
                        .param("to", "2026-09-30T00:00:00Z")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }
}