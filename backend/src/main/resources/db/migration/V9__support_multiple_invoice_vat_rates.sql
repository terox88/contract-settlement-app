CREATE TABLE main_contractor_invoice_vat_rate (
                                                  main_contractor_invoice_id BIGINT NOT NULL,
                                                  vat_rate VARCHAR(20) NOT NULL,

                                                  CONSTRAINT pk_main_contractor_invoice_vat_rate
                                                      PRIMARY KEY (
                                                                   main_contractor_invoice_id,
                                                                   vat_rate
                                                          ),

                                                  CONSTRAINT fk_main_invoice_vat_rate_invoice
                                                      FOREIGN KEY (
                                                                   main_contractor_invoice_id
                                                          )
                                                          REFERENCES main_contractor_invoice(id)
                                                          ON DELETE CASCADE
);


CREATE TABLE subcontractor_invoice_vat_rate (
                                                subcontractor_invoice_id BIGINT NOT NULL,
                                                vat_rate VARCHAR(20) NOT NULL,

                                                CONSTRAINT pk_subcontractor_invoice_vat_rate
                                                    PRIMARY KEY (
                                                                 subcontractor_invoice_id,
                                                                 vat_rate
                                                        ),

                                                CONSTRAINT fk_subcontractor_invoice_vat_rate_invoice
                                                    FOREIGN KEY (
                                                                 subcontractor_invoice_id
                                                        )
                                                        REFERENCES subcontractor_invoice(id)
                                                        ON DELETE CASCADE
);


/*
 * Migracja dotychczasowych pojedynczych stawek VAT.
 */

INSERT INTO main_contractor_invoice_vat_rate (
    main_contractor_invoice_id,
    vat_rate
)
SELECT
    id,
    TO_CHAR(
            vat_rate,
            'FM999990.##'
    )
FROM main_contractor_invoice
WHERE vat_rate IS NOT NULL;


INSERT INTO subcontractor_invoice_vat_rate (
    subcontractor_invoice_id,
    vat_rate
)
SELECT
    id,
    TO_CHAR(
            vat_rate,
            'FM999990.##'
    )
FROM subcontractor_invoice
WHERE vat_rate IS NOT NULL;



ALTER TABLE main_contractor_invoice
DROP COLUMN vat_rate;

ALTER TABLE subcontractor_invoice
DROP COLUMN vat_rate;