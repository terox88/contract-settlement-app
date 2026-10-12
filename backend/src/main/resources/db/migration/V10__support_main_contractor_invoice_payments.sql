ALTER TABLE payment_allocation
    ADD COLUMN main_contractor_invoice_id BIGINT;


ALTER TABLE payment_allocation
    ALTER COLUMN subcontractor_invoice_id DROP NOT NULL;


ALTER TABLE payment_allocation
DROP CONSTRAINT uk_payment_allocation;


ALTER TABLE payment_allocation
    ADD CONSTRAINT fk_payment_allocation_main_contractor_invoice
        FOREIGN KEY (main_contractor_invoice_id)
            REFERENCES main_contractor_invoice(id);


ALTER TABLE payment_allocation
    ADD CONSTRAINT chk_payment_allocation_invoice_owner
        CHECK (
            (
                subcontractor_invoice_id IS NOT NULL
                    AND main_contractor_invoice_id IS NULL
                )
                OR
            (
                subcontractor_invoice_id IS NULL
                    AND main_contractor_invoice_id IS NOT NULL
                )
            );


CREATE INDEX idx_payment_allocation_main_contractor_invoice
    ON payment_allocation(main_contractor_invoice_id);


CREATE UNIQUE INDEX uk_payment_allocation_subcontractor_invoice
    ON payment_allocation(
                          payment_id,
                          subcontractor_invoice_id
        )
    WHERE subcontractor_invoice_id IS NOT NULL;


CREATE UNIQUE INDEX uk_payment_allocation_main_contractor_invoice
    ON payment_allocation(
                          payment_id,
                          main_contractor_invoice_id
        )
    WHERE main_contractor_invoice_id IS NOT NULL;