import assert from 'node:assert/strict';
import test from 'node:test';
import {
    amendmentToForm, buildAmendmentRequest, emptyAmendment, formatAmendmentChange,
    getAmendmentPreview, parseAmendmentAmount, readAmendmentList, validateAmendmentForm,
} from '../src/utils/contractAmendmentForm.js';

const contract = {
    id: 7, contractDate: '2026-10-01', initialStartDate: '2026-10-10',
    initialEndDate: '2027-02-01', initialValue: '100.00', currentValue: '100.00',
    currentEndDate: '2027-02-01',
};
const valid = () => ({
    ...emptyAmendment(), amendmentNumber: ' A/1 ', amendmentDate: '2026-10-15', effectiveDate: '2026-10-15',
});
const row = (id, date, change, endDate = null) => ({
    id, contractId: 7, amendmentNumber: `A/${id}`, amendmentDate: date,
    effectiveDate: date, valueChange: change, newEndDate: endDate,
});

test('Kwoty zmian obsługują oba znaki i zachowują grosze', () => {
    assert.deepEqual(parseAmendmentAmount('-1 234,56'), { cents: -123456n, amount: '-1234.56' });
    assert.deepEqual(parseAmendmentAmount('+12.30'), { cents: 1230n, amount: '12.30' });
    assert.deepEqual(parseAmendmentAmount('−12,30'), { cents: -1230n, amount: '-12.30' });
    assert.deepEqual(parseAmendmentAmount('-0'), { cents: 0n, amount: '0.00' });
    for (const value of ['', '1,001', '--1', '+-1', '1e3', '12 34']) assert.equal(parseAmendmentAmount(value), null);
});

test('Brak zmiany jest odróżniony od jawnego zera i znaków kwot', () => {
    assert.equal(formatAmendmentChange(null), 'Bez zmiany wartości');
    assert.equal(formatAmendmentChange(0), '0,00 zł');
    assert.equal(formatAmendmentChange('10.20'), '+10,20 zł');
    assert.equal(formatAmendmentChange('-10.20'), '−10,20 zł');
});

test('Pobranie do edycji zachowuje ujemne wartości, zero i null', () => {
    const form = amendmentToForm({ ...valid(), valueChange: -12.5, newEndDate: null, description: null });
    assert.equal(form.valueChange, '-12.5');
    assert.equal(form.newEndDate, '');
    assert.equal(form.description, '');
    assert.equal(amendmentToForm({ ...valid(), valueChange: 0 }).valueChange, '0');
});

test('POST zawiera ID umowy i kwotę dziesiętną, PUT nie zmienia powiązania', () => {
    const form = { ...valid(), valueChange: '-12,30', description: '  Korekta  ' };
    const create = buildAmendmentRequest('7', undefined, form);
    assert.deepEqual(create, {
        method: 'post', url: '/contract-amendments', data: {
            amendmentNumber: 'A/1', amendmentDate: '2026-10-15', effectiveDate: '2026-10-15',
            valueChange: '-12.30', newEndDate: null, description: 'Korekta', contractId: 7,
        },
    });
    const edit = buildAmendmentRequest('7', '19', { ...valid(), valueChange: '', description: ' ' });
    assert.equal(edit.method, 'put');
    assert.equal(edit.url, '/contract-amendments/19');
    assert.equal(edit.data.contractId, undefined);
    assert.equal(edit.data.valueChange, null);
    assert.equal(edit.data.description, null);
});

test('Daty odpowiadają regułom backendu, również dla aneksów wstecznych', () => {
    assert.deepEqual(validateAmendmentForm({ ...valid(), amendmentDate: '2026-11-01', effectiveDate: '2026-10-20' }, contract, []), {});
    const errors = validateAmendmentForm({ ...valid(), amendmentDate: '2026-09-30',
        effectiveDate: '2026-09-30', newEndDate: '2026-10-09' }, contract, []);
    assert.ok(errors.amendmentDate);
    assert.ok(errors.effectiveDate);
    assert.ok(errors.newEndDate);
    assert.ok(validateAmendmentForm({ ...valid(), effectiveDate: '2026-02-30' }, contract, []).effectiveDate);
});

test('Ta sama data obowiązywania jest odrzucana poza edytowanym aneksem', () => {
    const amendments = [row(19, '2026-10-15', null)];
    assert.ok(validateAmendmentForm(valid(), contract, amendments).effectiveDate);
    assert.deepEqual(validateAmendmentForm(valid(), contract, amendments, '19'), {});
});

test('Ujemna historia jest odrzucana nawet gdy końcowa wartość jest dodatnia', () => {
    const amendments = [row(1, '2026-11-01', '-90'), row(2, '2026-12-01', '200')];
    const form = { ...valid(), valueChange: '-20' };
    const preview = getAmendmentPreview(form, contract, amendments);
    assert.equal(preview.proposedValueCents, 19000n);
    assert.equal(preview.negativeAtDate, '2026-11-01');
    assert.ok(validateAmendmentForm(form, contract, amendments).valueChange);
});

test('Edycja zastępuje starą zmianę i sprawdza także późniejsze aneksy', () => {
    const amendments = [row(1, '2026-11-01', '100'), row(2, '2026-12-01', '-90')];
    const reduced = { ...valid(), effectiveDate: '2026-11-01', valueChange: '-20' };
    const preview = getAmendmentPreview(reduced, contract, amendments, '1');
    assert.equal(preview.proposedValueCents, -1000n);
    assert.equal(preview.negativeAtDate, '2026-12-01');
    assert.ok(validateAmendmentForm(reduced, contract, amendments, '1').valueChange);
    const corrected = { ...reduced, valueChange: '30' };
    assert.equal(getAmendmentPreview(corrected, contract, amendments, '1').proposedValueCents, 4000n);
    assert.deepEqual(validateAmendmentForm(corrected, contract, amendments, '1'), {});
});

test('Termin pochodzi z ostatniej zmiany według daty obowiązywania', () => {
    const amendments = [row(1, '2026-11-01', null, '2027-04-01'), row(2, '2026-12-01', null, '2027-03-01')];
    const form = { ...valid(), newEndDate: '2027-09-01' };
    assert.equal(getAmendmentPreview(form, contract, amendments).proposedEndDate, '2027-03-01');
    const clearLast = { ...valid(), effectiveDate: '2026-12-01', newEndDate: '' };
    assert.equal(getAmendmentPreview(clearLast, contract, amendments, '2').proposedEndDate, '2027-04-01');
});

test('Aneks opisowy i całkowite obniżenie do zera są dozwolone', () => {
    assert.deepEqual(validateAmendmentForm(valid(), contract, []), {});
    const zero = { ...valid(), valueChange: '-100' };
    assert.deepEqual(validateAmendmentForm(zero, contract, []), {});
    assert.equal(getAmendmentPreview(zero, contract, []).proposedValueCents, 0n);
    assert.ok(validateAmendmentForm({ ...valid(), valueChange: '-100,01' }, contract, []).valueChange);
});

test('Zakresy kwoty, numeru i opisu odpowiadają DTO i bazie', () => {
    const large = { ...contract, initialValue: '9999999999999.99', currentValue: '9999999999999.99' };
    assert.deepEqual(validateAmendmentForm({ ...valid(), valueChange: '-9999999999999,99' }, large, []), {});
    assert.ok(validateAmendmentForm({ ...valid(), valueChange: '10000000000000' }, contract, []).valueChange);
    assert.ok(validateAmendmentForm({ ...valid(), valueChange: '-10000000000000' }, large, []).valueChange);
    const errors = validateAmendmentForm({ ...valid(), amendmentNumber: 'x'.repeat(256), description: 'x'.repeat(2001) }, contract, []);
    assert.ok(errors.amendmentNumber);
    assert.ok(errors.description);
});

test('Lista jest sortowana bez mutacji i odrzuca dane obcej umowy lub błędną odpowiedź', () => {
    const input = [row(2, '2026-12-01', '-10'), row(1, '2026-11-01', null)];
    const result = readAmendmentList(input, '7');
    assert.deepEqual(result.map((entry) => entry.id), [1, 2]);
    assert.equal(input[0].id, 2);
    assert.deepEqual(readAmendmentList([], '7'), []);
    assert.throws(() => readAmendmentList({ message: 'Błąd' }, '7'));
    assert.throws(() => readAmendmentList([row(1, '2026-11-01', null)], '8'));
    assert.throws(() => readAmendmentList([row(1, '2026-02-30', null)], '7'));
});
