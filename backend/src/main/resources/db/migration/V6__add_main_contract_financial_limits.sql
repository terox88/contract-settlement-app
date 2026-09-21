-- ============================================================
-- V6 - Main contract financial limits
-- ============================================================

CREATE TABLE financial_limit (
                                 id BIGSERIAL PRIMARY KEY,

                                 main_contract_id BIGINT NOT NULL,

                                 CONSTRAINT fk_financial_limit_main_contract
                                     FOREIGN KEY (main_contract_id)
                                         REFERENCES main_contract(id),

                                 CONSTRAINT uk_financial_limit_main_contract
                                     UNIQUE (main_contract_id)
);


CREATE TABLE financial_limit_year (
                                      id BIGSERIAL PRIMARY KEY,

                                      financial_limit_id BIGINT NOT NULL,

                                      year INTEGER NOT NULL,

                                      amount NUMERIC(15,2) NOT NULL,

                                      CONSTRAINT fk_financial_limit_year_financial_limit
                                          FOREIGN KEY (financial_limit_id)
                                              REFERENCES financial_limit(id),

                                      CONSTRAINT uk_financial_limit_year
                                          UNIQUE (financial_limit_id, year),

                                      CONSTRAINT chk_financial_limit_year_amount
                                          CHECK (amount >= 0)
);


CREATE INDEX idx_financial_limit_year_financial_limit
    ON financial_limit_year(financial_limit_id);