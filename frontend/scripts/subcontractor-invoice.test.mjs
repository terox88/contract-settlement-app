import assert from 'node:assert/strict';
import test from 'node:test';
import {
    buildInvoiceRequest, calculatedGross, emptyInvoice, filterInvoices, formatInvoiceAmount,
    getInvoiceDocumentItems, invoiceToForm, invoiceTotals, normalizeVatRates, readInvoice, readInvoices,
    validateInvoiceForm,
} from '../src/utils/subcontractorInvoiceForm.js';

const form = () => ({ ...emptyInvoice(), invoiceNumber: ' FV/1 ', issueDate: '2026-10-15',
    netAmount: '100,00', vatAmount: '23', vatRates: '23; zw; 23', currency: 'pln' });
const documents = { invoiceFileCount: 1, statementFileCount: 1, paymentConfirmationFileCount: 1,
    paymentCount: 1, paymentsWithConfirmationCount: 1, unassignedPaymentConfirmationFileCount: 0 };
const row = () => ({ id: 4, subcontractId: 21, invoiceNumber: 'FV/1', issueDate: '2026-10-15',
    netAmount: '100', vatAmount: '23', grossAmount: '123', paidAmount: '50', deductedAmount: '3',
    remainingAmount: '70', currency: 'PLN', vatRates: ['23', 'ZW'], settled: false, hasStatement: true, documents });

test('POST normalizuje kwoty, walutę i wiele stawek VAT, przypisując umowę', () => {
    const request = buildInvoiceRequest('21', undefined, form());
    assert.equal(request.method, 'post');
    assert.equal(request.url, '/subcontractor-invoices');
    assert.deepEqual(request.data, { subcontractId: 21, invoiceNumber: 'FV/1', issueDate: '2026-10-15',
        saleDate: null, dueDate: null, netAmount: '100.00', vatAmount: '23.00', grossAmount: '123.00',
        currency: 'PLN', vatRates: ['23', 'ZW'], description: null });
});

test('PUT nie przesyła powiązań, statusów dokumentów ani kwot rozliczeń', () => {
    const request = buildInvoiceRequest('21', '4', { ...form(), subcontractId: 99, documents,
        paidAmount: '9999', settled: true, hasStatement: true, editable: true });
    assert.equal(request.method, 'put');
    assert.equal(request.url, '/subcontractor-invoices/4');
    for (const key of ['subcontractId', 'documents', 'paidAmount', 'settled', 'hasStatement', 'editable']) {
        assert.equal(request.data[key], undefined);
    }
});

test('Brutto jest sumowane dokładnie do grosza', () => {
    assert.deepEqual(calculatedGross({ netAmount: '0,10', vatAmount: '0,20' }), { cents: 30n, amount: '0.30' });
    assert.equal(calculatedGross({ netAmount: '', vatAmount: '0' }), null);
    assert.equal(calculatedGross({ netAmount: '9999999999999.98', vatAmount: '0.01' }).amount, '9999999999999.99');
});

test('Kwoty nie mogą być ujemne, zbyt duże ani mieć ukrytych ułamków grosza', () => {
    for (const netAmount of ['', '-1', '100.001', '10000000000000']) {
        assert.ok(validateInvoiceForm({ ...form(), netAmount }).netAmount);
    }
    assert.ok(validateInvoiceForm({ ...form(), netAmount: '0', vatAmount: '0' }).netAmount);
    assert.ok(validateInvoiceForm({ ...form(), netAmount: '9999999999999.99', vatAmount: '0.01' }).netAmount);
    assert.deepEqual(validateInvoiceForm({ ...form(), vatAmount: '0', vatRates: 'ZW' }), {});
});

test('Stawki są zachowane jako lista, także ZW i NP, bez narzucania jednego procentu', () => {
    assert.deepEqual(normalizeVatRates('23; 8; zw; NP; zw'), ['23', '8', 'ZW', 'NP']);
    assert.deepEqual(normalizeVatRates('0 EX; 5,5'), ['0 EX', '5,5']);
    assert.ok(validateInvoiceForm({ ...form(), vatRates: '' }).vatRates);
    assert.ok(validateInvoiceForm({ ...form(), vatRates: 'x'.repeat(21) }).vatRates);
});

test('Walidacja respektuje długości kolumn i poprawność dat', () => {
    assert.ok(validateInvoiceForm({ ...form(), invoiceNumber: 'x'.repeat(256) }).invoiceNumber);
    assert.ok(validateInvoiceForm({ ...form(), description: 'x'.repeat(1001) }).description);
    assert.ok(validateInvoiceForm({ ...form(), currency: 'PL' }).currency);
    assert.ok(validateInvoiceForm({ ...form(), issueDate: '2026-02-29' }).issueDate);
    assert.ok(validateInvoiceForm({ ...form(), saleDate: '2026-15-01' }).saleDate);
    assert.deepEqual(validateInvoiceForm({ ...form(), saleDate: '2026-09-30', dueDate: '2026-11-01' }), {});
});

test('Odczyt do edycji zachowuje zera, puste daty i wiele stawek', () => {
    const invoice = invoiceToForm({ ...row(), vatAmount: 0, vatRates: ['ZW'], description: null });
    assert.equal(invoice.vatAmount, '0');
    assert.equal(invoice.saleDate, '');
    assert.equal(invoice.vatRates, 'ZW');
    assert.equal(invoice.description, '');
});

test('Lista odrzuca fakturę obcej umowy i uszkodzone dane zamiast pokazywać zerowe kwoty', () => {
    assert.deepEqual(readInvoices([], '21'), []);
    assert.equal(readInvoice(row(), '21').id, 4);
    assert.throws(() => readInvoices([row()], '22'));
    assert.throws(() => readInvoices({ content: [] }, '21'));
    assert.throws(() => readInvoice({ ...row(), paidAmount: null }, '21'));
    assert.throws(() => readInvoice({ ...row(), currency: '<bad>' }, '21'));
});

test('Podsumowanie grupuje waluty i zachowuje ujemne saldo', () => {
    const totals = invoiceTotals([row(), { ...row(), currency: 'EUR', remainingAmount: '-0.01' }]);
    assert.equal(totals.length, 2);
    assert.equal(totals[0].grossAmount, 12300n);
    assert.equal(totals[1].remainingAmount, -1n);
    assert.match(formatInvoiceAmount('123.45', 'EUR'), /EUR$/);
    assert.equal(formatInvoiceAmount(null), '—');
});

test('Nieznane statusy dokumentów nie są przedstawiane jako brak plików', () => {
    assert.ok(getInvoiceDocumentItems({ hasStatement: true }).every((item) => item.tone === 'unknown'));
    assert.ok(getInvoiceDocumentItems({ documents: { ...documents, paymentsWithConfirmationCount: 2 } })
        .every((item) => item.tone === 'unknown'));
});

test('Oświadczenie w bazie i plik oświadczenia są odróżnione', () => {
    const items = getInvoiceDocumentItems({ ...row(), documents: { ...documents, statementFileCount: 0 } });
    assert.equal(items[1].tone, 'missing');
    assert.equal(items[1].label, 'Brak pliku');
    assert.match(items[1].detail, /zarejestrowane/);
    const fileOnly = getInvoiceDocumentItems({ ...row(), hasStatement: false });
    assert.equal(fileOnly[1].tone, 'good');
    assert.match(fileOnly[1].detail, /nie zostało zarejestrowane/);
});

test('Potwierdzenia pokazują kompletność wszystkich przelewów', () => {
    const items = getInvoiceDocumentItems({ ...row(), documents: { ...documents, paymentCount: 3, paymentsWithConfirmationCount: 2 } });
    assert.equal(items[2].label, '2 z 3 przelewów');
    assert.equal(items[2].tone, 'missing');
    assert.equal(getInvoiceDocumentItems(row())[2].tone, 'good');
});

test('Brak przelewów jest neutralny; plik bez powiązania nie potwierdza przelewu', () => {
    const noPayments = { ...documents, paymentCount: 0, paymentsWithConfirmationCount: 0, paymentConfirmationFileCount: 0 };
    assert.equal(getInvoiceDocumentItems({ documents: noPayments })[2].tone, 'neutral');
    const unassigned = getInvoiceDocumentItems({ documents: { ...noPayments, paymentConfirmationFileCount: 1, unassignedPaymentConfirmationFileCount: 1 } })[2];
    assert.equal(unassigned.label, 'Brak przelewów');
    assert.equal(unassigned.tone, 'missing');
    assert.match(unassigned.detail, /Bez powiązania/);
});

test('Filtry numeru, rozliczenia i braków działają łącznie', () => {
    const rows = [row(), { ...row(), id: 5, invoiceNumber: 'FV/2', settled: true, documents: { ...documents, invoiceFileCount: 0 } }];
    assert.deepEqual(filterInvoices(rows, 'fv/', 'settled', true).map((invoice) => invoice.id), [5]);
    assert.deepEqual(filterInvoices(rows, 'fv/1', 'all', true), []);
    assert.equal(rows.length, 2);
});
