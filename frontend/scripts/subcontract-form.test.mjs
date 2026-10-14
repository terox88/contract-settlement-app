import assert from 'node:assert/strict';
import test from 'node:test';
import { emptyContract, emptyContractor } from '../src/utils/mainContractForm.js';
import {
    buildSubcontractRequest, deductionsToForm, emptyDeductions, formatDeductionRule,
    getSubcontractPreview, readSubcontracts, validateSubcontractForm,
} from '../src/utils/subcontractForm.js';

const types = [{ value: 'SERVICE', label: 'Usługa' }, { value: 'FUTURE_TYPE', label: 'Nowy rodzaj' }];
const contract = () => ({ ...emptyContract(), contractNumber: ' PW/1 ', contractDate: '2026-10-14',
    initialValue: '1230', vatRate: '23', contractType: 'SERVICE', remunerationType: 'LUMP_SUM' });
const firm = { ...emptyContractor(), name: '  Firma  ', email: ' kontakt@example.pl ' };
const selected = { id: 3, taxId: '1234567890' };
const missing = { status: 'missing', nip: '1234567890' };
const configured = () => ({
    performanceSecurityDeduction: { enabled: true, percentage: '5', basis: 'NET' },
    otherDeduction: { enabled: true, percentage: '2', basis: 'GROSS' },
});
const validate = (data, deductions, editing = true, chosen = null, lookup = missing, nip = '1234567890') =>
    validateSubcontractForm(data, deductions, firm, nip, lookup, chosen, types, editing);

test('POST wiąże istniejącego podwykonawcę bez danych nowej firmy', () => {
    assert.deepEqual(validate(contract(), configured(), false, selected), {});
    const request = buildSubcontractRequest('7', undefined, contract(), configured(), firm, selected.taxId, selected);
    assert.equal(request.method, 'post');
    assert.equal(request.url, '/subcontracts');
    assert.equal(request.data.mainContractId, 7);
    assert.equal(request.data.subcontractorId, 3);
    assert.equal(request.data.newSubcontractor, undefined);
    assert.equal(request.data.contractNumber, 'PW/1');
    assert.equal(request.data.initialValue, '1230.00');
    assert.deepEqual(request.data.performanceSecurityDeduction, { percentage: '5.00', basis: 'NET' });
});

test('POST dla nowej firmy przesyła jeden wariant, znormalizowany NIP i obie reguły', () => {
    assert.deepEqual(validate(contract(), configured(), false), {});
    const request = buildSubcontractRequest('7', undefined, contract(), configured(), firm, '123-456-78-90', null);
    assert.equal(request.data.subcontractorId, undefined);
    assert.equal(request.data.newSubcontractor.name, 'Firma');
    assert.equal(request.data.newSubcontractor.taxId, '1234567890');
    assert.equal(request.data.newSubcontractor.email, 'kontakt@example.pl');
    assert.equal(request.data.newSubcontractor.street, null);
    assert.deepEqual(request.data.otherDeduction, { percentage: '2.00', basis: 'GROSS' });
});

test('PUT zawiera dane umowy i reguły, zachowując powiązania i rozliczenia', () => {
    const data = { ...contract(), subcontractorId: 999, mainContractId: 999, paidAmount: '200', currentValue: '9000' };
    const request = buildSubcontractRequest('7', '21', data, emptyDeductions(), firm, selected.taxId, selected);
    assert.equal(request.method, 'put');
    assert.equal(request.url, '/subcontracts/21');
    for (const name of ['mainContractId', 'subcontractorId', 'newSubcontractor', 'paidAmount', 'currentValue']) {
        assert.equal(request.data[name], undefined, name);
    }
    assert.equal(request.data.performanceSecurityDeduction, null);
    assert.equal(request.data.otherDeduction, null);
});

test('Reguła zerowa jest zachowana, a brak reguły jest wyłączony', () => {
    const deductions = deductionsToForm({ performanceSecurityDeduction: { percentage: 0, basis: 'NET' }, otherDeduction: null });
    assert.deepEqual(deductions.performanceSecurityDeduction, { enabled: true, percentage: '0', basis: 'NET' });
    assert.equal(deductions.otherDeduction.enabled, false);
    const request = buildSubcontractRequest('7', '21', contract(), deductions);
    assert.deepEqual(request.data.performanceSecurityDeduction, { percentage: '0.00', basis: 'NET' });
    assert.equal(request.data.otherDeduction, null);
});

test('Wyłączenie reguły usuwa ją mimo zachowanych danych pól', () => {
    const deductions = configured();
    deductions.performanceSecurityDeduction = { enabled: false, percentage: 'błędny', basis: 'UNKNOWN' };
    assert.deepEqual(validate(contract(), deductions), {});
    assert.equal(buildSubcontractRequest('7', '21', contract(), deductions).data.performanceSecurityDeduction, null);
});

test('Każdy procent ma własną podstawę i zakres od zera do stu', () => {
    for (const percentage of ['0', '100', '12,34']) {
        const deductions = configured();
        deductions.performanceSecurityDeduction.percentage = percentage;
        assert.deepEqual(validate(contract(), deductions), {});
    }
    for (const percentage of ['', '-1', '100,01', '5,001']) {
        const deductions = configured();
        deductions.performanceSecurityDeduction.percentage = percentage;
        assert.ok(validate(contract(), deductions)['performanceSecurityDeduction.percentage']);
    }
    const deductions = configured();
    deductions.otherDeduction.basis = '';
    assert.ok(validate(contract(), deductions)['otherDeduction.basis']);
});

test('Podgląd oblicza potrącenia od netto i brutto zgodnie z backendem', () => {
    const result = getSubcontractPreview(contract(), configured());
    assert.equal(result.grossCents, 123000n);
    assert.equal(result.netCents, 100000n);
    assert.equal(result.amounts.performanceSecurityDeduction, 5000n);
    assert.equal(result.amounts.otherDeduction, 2460n);
    assert.equal(result.totalCents, 7460n);
});

test('Netto oraz potrącenie są zaokrąglane HALF_UP osobno', () => {
    const deductions = emptyDeductions();
    deductions.performanceSecurityDeduction = { enabled: true, percentage: '50', basis: 'NET' };
    const result = getSubcontractPreview({ ...contract(), initialValue: '0,01' }, deductions);
    assert.equal(result.netCents, 1n);
    assert.equal(result.amounts.performanceSecurityDeduction, 1n);
});

test('Edycja zachowuje zmianę wartości z aneksów i stosuje nowy VAT i reguły', () => {
    const deductions = configured();
    deductions.otherDeduction.percentage = '2,5';
    const result = getSubcontractPreview({ ...contract(), initialValue: '1000' }, deductions,
        { initialValue: '1230', currentValue: '2460' });
    assert.equal(result.grossCents, 223000n);
    assert.equal(result.netCents, 181301n);
    assert.equal(result.amounts.performanceSecurityDeduction, 9065n);
    assert.equal(result.amounts.otherDeduction, 5575n);
    assert.equal(result.totalCents, 14640n);
    assert.equal(getSubcontractPreview({ ...contract(), vatRate: '0' }, deductions).netCents, 123000n);
});

test('Niekompletna lub błędna reguła nie tworzy pozornego podglądu', () => {
    const deductions = configured();
    deductions.otherDeduction.percentage = '';
    assert.equal(getSubcontractPreview(contract(), deductions), null);
    assert.equal(getSubcontractPreview({ ...contract(), vatRate: '101' }, emptyDeductions()), null);
});

test('Tworzenie wymaga sprawdzonego NIP i świadomego wyboru istniejącej firmy', () => {
    assert.ok(validate(contract(), emptyDeductions(), false, null, { ...missing, status: 'found' }).taxId);
    assert.ok(validate(contract(), emptyDeductions(), false, null, { ...missing, status: 'error' }).taxId);
    assert.ok(validate(contract(), emptyDeductions(), false, selected, missing, '9999999999').taxId);
    assert.deepEqual(validate(contract(), emptyDeductions(), true, null, {}, ''), {});
});

test('Walidacja nadal obsługuje przyszły rodzaj z API i kolejność terminów', () => {
    assert.deepEqual(validate({ ...contract(), contractType: 'FUTURE_TYPE' }, emptyDeductions()), {});
    assert.ok(validate({ ...contract(), initialStartDate: '2026-12-02', initialEndDate: '2026-12-01' }, emptyDeductions()).initialEndDate);
});

test('Lista pokazuje tylko umowy wskazanej umowy głównej i rozróżnia brak od błędu', () => {
    const list = [{ id: 21, mainContractId: 7, contractDate: '2026-10-14' }, { id: 22, mainContractId: 8, contractDate: '2026-10-15' }];
    assert.deepEqual(readSubcontracts(list, '7').map((entry) => entry.id), [21]);
    assert.equal(list.length, 2);
    assert.deepEqual(readSubcontracts([], '7'), []);
    assert.throws(() => readSubcontracts({ message: 'Błąd' }, '7'));
});

test('Opisy reguł mają właściwy procent, podstawę i brak ustawienia', () => {
    assert.equal(formatDeductionRule({ percentage: '100.00', basis: 'GROSS' }), '100% brutto');
    assert.equal(formatDeductionRule({ percentage: '5.10', basis: 'NET' }), '5.1% netto');
    assert.equal(formatDeductionRule({ percentage: '0.00', basis: 'NET' }), '0% netto');
    assert.equal(formatDeductionRule(null), 'Nie ustalono');
});
