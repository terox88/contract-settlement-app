ALTER TABLE subcontractor_invoice
    ADD COLUMN deduction_confirmed_fingerprint varchar(64),
    ADD COLUMN deduction_confirmed_at timestamptz;

ALTER TABLE main_contractor_invoice
    ADD COLUMN review_fingerprint varchar(64),
    ADD COLUMN reviewed_at timestamptz,
    ADD COLUMN documents_exemption_reason varchar(1000);

ALTER TABLE document_attachment
    ADD COLUMN display_name varchar(255),
    ADD COLUMN metadata_version bigint NOT NULL DEFAULT 0;
