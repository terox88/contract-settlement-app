-- ============================================================
-- V4 - Extend invoice and settlement model
-- ============================================================


-- ============================================================
-- 1. CONTRACT
-- Short description used e.g. on contract lists
-- ============================================================

ALTER TABLE contract
    ADD COLUMN short_description VARCHAR(500);


-- ============================================================
-- 2. MAIN CONTRACTOR INVOICE
-- ============================================================

ALTER TABLE main_contractor_invoice
    ADD COLUMN vat_rate NUMERIC(5,2);

ALTER TABLE main_contractor_invoice
    ADD COLUMN description VARCHAR(1000);


-- ============================================================
-- 3. SUBCONTRACTOR INVOICE
-- ============================================================

ALTER TABLE subcontractor_invoice
    ADD COLUMN vat_rate NUMERIC(5,2);

ALTER TABLE subcontractor_invoice
    ADD COLUMN description VARCHAR(1000);


-- ============================================================
-- 4. MAIN INVOICE SETTLEMENT
--
-- Links a subcontractor invoice with a main contractor invoice.
-- One subcontractor invoice may be settled under multiple
-- main contractor invoices.
-- ============================================================

CREATE TABLE main_invoice_settlement (
                                         id BIGSERIAL PRIMARY KEY,

                                         main_contractor_invoice_id BIGINT NOT NULL,
                                         subcontractor_invoice_id BIGINT NOT NULL,

                                         settled_amount NUMERIC(15,2) NOT NULL,

                                         CONSTRAINT fk_main_invoice_settlement_main_invoice
                                             FOREIGN KEY (main_contractor_invoice_id)
                                                 REFERENCES main_contractor_invoice(id),

                                         CONSTRAINT fk_main_invoice_settlement_subcontractor_invoice
                                             FOREIGN KEY (subcontractor_invoice_id)
                                                 REFERENCES subcontractor_invoice(id),

                                         CONSTRAINT uk_main_invoice_settlement
                                             UNIQUE (
                                                     main_contractor_invoice_id,
                                                     subcontractor_invoice_id
                                                 ),

                                         CONSTRAINT chk_main_invoice_settlement_amount
                                             CHECK (settled_amount > 0)
);


CREATE INDEX idx_main_invoice_settlement_main_invoice
    ON main_invoice_settlement(main_contractor_invoice_id);

CREATE INDEX idx_main_invoice_settlement_subcontractor_invoice
    ON main_invoice_settlement(subcontractor_invoice_id);


-- ============================================================
-- 5. SUBCONTRACTOR STATEMENT
--
-- One statement concerns one subcontractor invoice.
-- ============================================================

CREATE TABLE subcontractor_statement (
                                         id BIGSERIAL PRIMARY KEY,

                                         subcontractor_invoice_id BIGINT NOT NULL,

                                         statement_date DATE NOT NULL,

                                         remarks VARCHAR(1000),

                                         CONSTRAINT fk_subcontractor_statement_invoice
                                             FOREIGN KEY (subcontractor_invoice_id)
                                                 REFERENCES subcontractor_invoice(id),

                                         CONSTRAINT uk_statement_subcontractor_invoice
                                             UNIQUE (subcontractor_invoice_id)
);


-- ============================================================
-- 6. DOCUMENT ATTACHMENT
--
-- Allow attachments to belong to a subcontractor statement.
-- ============================================================

ALTER TABLE document_attachment
    ADD COLUMN subcontractor_statement_id BIGINT;


ALTER TABLE document_attachment
    ADD CONSTRAINT fk_document_attachment_subcontractor_statement
        FOREIGN KEY (subcontractor_statement_id)
            REFERENCES subcontractor_statement(id);


CREATE INDEX idx_document_attachment_subcontractor_statement
    ON document_attachment(subcontractor_statement_id);


-- ============================================================
-- 7. UPDATE DOCUMENT ATTACHMENT OWNER CHECK
--
-- Attachment may belong to at most one domain object.
-- The old V3 constraint does not know about
-- subcontractor_statement_id, so it must be recreated.
-- ============================================================

ALTER TABLE document_attachment
DROP CONSTRAINT chk_document_attachment_single_owner;


ALTER TABLE document_attachment
    ADD CONSTRAINT chk_document_attachment_single_owner
        CHECK (
            num_nonnulls(
                    main_contractor_invoice_id,
                    subcontractor_invoice_id,
                    contract_id,
                    contract_amendment_id,
                    payment_id,
                    subcontractor_statement_id
            ) <= 1
            );