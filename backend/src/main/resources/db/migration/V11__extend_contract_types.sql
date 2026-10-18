-- V11: keep the database constraint aligned with ContractType after V1-V10.
-- Applies to both main contracts and subcontracts stored in the contract table.

ALTER TABLE contract
    DROP CONSTRAINT IF EXISTS chk_contract_type;

ALTER TABLE contract
    ADD CONSTRAINT chk_contract_type
        CHECK (
            contract_type IN (
                'SERVICE',
                'DESIGN_AND_BUILD',
                'CONSTRUCTION_WORKS',
                'DELIVERY',
                'OTHER'
            )
        );
