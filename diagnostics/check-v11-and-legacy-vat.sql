-- Diagnostic queries only. Run in the application database after startup.
-- This file is not a Flyway migration and does not modify any data.

-- 1. Confirm migration V11 was applied; V8 must remain the task-status migration.
SELECT installed_rank, version, script, success
FROM flyway_schema_history
WHERE version IN ('8', '9', '10', '11')
ORDER BY installed_rank;

-- 2. The constraint should contain all five ContractType constants.
SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'contract'::regclass
  AND conname = 'chk_contract_type';

-- 3a. Compare the V9 formatting pattern against a numeric format.
-- This reproduces the conversion without changing invoice rows.
SELECT rate,
       to_char(rate, 'FM999990.##') AS v9_pattern,
       rtrim(rtrim(to_char(rate, 'FM999990.00'), '0'), '.') AS numeric_pattern
FROM (VALUES (0.00::numeric), (8.00::numeric), (23.00::numeric), (7.50::numeric)) AS sample(rate);

-- 3b. Find legacy VAT labels containing '#', if any were migrated by V9.
-- No result rows means no such labels are currently present.
-- Review any returned invoice against its source document; do not blindly strip '#'.
SELECT 'GW' AS invoice_type, invoice.id AS invoice_id, invoice.invoice_number,
       rates.vat_rate
FROM main_contractor_invoice_vat_rate rates
JOIN main_contractor_invoice invoice ON invoice.id = rates.main_contractor_invoice_id
WHERE rates.vat_rate LIKE '%#%'
UNION ALL
SELECT 'PW' AS invoice_type, invoice.id AS invoice_id, invoice.invoice_number,
       rates.vat_rate
FROM subcontractor_invoice_vat_rate rates
JOIN subcontractor_invoice invoice ON invoice.id = rates.subcontractor_invoice_id
WHERE rates.vat_rate LIKE '%#%'
ORDER BY invoice_type, invoice_id, vat_rate;
