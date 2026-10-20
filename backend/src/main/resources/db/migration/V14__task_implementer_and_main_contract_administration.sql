ALTER TABLE investment_task ADD COLUMN implementer varchar(255);

-- Nullable columns preserve the distinction between missing historical data and no security.
ALTER TABLE main_contract
    ADD COLUMN actual_completion_date date,
    ADD COLUMN warranty_months integer,
    ADD COLUMN oc_insurance_valid_until date,
    ADD COLUMN car_insurance_valid_until date,
    ADD COLUMN performance_security_type varchar(30),
    ADD COLUMN performance_security_amount numeric(15,2),
    ADD COLUMN security_validity_mode varchar(30),
    ADD COLUMN security_valid_until date,
    ADD COLUMN retention_security_amount numeric(15,2),
    ADD COLUMN retention_security_valid_until date;

ALTER TABLE main_contract
    ADD CONSTRAINT chk_main_warranty_months CHECK (warranty_months BETWEEN 0 AND 1200),
    ADD CONSTRAINT chk_main_security_amount CHECK (performance_security_amount > 0),
    ADD CONSTRAINT chk_main_retention_amount CHECK (retention_security_amount > 0),
    ADD CONSTRAINT chk_main_administration_dates CHECK (
        (actual_completion_date IS NULL OR actual_completion_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31')
        AND (oc_insurance_valid_until IS NULL OR oc_insurance_valid_until BETWEEN DATE '0001-01-01' AND DATE '9999-12-31')
        AND (car_insurance_valid_until IS NULL OR car_insurance_valid_until BETWEEN DATE '0001-01-01' AND DATE '9999-12-31')
        AND (security_valid_until IS NULL OR security_valid_until BETWEEN DATE '0001-01-01' AND DATE '9999-12-31')
        AND (retention_security_valid_until IS NULL OR retention_security_valid_until BETWEEN DATE '0001-01-01' AND DATE '9999-12-31')
        AND (actual_completion_date IS NULL OR warranty_months IS NULL
            OR actual_completion_date + make_interval(months => warranty_months) <= TIMESTAMP '9999-12-31 00:00:00')
    ),
    ADD CONSTRAINT chk_main_security_structure CHECK (
        CASE
            WHEN performance_security_type IS NULL OR performance_security_type = 'NOT_ESTABLISHED' THEN
                performance_security_amount IS NULL AND security_validity_mode IS NULL
                AND security_valid_until IS NULL AND retention_security_amount IS NULL
                AND retention_security_valid_until IS NULL
            WHEN performance_security_type = 'CASH' THEN
                security_validity_mode IS NULL AND security_valid_until IS NULL
                AND retention_security_amount IS NULL AND retention_security_valid_until IS NULL
            WHEN performance_security_type IN ('BANK_GUARANTEE', 'INSURANCE_GUARANTEE', 'OTHER_DOCUMENT') THEN
                CASE
                    WHEN security_validity_mode = 'SINGLE' THEN
                        security_valid_until IS NOT NULL AND retention_security_amount IS NULL
                        AND retention_security_valid_until IS NULL
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
