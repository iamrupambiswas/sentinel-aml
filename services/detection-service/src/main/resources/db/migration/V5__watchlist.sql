-- Synthetic watchlist (P9). NOT a real sanctions list - for demo/testing only.
CREATE TABLE watchlist_entry (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    entry_type VARCHAR(20)  NOT NULL,          -- COUNTRY | NAME
    value      VARCHAR(255) NOT NULL,
    source     VARCHAR(50)  NOT NULL,          -- e.g. FATF_STYLE, OFAC_STYLE (synthetic)
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_watchlist (entry_type, value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO watchlist_entry (entry_type, value, source) VALUES
('COUNTRY', 'IR',  'FATF_STYLE'), ('COUNTRY', 'IRN', 'FATF_STYLE'),
('COUNTRY', 'KP',  'FATF_STYLE'), ('COUNTRY', 'PRK', 'FATF_STYLE'),
('COUNTRY', 'MM',  'FATF_STYLE'), ('COUNTRY', 'MMR', 'FATF_STYLE'),
('COUNTRY', 'SY',  'FATF_STYLE'), ('COUNTRY', 'SYR', 'FATF_STYLE'),
('NAME', 'ZENITH SHELL HOLDINGS LTD',   'OFAC_STYLE'),
('NAME', 'CRIMSON MERIDIAN TRADING CO', 'OFAC_STYLE'),
('NAME', 'NORTHWIND OFFSHORE VENTURES', 'UN_STYLE'),
('NAME', 'VIKTOR STRAND',               'UN_STYLE'),
('NAME', 'AL NOOR GENERAL TRADING FZE', 'OFAC_STYLE');
