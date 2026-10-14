import assert from 'node:assert/strict';
import test from 'node:test';
import { formatContractType, readContractTypeOptions } from '../src/utils/contractTypes.js';
import {
    buildMainContractUpdatePayload, emptyContract, emptyContractor,
    validateContractData, validateMainContractForm,
} from '../src/utils/mainContractForm.js';

const dictionary = [
    { value: 'DELIVERY', label: 'Dostawy' },
    { value: 'OTHER', label: 'Inne' },
    { value: 'FUTURE_TYPE', label: 'Nowy rodzaj dodany tylko w backendzie' },
];
const valid = (type) => ({
    ...emptyContract(), contractNumber: 'UG/1', contractDate: '2026-10-14',
    initialValue: '1000', vatRate: '23', contractType: type, remunerationType: 'LUMP_SUM',
});

test('Lista z API obsługuje dostawy, inne i nieznany wcześniej rodzaj', () => {
    const options = readContractTypeOptions(dictionary);
    assert.deepEqual(options, dictionary);
    for (const option of options) {
        const contract = valid(option.value);
        assert.deepEqual(validateContractData(contract, options), {});
        assert.equal(buildMainContractUpdatePayload(contract).contractType, option.value);
        assert.deepEqual(validateMainContractForm(contract, emptyContractor(), '1234567890', {},
            { id: 12, taxId: '1234567890' }, options), {});
    }
});

test('Walidacja wymaga wyboru wartości pobranej z API', () => {
    assert.ok(validateContractData(valid('OTHER'), []).contractType);
    assert.ok(validateContractData(valid('UNKNOWN'), readContractTypeOptions(dictionary)).contractType);
    assert.ok(validateContractData(valid(''), readContractTypeOptions(dictionary)).contractType);
});

test('Uszkodzony lub pusty słownik jest odrzucany', () => {
    for (const data of [null, {}, [], ['DELIVERY'], [null], [{ value: 'DELIVERY' }],
        [{ value: '', label: 'Dostawy' }], [{ value: 'DELIVERY', label: ' ' }],
        [{ value: 'DELIVERY', label: 'Dostawy' }, { value: 'DELIVERY', label: 'Duplikat' }]]) {
        assert.throws(() => readContractTypeOptions(data), /rodzajów umowy/);
    }
});

test('Widok używa etykiety z odpowiedzi i obsługuje brak etykiety', () => {
    assert.equal(formatContractType({ contractType: 'DELIVERY', contractTypeLabel: 'Dostawy' }), 'Dostawy');
    assert.equal(formatContractType({ contractType: 'FUTURE_TYPE', contractTypeLabel: 'Nowa nazwa' }), 'Nowa nazwa');
    assert.equal(formatContractType({ contractType: 'DELIVERY' }), 'DELIVERY');
    assert.equal(formatContractType({}), '—');
});
