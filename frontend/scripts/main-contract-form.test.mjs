import { administrationPayload, emptyAdministration } from '../src/utils/contractAdministration.js';
import assert from 'node:assert/strict';
import test from 'node:test';
import {
    buildMainContractPayload, buildMainContractUpdatePayload, contractToForm,
    emptyContract, emptyContractor, validateContractData, validateMainContractForm,
} from '../src/utils/mainContractForm.js';

const valid = () => ({
    ...emptyContract(), contractNumber: ' UG/1/2026 ', contractDate: '2026-10-14',
    initialValue: '12 345,67', vatRate: '23', contractType: 'SERVICE',
    remunerationType: 'LUMP_SUM',
});
const contractTypes = [{ value: 'SERVICE', label: 'Usługa' }];

test('Edycja wczytuje dane pierwotne, również zera i puste pola', () => {
    const form = contractToForm({
        ...valid(), initialValue: 0, vatRate: 0,
        initialStartDate: null, initialEndDate: '2026-12-01', shortDescription: null,
        currentValue: 8000, currentEndDate: '2027-03-01',
    });
    assert.equal(form.initialValue, '0');
    assert.equal(form.vatRate, '0');
    assert.equal(form.initialStartDate, '');
    assert.equal(form.initialEndDate, '2026-12-01');
    assert.equal(form.shortDescription, '');
    assert.equal(form.currentValue, undefined);
    assert.equal(form.currentEndDate, undefined);
});

test('PUT zawiera dane umowy, dokładne kwoty i null przy czyszczeniu pól', () => {
    const payload = buildMainContractUpdatePayload({
        ...valid(), shortDescription: '  ',
        investmentTaskId: 7, mainContractorId: 12,
        newMainContractor: { name: 'Firma' }, currentValue: 99000,
    });
    assert.deepEqual(payload, {
        administration: administrationPayload(emptyAdministration()),
        contractNumber: 'UG/1/2026', contractDate: '2026-10-14',
        initialValue: '12345.67', vatRate: '23.00',
        initialStartDate: null, initialEndDate: null,
        contractType: 'SERVICE', remunerationType: 'LUMP_SUM', shortDescription: null,
    });
});

test('Walidacja edycji działa bez ponownego wyboru wykonawcy', () => {
    assert.deepEqual(validateContractData(valid(), contractTypes), {});
    const errors = validateContractData({
        ...valid(), contractNumber: ' ', contractDate: '2026-02-30',
        initialValue: '-1', vatRate: '101',
        initialStartDate: '2026-11-02', initialEndDate: '2026-11-01',
    }, contractTypes);
    for (const field of ['contractNumber', 'contractDate', 'initialValue', 'vatRate', 'initialEndDate']) {
        assert.ok(errors[field], field);
    }
    assert.equal(errors.taxId, undefined);
});

test('Kwoty zachowują grosze i respektują zakres kolumny oraz VAT', () => {
    const maximum = { ...valid(), initialValue: '9999999999999,99', vatRate: '100' };
    assert.deepEqual(validateContractData(maximum, contractTypes), {});
    assert.equal(buildMainContractUpdatePayload(maximum).initialValue, '9999999999999.99');
    assert.deepEqual(validateContractData({ ...valid(), initialValue: '0', vatRate: '0' }, contractTypes), {});
    assert.ok(validateContractData({ ...valid(), initialValue: '10000000000000' }, contractTypes).initialValue);
    assert.ok(validateContractData({ ...valid(), initialValue: '1,001' }, contractTypes).initialValue);
    assert.ok(validateContractData({ ...valid(), vatRate: '100,01' }, contractTypes).vatRate);
});

test('Tworzenie z istniejącym wykonawcą wysyła jego ID', () => {
    const selected = { id: 12, taxId: '1234567890' };
    const company = emptyContractor();
    assert.deepEqual(validateMainContractForm(valid(), company, selected.taxId, {}, selected, contractTypes), {});
    const payload = buildMainContractPayload('7', valid(), company, selected.taxId, selected);
    assert.equal(payload.investmentTaskId, 7);
    assert.equal(payload.mainContractorId, 12);
    assert.equal(payload.newMainContractor, undefined);
    assert.equal(payload.initialValue, '12345.67');
});

test('Tworzenie z nowym wykonawcą nadal wymaga sprawdzenia NIP', () => {
    const company = { ...emptyContractor(), name: '  Firma  ' };
    const nip = '123-456-78-90';
    const lookup = { status: 'missing', nip: '1234567890' };
    assert.deepEqual(validateMainContractForm(valid(), company, nip, lookup, null, contractTypes), {});
    for (const status of ['idle', 'pending', 'checking', 'error', 'found']) {
        assert.ok(validateMainContractForm(valid(), company, nip, { ...lookup, status }, null, contractTypes).taxId);
    }
    const payload = buildMainContractPayload('7', valid(), company, nip, null);
    assert.equal(payload.mainContractorId, undefined);
    assert.equal(payload.newMainContractor.name, 'Firma');
    assert.equal(payload.newMainContractor.taxId, '1234567890');
    assert.equal(payload.newMainContractor.street, null);
});
