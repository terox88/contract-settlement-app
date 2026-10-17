import test from 'node:test';
import assert from 'node:assert/strict';
import { assignmentError, assignmentMaximum, availableInvoices, buildAssignmentRequest,
    buildInvoiceAndAssignmentRequest, buildMainInvoiceRequest, loadSettlementWorkspace,
    readMainInvoice, readMainInvoices, readSettlementWorkspace, settlementSummary } from '../src/utils/mainInvoiceSettlement.js';
import { emptyInvoice } from '../src/utils/subcontractorInvoiceForm.js';

const main = { id: 10, mainContractId: 7, invoiceNumber: 'GW/1', issueDate: '2026-10-15',
    netAmount: '100', vatAmount: '23', grossAmount: '123', currency: 'PLN', vatRates: ['23'] };
const docs = { invoiceFileCount: 1, statementFileCount: 1, paymentConfirmationFileCount: 2,
    paymentCount: 3, paymentsWithConfirmationCount: 2, unassignedPaymentConfirmationFileCount: 0 };
const invoice = { id: 40, subcontractId: 21, mainContractId: 7, invoiceNumber: 'PW/1', issueDate: '2026-10-15',
    subcontractorName: 'Firma A', subcontractNumber: 'UP/1', subcontractorTaxId: '2222222222',
    netAmount: '100', vatAmount: '23', grossAmount: '123', paidAmount: '30', deductedAmount: '3', remainingAmount: '90',
    assignedToMainInvoicesAmount: '100', remainingToAssignToMainInvoices: '23', currency: 'PLN', vatRates: ['23'], documents: docs };
const assignment = { id: 5, mainContractorInvoiceId: 10, subcontractorInvoiceId: 40, settledAmount: '60' };
const sub = { id: 21, mainContractId: 7 };
const workspace = (rows = [assignment], invoices = [invoice]) => readSettlementWorkspace(main, rows, invoices, [sub], 7, 10);
const form = { ...emptyInvoice(), invoiceNumber: ' FV/2 ', issueDate: '2026-10-15',
    netAmount: '100,00', vatAmount: '23', currency: 'pln', vatRates: '23; ZW' };

test('Kwota przypisana 60 nie jest zapłatą 30 ani pełną wartością 123', () => {
    const data = workspace();
    assert.equal(data.rows[0].settledAmount, '60');
    assert.equal(data.rows[0].invoice.paidAmount, '30');
    assert.equal(settlementSummary(data).assigned, 6000n);
    assert.equal(settlementSummary(data).unpaid, 1);
    assert.equal(settlementSummary(data).documentGaps, 1);
});

test('Edycja przypisania przywraca poprzednią kwotę do dostępnego limitu', () => {
    const maximum = assignmentMaximum(workspace().rows[0]);
    assert.equal(maximum, '83.00');
    assert.equal(assignmentError('83', maximum), '');
    assert.ok(assignmentError('83.01', maximum));
    assert.ok(assignmentError('0', maximum));
    assert.ok(assignmentError('1.001', maximum));
});

test('POST i PUT przypisania zapisują kwotę dziesiętną i właściwe identyfikatory', () => {
    assert.deepEqual(buildAssignmentRequest('10', '40', '12,34', '23'), { method: 'post', url: '/main-invoice-settlements',
        data: { mainContractorInvoiceId: 10, subcontractorInvoiceId: 40, settledAmount: '12.34' } });
    assert.deepEqual(buildAssignmentRequest('10', '40', '83', '83', 5), { method: 'put', url: '/main-invoice-settlements/5', data: { settledAmount: '83.00' } });
    assert.throws(() => buildAssignmentRequest(10, 40, '24', '23'));
});

test('Nowa faktura i częściowe przypisanie korzystają z jednego żądania', () => {
    const request = buildInvoiceAndAssignmentRequest('10', '21', form, '60');
    assert.equal(request.url, '/main-invoice-settlements/with-new-invoice');
    assert.equal(request.data.mainContractorInvoiceId, 10);
    assert.equal(request.data.invoice.subcontractId, 21);
    assert.equal(request.data.invoice.grossAmount, '123.00');
    assert.equal(request.data.settledAmount, '60.00');
    assert.throws(() => buildInvoiceAndAssignmentRequest(10, 21, form, '123.01'));
});

test('Faktura GW ma oddzielne endpointy i nie wysyła subcontractId', () => {
    const create = buildMainInvoiceRequest(7, null, form);
    assert.equal(create.url, '/main-contractor-invoices');
    assert.equal(create.data.mainContractId, 7);
    assert.equal(create.data.subcontractId, undefined);
    const edit = buildMainInvoiceRequest(7, 10, form);
    assert.equal(edit.url, '/main-contractor-invoices/10');
    assert.equal(edit.method, 'put');
    assert.equal(edit.data.mainContractId, undefined);
});

test('Wybór obejmuje tylko nieprzypisane faktury z dostępną kwotą w tej samej walucie', () => {
    const invoices = [invoice, { ...invoice, id: 41 }, { ...invoice, id: 42, currency: 'EUR' },
        { ...invoice, id: 43, remainingToAssignToMainInvoices: '0' }];
    assert.deepEqual(availableInvoices(workspace([assignment], invoices)).map((value) => value.id), [41]);
    assert.equal(availableInvoices(workspace([], invoices), '2222222222').length, 2);
    assert.equal(availableInvoices(workspace([], invoices), 'inna firma').length, 0);
});

test('Niepełny lub obcy kontekst nie jest pokazywany jako pusta poprawna lista', () => {
    assert.throws(() => readMainInvoice(main, 8));
    assert.throws(() => readMainInvoice(main, 7, 11));
    assert.throws(() => readMainInvoices({}, 7));
    assert.throws(() => workspace([assignment], []));
    assert.throws(() => workspace([{ ...assignment, mainContractorInvoiceId: 11 }]));
    assert.throws(() => workspace([assignment], [{ ...invoice, mainContractId: 8 }]));
    assert.throws(() => workspace([assignment, assignment]));
    assert.throws(() => workspace([], [{ ...invoice, remainingToAssignToMainInvoices: null }]));
});

test('Pusty zakres jest poprawnie rozpoznany bez dorabiania rozliczonych faktur', () => {
    assert.deepEqual(settlementSummary(workspace([], [])), {
        assigned: 0n, exceedsMainInvoice: false, unpaid: 0, documentGaps: 0, inconsistencies: 0,
    });
});

test('Przekroczenie faktury GW jest ostrzeżeniem i nie zależy od kwoty zapłaconej', () => {
    const rows = [assignment, { ...assignment, id: 6, subcontractorInvoiceId: 41, settledAmount: '64' }];
    const data = workspace(rows, [invoice, { ...invoice, id: 41, paidAmount: '0', remainingAmount: '123' }]);
    assert.equal(settlementSummary(data).assigned, 12400n);
    assert.equal(settlementSummary(data).exceedsMainInvoice, true);
    assert.equal(settlementSummary(data).unpaid, 2);
});

test('Oświadczenie sprzeczne z płatnościami i nadpłata zwiększają licznik rozbieżności', () => {
    assert.equal(settlementSummary(workspace([assignment], [{ ...invoice, statementSettlementMismatch: true }])).inconsistencies, 1);
    assert.equal(settlementSummary(workspace([assignment], [{ ...invoice, amountExceeded: true }])).inconsistencies, 1);
    assert.equal(settlementSummary(workspace([assignment], [{ ...invoice, documents: null }])).documentGaps, 1);
});

test('Historyczne przypisanie w obcej walucie nie jest sumowane jak PLN', () => {
    const data = workspace([assignment], [{ ...invoice, currency: 'EUR' }]);
    assert.equal(settlementSummary(data).assigned, null);
    assert.equal(settlementSummary(data).inconsistencies, 1);
    assert.equal(settlementSummary(data).exceedsMainInvoice, false);
});

test('Odczyt obejmuje faktury wszystkich podwykonawców jednej umowy głównej', async () => {
    const calls = [];
    const controller = new AbortController();
    const http = { get: async (url, options) => {
        calls.push([url, options]);
        return { data: url === '/main-contractor-invoices/10' ? main : url === '/main-invoice-settlements/main-invoice/10'
            ? [assignment] : url === '/subcontractor-invoices' ? [invoice] : [sub] };
    } };
    const data = await loadSettlementWorkspace(http, 7, 10, controller.signal);
    assert.equal(data.rows.length, 1);
    assert.equal(calls.length, 4);
    assert.deepEqual(calls.find(([url]) => url === '/subcontractor-invoices')[1].params, { mainContractId: 7 });
    assert.ok(calls.every(([, options]) => options.signal === controller.signal));
});

test('Błąd odczytu płatności lub faktur blokuje prezentację niepełnego rozliczenia', async () => {
    const http = { get: async (url) => {
        if (url === '/subcontractor-invoices') throw new Error('Brak połączenia');
        return { data: [] };
    } };
    await assert.rejects(loadSettlementWorkspace(http, 7, 10), /Brak połączenia/);
});
