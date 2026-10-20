import test from 'node:test';
import assert from 'node:assert/strict';
import { administrationPayload, administrationToForm, changeSecurityType, emptyAdministration, expiryNotice,
    formatAdministrationDate, readAdministrationOptions, validateAdministration, warrantyEndDate } from '../src/utils/contractAdministration.js';
import { buildMainContractPayload, buildMainContractUpdatePayload, contractToForm, emptyContract } from '../src/utils/mainContractForm.js';
const document = () => ({ ...emptyAdministration(), securityType: 'BANK_GUARANTEE', securityValidityMode: 'SINGLE', securityValidUntil: '2026-12-31' });
const validContract = () => ({ ...emptyContract(), contractNumber: 'UG/1', contractDate: '2026-10-20', initialValue: '1000', vatRate: '23', contractType: 'SERVICE', remunerationType: 'LUMP_SUM' });
test('Miesiące kalendarzowe: koniec miesiąca, rok przestępny i 0 miesięcy', () => {
    assert.equal(warrantyEndDate('2024-01-31', '1'), '2024-02-29');
    assert.equal(warrantyEndDate('2025-01-31', '1'), '2025-02-28');
    assert.equal(warrantyEndDate('2024-02-29', '12'), '2025-02-28');
    assert.equal(warrantyEndDate('2026-10-20', '60'), '2031-10-20');
    assert.equal(warrantyEndDate('2026-10-20', '0'), '2026-10-20');
});
test('Brak danych i nieprawidłowe daty nie tworzą daty końca gwarancji', () => {
    for (const [date, months] of [['', '12'], ['2026-10-20', ''], ['2026-02-29', '1'], ['2026-10-20', '-1'], ['2026-10-20', '1.5'], ['9999-12-31', '1']]) {
        assert.equal(warrantyEndDate(date, months), null);
    }
    assert.equal(warrantyEndDate('0001-01-31', '1'), '0001-02-28');
});
test('Próg ostrzeżenia jest mniejszy niż 45 dni; obsługuje dzisiaj i wygaśnięcie', () => {
    assert.equal(expiryNotice('2026-12-04', '2026-10-20'), null);
    assert.equal(expiryNotice('2026-12-03', '2026-10-20').days, 44);
    assert.equal(expiryNotice('2026-10-21', '2026-10-20').days, 1);
    assert.match(expiryNotice('2026-10-20', '2026-10-20').text, /dzisiaj/);
    assert.equal(expiryNotice('2026-10-19', '2026-10-20').expired, true);
    assert.equal(expiryNotice('', '2026-10-20'), null);
});
test('Dni kalendarzowe są odporne na zmianę czasu i strefy', () => {
    assert.equal(expiryNotice('2026-10-26', '2026-10-24').days, 2);
    assert.equal(expiryNotice('2026-03-30', '2026-03-28').days, 2);
    assert.equal(formatAdministrationDate('2026-10-20'), '20.10.2026');
});
test('Puste historyczne dane pozostają nieuzupełnione; null różni się od braku zabezpieczenia', () => {
    assert.deepEqual(validateAdministration(emptyAdministration()), {});
    assert.equal(administrationPayload(emptyAdministration()).securityType, null);
    assert.equal(administrationPayload({ ...emptyAdministration(), securityType: 'NOT_ESTABLISHED' }).securityType, 'NOT_ESTABLISHED');
    assert.deepEqual(administrationToForm(null), emptyAdministration());
});
test('Ubezpieczenia wygasłe oraz zakończenie przed i po terminie umownym nie blokują zapisu', () => {
    for (const actualCompletionDate of ['2025-01-01', '2028-01-01']) assert.deepEqual(validateAdministration({
        ...emptyAdministration(), actualCompletionDate, warrantyMonths: '60', ocInsuranceValidUntil: '2024-01-01', carInsuranceValidUntil: '2024-02-01',
    }), {});
});
test('Dokument zabezpieczenia wymaga wariantu i daty; dwa etapy drugiej daty', () => {
    assert.deepEqual(validateAdministration(document()), {});
    assert.ok(validateAdministration({ ...document(), securityValidUntil: '' })['administration.securityValidUntil']);
    assert.ok(validateAdministration({ ...document(), securityValidityMode: '' })['administration.securityValidityMode']);
    assert.ok(validateAdministration({ ...document(), securityValidityMode: 'TWO_STAGE' })['administration.retentionSecurityValidUntil']);
});
test('Drugi etap: daty i kwoty są spójne, kwoty pozostają dokładne do grosza', () => {
    const value = { ...document(), securityValidityMode: 'TWO_STAGE', retentionSecurityValidUntil: '2031-12-31', securityAmount: '10000', retentionSecurityAmount: '3000,01' };
    assert.deepEqual(validateAdministration(value), {});
    assert.equal(administrationPayload(value).retentionSecurityAmount, '3000.01');
    assert.ok(validateAdministration({ ...value, retentionSecurityAmount: '10000.01' })['administration.retentionSecurityAmount']);
    assert.ok(validateAdministration({ ...value, retentionSecurityValidUntil: '2026-12-30' })['administration.retentionSecurityValidUntil']);
    for (const amount of ['-1', '0', '0.001', '10000000000000']) assert.ok(validateAdministration({ ...value, securityAmount: amount })['administration.securityAmount']);
});
test('Przejście na gotówkę/brak i jeden termin usuwa niewłaściwe dane zamiast je ukrywać', () => {
    const value = { ...document(), securityValidityMode: 'TWO_STAGE', retentionSecurityValidUntil: '2031-12-31', retentionSecurityAmount: '300', securityAmount: '1000' };
    const cash = changeSecurityType(value, 'CASH');
    assert.equal(cash.securityAmount, '1000'); assert.equal(cash.securityValidUntil, ''); assert.equal(cash.retentionSecurityAmount, '');
    assert.deepEqual(validateAdministration(cash), {});
    const none = changeSecurityType(value, 'NOT_ESTABLISHED'); assert.equal(none.securityAmount, '');
    assert.deepEqual(validateAdministration(none), {});
    assert.ok(validateAdministration({ ...value, securityValidityMode: 'SINGLE' })['administration.securityValidityMode']);
});
test('POST i PUT zachowują oraz czyszczą wszystkie nowe dane, także po odczycie umowy', () => {
    const original = validContract(); original.administration = { ...document(), actualCompletionDate: '2026-10-10', warrantyMonths: '60', securityAmount: '123,45' };
    const payload = buildMainContractUpdatePayload(original);
    assert.equal(payload.administration.warrantyMonths, 60);
    assert.equal(payload.administration.securityAmount, '123.45');
    assert.deepEqual(contractToForm(payload).administration, { ...original.administration, securityAmount: '123.45' });
    assert.deepEqual(buildMainContractPayload(7, original, {}, '1234563218', { id: 12 }).administration, payload.administration);
    assert.equal(buildMainContractUpdatePayload({ ...original, administration: emptyAdministration() }).administration.actualCompletionDate, null);
});
test('Niepoprawne okresy i przekroczenie obsługiwanego roku są odrzucane', () => {
    for (const months of ['-1', '1201', '1.5', '1e2']) assert.ok(validateAdministration({ ...emptyAdministration(), warrantyMonths: months })['administration.warrantyMonths']);
    assert.ok(validateAdministration({ ...emptyAdministration(), actualCompletionDate: '9999-12-31', warrantyMonths: '1' })['administration.warrantyMonths']);
});
test('Opcje zabezpieczenia i polskie etykiety pochodzą z backendu', () => {
    const options = { securityTypes: [{ value: 'BANK_GUARANTEE', label: 'Gwarancja bankowa', document: true }], validityModes: [{ value: 'SINGLE', label: 'Jeden' }, { value: 'TWO_STAGE', label: 'Dwa' }] };
    assert.equal(readAdministrationOptions(options), options);
    assert.throws(() => readAdministrationOptions({ ...options, validityModes: [] }));
});
