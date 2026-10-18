import test from 'node:test';
import assert from 'node:assert/strict';
import { buildAssignmentRequest, buildInvoiceAndAssignmentRequest, availableInvoices, loadSettlementWorkspace,
    readSettlementWorkspace, settlementSummary } from '../src/utils/mainInvoiceSettlement.js';
import { loadSameRecipientTargets, samePaymentParties, paymentMatchesTarget, validatePaymentAllocations, emptyPayment } from '../src/utils/paymentForm.js';
import { emptyInvoice } from '../src/utils/subcontractorInvoiceForm.js';
import { resolveTaskNavigation } from '../src/utils/taskNavigation.js';

const parent = { id: 40, subcontractId: 21, mainContractId: 7, invoiceNumber: 'PW/1', issueDate: '2026-10-15',
    netAmount: '100', vatAmount: '23', grossAmount: '123', paidAmount: '30', deductedAmount: '3', remainingAmount: '90',
    assignedToMainInvoicesAmount: '100', remainingToAssignToMainInvoices: '23', currency: 'PLN', vatRates: ['23'] };
const child = { ...parent, id: 41, subcontractId: 22, subcontractorId: 3, payerContractorId: 2, parentSubcontractId: 21, invoiceNumber: 'DPW/1' };
const contracts = [{ id: 21, mainContractId: 7, parentSubcontractId: null },
    { id: 22, mainContractId: 7, parentSubcontractId: 21 }, { id: 23, mainContractId: 7, parentSubcontractId: 22 }];
const assignment = { id: 9, parentSubcontractorInvoiceId: 40, subcontractorInvoiceId: 41, settledAmount: '20' };
const form = { ...emptyInvoice(), invoiceNumber: 'DPW/2', issueDate: '2026-10-15', netAmount: '10', vatAmount: '2.30', vatRates: '23' };
const target = { type: 'sub', id: 41, contractId: 7, subcontractId: 22, recipientContractorId: 3, payerContractorId: 2, currency: 'PLN' };
const paymentForm = { ...emptyPayment(), amount: '100' };

test('Faktura PW rozlicza tylko bezpośrednie faktury kolejnego poziomu', () => {
    const deeper = { ...child, id: 42, subcontractId: 23, parentSubcontractId: 22 };
    const workspace = readSettlementWorkspace(parent, [assignment], [parent, child, deeper], contracts, 7, 40, 21);
    assert.equal(workspace.parentType, 'sub');
    assert.deepEqual(workspace.invoices.map((invoice) => invoice.id), [41]);
    assert.deepEqual(workspace.subcontracts.map((contract) => contract.id), [22]);
    assert.equal(settlementSummary(workspace).assigned, 2000n);
});
test('Przy fakturze GW wybór nie zawiera dalszych poziomów', () => {
    const main = { ...parent, id: 10 };
    const workspace = readSettlementWorkspace(main, [], [parent, child], contracts, 7, 10);
    assert.deepEqual(availableInvoices(workspace).map((invoice) => invoice.id), [40]);
});
test('Nie można przypisać faktury z innej gałęzi albo pominąć poziomu', () => {
    assert.throws(() => readSettlementWorkspace(parent, [{ ...assignment, subcontractorInvoiceId: 40 }], [parent, child], contracts, 7, 40, 21));
    assert.throws(() => readSettlementWorkspace(parent, [{ ...assignment, parentSubcontractorInvoiceId: 99 }], [child], contracts, 7, 40, 21));
    assert.throws(() => readSettlementWorkspace({ ...parent, mainContractId: 8 }, [], [child], contracts, 7, 40, 21));
});
test('Przypisanie dalszej faktury wskazuje rodzica PW, bez identyfikatora faktury GW', () => {
    const request = buildAssignmentRequest(40, 41, '12,34', '23', null, 'sub');
    assert.deepEqual(request.data, { parentSubcontractorInvoiceId: 40, subcontractorInvoiceId: 41, settledAmount: '12.34' });
    assert.deepEqual(buildAssignmentRequest(40, 41, '12', '23', 9, 'sub').data, { settledAmount: '12.00' });
});
test('Nowa dalsza faktura i przypisanie są zapisywane jednym żądaniem', () => {
    const request = buildInvoiceAndAssignmentRequest(40, 22, form, '10', 'sub');
    assert.equal(request.url, '/main-invoice-settlements/with-new-invoice');
    assert.equal(request.data.parentSubcontractorInvoiceId, 40);
    assert.equal(request.data.mainContractorInvoiceId, undefined);
    assert.equal(request.data.invoice.subcontractId, 22);
    assert.equal(request.data.invoice.grossAmount, '12.30');
});
test('Odczyt rozliczenia PW korzysta z właściwych endpointów', async () => {
    const calls = [];
    const data = { '/subcontractor-invoices/40': parent, '/main-invoice-settlements/parent-invoice/40': [assignment],
        '/subcontractor-invoices': [parent, child], '/subcontracts': contracts };
    const http = { get: async (url) => { calls.push(url); return { data: data[url] }; } };
    const workspace = await loadSettlementWorkspace(http, 7, 40, undefined, 21);
    assert.equal(workspace.rows[0].invoice.id, 41);
    assert.ok(!calls.some((url) => url.startsWith('/main-contractor-invoices')));
});
test('Jeden przelew obejmuje kilka faktur tej samej firmy i płatnika', () => {
    assert.equal(samePaymentParties(target, { ...target, id: 42, subcontractId: 28 }), true);
    assert.deepEqual(validatePaymentAllocations(paymentForm, [{ target, allocatedAmount: '50', deductedAmount: '1' },
        { target: { ...target, id: 42 }, allocatedAmount: '50', deductedAmount: '2' }]), {});
});
test('Kolejne faktury PW są pobierane dla odbiorcy i filtrowane według płatnika', async () => {
    const calls = [];
    const http = { get: async (...args) => { calls.push(args); return { data: [child, { ...child, id: 42, payerContractorId: 1 }, { ...child, id: 43, subcontractorId: 4 }] }; } };
    const rows = await loadSameRecipientTargets(http, target);
    assert.deepEqual(rows.map((row) => row.id), [41]);
    assert.deepEqual(calls[0][1].params, { subcontractorId: 3 });
});
test('Kolejne faktury GW pochodzą tylko z umów tego samego wykonawcy', async () => {
    const calls = [];
    const data = { '/main-contracts': [{ id: 7, mainContractorId: 9 }, { id: 8, mainContractorId: 10 }, { id: 9, mainContractorId: 9 }],
        '/main-contractor-invoices/main-contract/7': [{ ...parent, id: 10, mainContractorId: 9 }],
        '/main-contractor-invoices/main-contract/9': [{ ...parent, id: 11, mainContractId: 9, mainContractorId: 9 }] };
    const rows = await loadSameRecipientTargets({ get: async (url) => { calls.push(url); return { data: data[url] }; } }, { type: 'main', recipientContractorId: 9 });
    assert.deepEqual(rows.map((row) => row.id), [10, 11]);
    assert.equal(calls.length, 3);
    assert.ok(!calls.includes('/main-contractor-invoices/main-contract/8'));
});
test('Inny odbiorca, płatnik i mieszanie faktur GW z PW są blokowane', () => {
    for (const second of [{ ...target, recipientContractorId: 4 }, { ...target, payerContractorId: 1 }, { ...target, type: 'main' }]) {
        assert.equal(samePaymentParties(target, second), false);
        const errors = validatePaymentAllocations(paymentForm, [{ target, allocatedAmount: '50', deductedAmount: '0' },
            { target: { ...second, id: 42 }, allocatedAmount: '50', deductedAmount: '0' }]);
        assert.match(errors.allocations, /tego samego odbiorcy/);
    }
});
test('Wybór istniejącego przelewu odrzuca innego odbiorcę i dawne mieszane przypisania', () => {
    const payment = { allocationCount: 1, mainInvoicePayment: false, recipientContractorId: 3, payerContractorId: 2 };
    assert.equal(paymentMatchesTarget(payment, target), true);
    assert.equal(paymentMatchesTarget({ ...payment, recipientContractorId: 4 }, target), false);
    assert.equal(paymentMatchesTarget({ ...payment, mixedRecipients: true }, target), false);
    assert.equal(paymentMatchesTarget({ allocationCount: 0 }, target), true);
});
test('Nawigacja pokazuje cały łańcuch umów', async () => {
    const data = { '/investment-tasks/1': { id: 1, taskNumber: 'Z/1' }, '/main-contracts/7': { id: 7, investmentTaskId: 1 },
        '/subcontracts/21': { ...contracts[0], contractNumber: 'PW/1' }, '/subcontracts/22': { ...contracts[1], contractNumber: 'DPW/1' },
        '/subcontractor-invoices/41': child };
    const http = { get: async (url) => ({ data: data[url] }) };
    const nav = await resolveTaskNavigation(http, '/main-contracts/7/subcontracts/22/invoices/41/settlements');
    assert.deepEqual(nav.crumbs.slice(0, 5).map((crumb) => crumb.url), ['/tasks', '/tasks/1', '/main-contracts/7',
        '/main-contracts/7/subcontracts/21', '/main-contracts/7/subcontracts/22']);
    assert.equal(nav.crumbs.at(-1).url, '/main-contracts/7/subcontracts/22/invoices/41/settlements');
});
test('Cykl albo rodzic z innej umowy głównej nie tworzą błędnej ścieżki', async () => {
    for (const bad of [{ ...contracts[0], parentSubcontractId: 22 }, { ...contracts[0], mainContractId: 99 }]) {
        const data = { '/investment-tasks/1': { id: 1 }, '/main-contracts/7': { id: 7, investmentTaskId: 1 },
            '/subcontracts/22': contracts[1], '/subcontracts/21': bad };
        await assert.rejects(resolveTaskNavigation({ get: async (url) => ({ data: data[url] }) }, '/main-contracts/7/subcontracts/22'), /łańcuch/);
    }
});
