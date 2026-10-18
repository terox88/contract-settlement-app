-- Existing subcontracts remain direct children of their main contract.
ALTER TABLE subcontract
    ADD COLUMN parent_subcontract_id BIGINT,
    ADD CONSTRAINT uk_subcontract_root UNIQUE (id, main_contract_id),
    ADD CONSTRAINT chk_subcontract_not_own_parent
        CHECK (parent_subcontract_id IS NULL OR parent_subcontract_id <> id);

ALTER TABLE subcontract
    ADD CONSTRAINT fk_subcontract_parent_same_root
        FOREIGN KEY (parent_subcontract_id, main_contract_id)
            REFERENCES subcontract(id, main_contract_id);

CREATE INDEX idx_subcontract_parent ON subcontract(parent_subcontract_id);

-- Reuse existing assignments, adding an alternative parent invoice for lower tiers.
ALTER TABLE main_invoice_settlement
    ADD COLUMN parent_subcontractor_invoice_id BIGINT,
    ALTER COLUMN main_contractor_invoice_id DROP NOT NULL;

ALTER TABLE main_invoice_settlement
    ADD CONSTRAINT fk_settlement_parent_subcontractor_invoice
        FOREIGN KEY (parent_subcontractor_invoice_id) REFERENCES subcontractor_invoice(id),
    ADD CONSTRAINT chk_settlement_one_parent
        CHECK (num_nonnulls(main_contractor_invoice_id, parent_subcontractor_invoice_id) = 1),
    ADD CONSTRAINT chk_settlement_not_own_parent
        CHECK (parent_subcontractor_invoice_id IS NULL OR parent_subcontractor_invoice_id <> subcontractor_invoice_id);

CREATE UNIQUE INDEX uk_settlement_parent_subcontractor_invoice
    ON main_invoice_settlement(parent_subcontractor_invoice_id, subcontractor_invoice_id)
    WHERE parent_subcontractor_invoice_id IS NOT NULL;
