-- ============================================================
-- V3 - invoices, payments and additional contract details
-- ============================================================


-- ============================================================
-- CONTRACT
-- initial_value is treated as GROSS contract value.
-- ============================================================

ALTER TABLE contract
    ADD COLUMN contract_type VARCHAR(50) NOT NULL,
    ADD COLUMN remuneration_type VARCHAR(50) NOT NULL,
    ADD COLUMN vat_rate NUMERIC(5,2) NOT NULL;

ALTER TABLE contract
    ADD CONSTRAINT chk_contract_type
        CHECK (
                contract_type IN (
                                  'SERVICE',
                                  'DESIGN_AND_BUILD',
                                  'CONSTRUCTION_WORKS'
                )
            );

ALTER TABLE contract
    ADD CONSTRAINT chk_contract_remuneration_type
        CHECK (
                remuneration_type IN (
                                      'LUMP_SUM',
                                      'UNIT_PRICE'
                )
            );

ALTER TABLE contract
    ADD CONSTRAINT chk_contract_vat_rate
        CHECK (
                    vat_rate >= 0
                AND vat_rate <= 100
            );


-- ============================================================
-- SUBCONTRACT DEDUCTION RULES
-- percentage = contractual deduction percentage
-- basis = NET or GROSS contract value
-- ============================================================

ALTER TABLE subcontract
    ADD COLUMN performance_security_percentage NUMERIC(5,2),
    ADD COLUMN performance_security_basis VARCHAR(20),
    ADD COLUMN other_deduction_percentage NUMERIC(5,2),
    ADD COLUMN other_deduction_basis VARCHAR(20);


ALTER TABLE subcontract
    ADD CONSTRAINT chk_performance_security_percentage
        CHECK (
                performance_security_percentage IS NULL
                OR (
                            performance_security_percentage >= 0
                        AND performance_security_percentage <= 100
                    )
            );

ALTER TABLE subcontract
    ADD CONSTRAINT chk_other_deduction_percentage
        CHECK (
                other_deduction_percentage IS NULL
                OR (
                            other_deduction_percentage >= 0
                        AND other_deduction_percentage <= 100
                    )
            );


ALTER TABLE subcontract
    ADD CONSTRAINT chk_performance_security_basis
        CHECK (
                performance_security_basis IS NULL
                OR performance_security_basis IN ('NET', 'GROSS')
            );

ALTER TABLE subcontract
    ADD CONSTRAINT chk_other_deduction_basis
        CHECK (
                other_deduction_basis IS NULL
                OR other_deduction_basis IN ('NET', 'GROSS')
            );


-- Either both values are set or both are null.

ALTER TABLE subcontract
    ADD CONSTRAINT chk_performance_security_complete
        CHECK (
                (
                        performance_security_percentage IS NULL
                        AND performance_security_basis IS NULL
                    )
                OR
                (
                        performance_security_percentage IS NOT NULL
                        AND performance_security_basis IS NOT NULL
                    )
            );

ALTER TABLE subcontract
    ADD CONSTRAINT chk_other_deduction_complete
        CHECK (
                (
                        other_deduction_percentage IS NULL
                        AND other_deduction_basis IS NULL
                    )
                OR
                (
                        other_deduction_percentage IS NOT NULL
                        AND other_deduction_basis IS NOT NULL
                    )
            );


-- ============================================================
-- CONTRACT PENALTY
-- Can belong to MainContract or Subcontract through Contract.
-- ============================================================

CREATE TABLE contract_penalty (
                                  id BIGSERIAL PRIMARY KEY,

                                  contract_id BIGINT NOT NULL,

                                  imposed_date DATE NOT NULL,

                                  amount NUMERIC(15,2) NOT NULL,

                                  description VARCHAR(1000),

                                  CONSTRAINT fk_contract_penalty_contract
                                      FOREIGN KEY (contract_id)
                                          REFERENCES contract(id),

                                  CONSTRAINT chk_contract_penalty_amount
                                      CHECK (amount > 0)
);

CREATE INDEX idx_contract_penalty_contract
    ON contract_penalty(contract_id);


-- ============================================================
-- MAIN CONTRACTOR INVOICE
-- ============================================================

CREATE TABLE main_contractor_invoice (
                                         id BIGSERIAL PRIMARY KEY,

                                         main_contract_id BIGINT NOT NULL,

                                         invoice_number VARCHAR(255) NOT NULL,

                                         issue_date DATE NOT NULL,

                                         sale_date DATE,

                                         due_date DATE,

                                         net_amount NUMERIC(15,2) NOT NULL,

                                         vat_amount NUMERIC(15,2) NOT NULL,

                                         gross_amount NUMERIC(15,2) NOT NULL,

                                         currency VARCHAR(3) NOT NULL DEFAULT 'PLN',

                                         CONSTRAINT fk_main_contractor_invoice_contract
                                             FOREIGN KEY (main_contract_id)
                                                 REFERENCES main_contract(id),

                                         CONSTRAINT uk_main_invoice_contract_number
                                             UNIQUE (
                                                     main_contract_id,
                                                     invoice_number
                                                 ),

                                         CONSTRAINT chk_main_invoice_net_amount
                                             CHECK (net_amount >= 0),

                                         CONSTRAINT chk_main_invoice_vat_amount
                                             CHECK (vat_amount >= 0),

                                         CONSTRAINT chk_main_invoice_gross_amount
                                             CHECK (gross_amount > 0)
);

CREATE INDEX idx_main_contractor_invoice_contract
    ON main_contractor_invoice(main_contract_id);


-- ============================================================
-- SUBCONTRACTOR INVOICE
-- ============================================================

CREATE TABLE subcontractor_invoice (
                                       id BIGSERIAL PRIMARY KEY,

                                       subcontract_id BIGINT NOT NULL,

                                       invoice_number VARCHAR(255) NOT NULL,

                                       issue_date DATE NOT NULL,

                                       sale_date DATE,

                                       due_date DATE,

                                       net_amount NUMERIC(15,2) NOT NULL,

                                       vat_amount NUMERIC(15,2) NOT NULL,

                                       gross_amount NUMERIC(15,2) NOT NULL,

                                       currency VARCHAR(3) NOT NULL DEFAULT 'PLN',

                                       CONSTRAINT fk_subcontractor_invoice_subcontract
                                           FOREIGN KEY (subcontract_id)
                                               REFERENCES subcontract(id),

                                       CONSTRAINT uk_subcontract_invoice_number
                                           UNIQUE (
                                                   subcontract_id,
                                                   invoice_number
                                               ),

                                       CONSTRAINT chk_subcontract_invoice_net_amount
                                           CHECK (net_amount >= 0),

                                       CONSTRAINT chk_subcontract_invoice_vat_amount
                                           CHECK (vat_amount >= 0),

                                       CONSTRAINT chk_subcontract_invoice_gross_amount
                                           CHECK (gross_amount > 0)
);

CREATE INDEX idx_subcontractor_invoice_subcontract
    ON subcontractor_invoice(subcontract_id);


-- ============================================================
-- PAYMENT
-- A single bank payment may cover multiple subcontractor invoices.
-- ============================================================

CREATE TABLE payment (
                         id BIGSERIAL PRIMARY KEY,

                         payment_date DATE NOT NULL,

                         amount NUMERIC(15,2) NOT NULL,

                         currency VARCHAR(3) NOT NULL DEFAULT 'PLN',

                         bank_reference VARCHAR(255),

                         description VARCHAR(1000),

                         CONSTRAINT chk_payment_amount
                             CHECK (amount > 0)
);


-- ============================================================
-- PAYMENT ALLOCATION
--
-- allocated_amount = actually paid amount for this invoice
-- deducted_amount  = amount deducted while settling this invoice
--
-- One payment may settle multiple invoices.
-- One invoice may be settled by multiple payments.
-- ============================================================

CREATE TABLE payment_allocation (
                                    id BIGSERIAL PRIMARY KEY,

                                    payment_id BIGINT NOT NULL,

                                    subcontractor_invoice_id BIGINT NOT NULL,

                                    allocated_amount NUMERIC(15,2) NOT NULL,

                                    deducted_amount NUMERIC(15,2) NOT NULL DEFAULT 0,

                                    CONSTRAINT fk_payment_allocation_payment
                                        FOREIGN KEY (payment_id)
                                            REFERENCES payment(id),

                                    CONSTRAINT fk_payment_allocation_invoice
                                        FOREIGN KEY (subcontractor_invoice_id)
                                            REFERENCES subcontractor_invoice(id),

                                    CONSTRAINT uk_payment_allocation
                                        UNIQUE (
                                                payment_id,
                                                subcontractor_invoice_id
                                            ),

                                    CONSTRAINT chk_payment_allocation_amount
                                        CHECK (allocated_amount > 0),

                                    CONSTRAINT chk_payment_allocation_deducted_amount
                                        CHECK (deducted_amount >= 0)
);

CREATE INDEX idx_payment_allocation_payment
    ON payment_allocation(payment_id);

CREATE INDEX idx_payment_allocation_invoice
    ON payment_allocation(subcontractor_invoice_id);


-- ============================================================
-- DOCUMENT ATTACHMENT
--
-- Add relationships to newly introduced business entities.
-- ============================================================

ALTER TABLE document_attachment
    ADD COLUMN main_contractor_invoice_id BIGINT,
    ADD COLUMN subcontractor_invoice_id BIGINT,
    ADD COLUMN contract_id BIGINT,
    ADD COLUMN contract_amendment_id BIGINT,
    ADD COLUMN payment_id BIGINT;


ALTER TABLE document_attachment
    ADD CONSTRAINT fk_document_attachment_main_invoice
        FOREIGN KEY (main_contractor_invoice_id)
            REFERENCES main_contractor_invoice(id);

ALTER TABLE document_attachment
    ADD CONSTRAINT fk_document_attachment_subcontract_invoice
        FOREIGN KEY (subcontractor_invoice_id)
            REFERENCES subcontractor_invoice(id);

ALTER TABLE document_attachment
    ADD CONSTRAINT fk_document_attachment_contract
        FOREIGN KEY (contract_id)
            REFERENCES contract(id);

ALTER TABLE document_attachment
    ADD CONSTRAINT fk_document_attachment_contract_amendment
        FOREIGN KEY (contract_amendment_id)
            REFERENCES contract_amendment(id);

ALTER TABLE document_attachment
    ADD CONSTRAINT fk_document_attachment_payment
        FOREIGN KEY (payment_id)
            REFERENCES payment(id);


CREATE INDEX idx_document_attachment_main_invoice
    ON document_attachment(main_contractor_invoice_id);

CREATE INDEX idx_document_attachment_subcontract_invoice
    ON document_attachment(subcontractor_invoice_id);

CREATE INDEX idx_document_attachment_contract
    ON document_attachment(contract_id);

CREATE INDEX idx_document_attachment_contract_amendment
    ON document_attachment(contract_amendment_id);

CREATE INDEX idx_document_attachment_payment
    ON document_attachment(payment_id);


-- An attachment can temporarily have no owner, for example during import,
-- but it cannot belong to more than one business object simultaneously.

ALTER TABLE document_attachment
    ADD CONSTRAINT chk_document_attachment_single_owner
        CHECK (
                num_nonnulls(
                        main_contractor_invoice_id,
                        subcontractor_invoice_id,
                        contract_id,
                        contract_amendment_id,
                        payment_id
                    ) <= 1
            );