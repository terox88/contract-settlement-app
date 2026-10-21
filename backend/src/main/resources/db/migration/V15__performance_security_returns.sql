ALTER TABLE main_contract
    ADD COLUMN security_returned boolean DEFAULT false,
    ADD COLUMN retention_security_returned boolean DEFAULT false;

-- NULL also means no marked return: Hibernate can write an entirely absent
-- optional embeddable as NULL in every column (including these two flags).

-- Cash security may also be returned in two stages. Historical cash rows with
-- no explicit mode remain valid and are treated as a single stage.
ALTER TABLE main_contract DROP CONSTRAINT chk_main_security_structure;
ALTER TABLE main_contract ADD CONSTRAINT chk_main_security_structure CHECK (
    CASE
        WHEN performance_security_type IS NULL OR performance_security_type = 'NOT_ESTABLISHED' THEN
            performance_security_amount IS NULL AND security_validity_mode IS NULL
            AND security_valid_until IS NULL AND retention_security_amount IS NULL
            AND retention_security_valid_until IS NULL
            AND NOT coalesce(security_returned, false) AND NOT coalesce(retention_security_returned, false)
        WHEN performance_security_type = 'CASH' THEN
            security_valid_until IS NULL AND retention_security_valid_until IS NULL
            AND CASE
                WHEN security_validity_mode = 'TWO_STAGE' THEN
                    performance_security_amount IS NULL OR retention_security_amount IS NULL
                    OR retention_security_amount <= performance_security_amount
                WHEN security_validity_mode IS NULL OR security_validity_mode = 'SINGLE' THEN
                    retention_security_amount IS NULL AND NOT coalesce(retention_security_returned, false)
                ELSE FALSE
            END
        WHEN performance_security_type IN ('BANK_GUARANTEE', 'INSURANCE_GUARANTEE', 'OTHER_DOCUMENT') THEN
            CASE
                WHEN security_validity_mode = 'SINGLE' THEN
                    security_valid_until IS NOT NULL AND retention_security_amount IS NULL
                    AND retention_security_valid_until IS NULL AND NOT coalesce(retention_security_returned, false)
                WHEN security_validity_mode = 'TWO_STAGE' THEN
                    security_valid_until IS NOT NULL AND retention_security_valid_until IS NOT NULL
                    AND retention_security_valid_until >= security_valid_until
                    AND (performance_security_amount IS NULL OR retention_security_amount IS NULL
                        OR retention_security_amount <= performance_security_amount)
                ELSE FALSE
            END
        ELSE FALSE
    END
);
