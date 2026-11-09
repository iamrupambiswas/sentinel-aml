CREATE TABLE fx_rate (
    currency_code VARCHAR(3) PRIMARY KEY,
    rate_to_inr DECIMAL(19,4) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

-- Seed initial rates
INSERT INTO fx_rate (currency_code, rate_to_inr, updated_at) VALUES
('USD', 83.5000, NOW()),
('EUR', 89.2000, NOW()),
('GBP', 105.1000, NOW()),
('AED', 22.7300, NOW()),
('INR', 1.0000, NOW());