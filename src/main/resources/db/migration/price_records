CREATE TABLE price_records (
    id         BIGSERIAL PRIMARY KEY,
    symbol     VARCHAR(20)    NOT NULL,
    price      NUMERIC(19, 8) NOT NULL,
    fetched_at TIMESTAMPTZ    NOT NULL,
    close_time BIGINT         NOT NULL,
    CONSTRAINT uq_price_records_symbol_close_time UNIQUE (symbol, close_time)
);

CREATE INDEX idx_price_records_symbol_fetched_at
    ON price_records (symbol, fetched_at);