import test from 'node:test';
import assert from 'node:assert/strict';
import { allocationError, buildAllocationRequest, buildPaymentRequest, contextFromSearch, emptyPayment, invoicePaymentTotals,
    invoicePaymentsPath, paymentAvailable, readAllocations, readPayment, readPayments, readTarget, targetQuery,
    validatePaymentAllocations, validatePaymentForm } from '../src/utils/paymentForm.js';

const form = { ...emptyPayment(), paymentDate: '2026-10-17', amount: '100,00', currency: 'pln', bankReference: ' REF/1 ' };
const target = { type: 'sub', id: 40, contractId: 7, subcontractId: 21, currency: 'PLN', number: 'PW/1', grossAmount: '105' };
const payment = { id: 8, paymentDate: '2026-10-17', amount: '100', currency: 'PLN', allocatedAmount: '60', unallocatedAmount: '40', allocationCount: 1 };
const row = { id: 3, paymentId: 8, paymentDate: '2026-10-17', paymentCurrency: 'PLN', paymentAmount: '100', remainingPaymentAmount: '40',
    allocatedAmount: '60', deductedAmount: '5', subcontractorInvoiceId: 40, invoiceGrossAmount: '105', invoicePaidAmount: '60', invoiceDeductedAmount: '5', invoiceRemainingAmount: '40' };

test('Kwota przelewu jest dodatnia, dokładna do grosza i w zakresie bazy', () => {
    assert.deepEqual(validatePaymentForm(form), {});
    for (const amount of ['0', '-1', '', '0.001', '10000000000000']) assert.ok(validatePaymentForm({ ...form, amount }).amount);
    assert.ok(validatePaymentForm({ ...form, amount: '59.99' }, '60').amount);
    assert.equal(validatePaymentForm({ ...form, amount: '60' }, '60').amount, undefined);
});
test('Daty, waluty i długości pól odpowiadają DTO', () => {
    assert.ok(validatePaymentForm({ ...form, paymentDate: '2026-02-29' }).paymentDate);
    assert.ok(validatePaymentForm({ ...form, currency: 'PL1' }).currency);
    assert.ok(validatePaymentForm({ ...form, bankReference: 'x'.repeat(256) }).bankReference);
    assert.ok(validatePaymentForm({ ...form, description: 'x'.repeat(1001) }).description);
});
test('Przelew i wiele faktur zapisują się jednym żądaniem, a potrącenia nie zużywają przelewu', () => {
    const rows = [{ target, allocatedAmount: '60', deductedAmount: '5' }, { target: { ...target, id: 41 }, allocatedAmount: '40', deductedAmount: '10' }];
    const request = buildPaymentRequest(form, rows);
    assert.equal(request.url, '/payments/with-allocations');
    assert.equal(request.data.payment.amount, '100.00');
    assert.equal(request.data.payment.bankReference, 'REF/1');
    assert.deepEqual(request.data.allocations.map((value) => value.allocatedAmount), ['60.00', '40.00']);
    assert.equal(request.data.allocations[0].deductedAmount, '5.00');
    assert.equal(request.data.allocations[0].subcontractorInvoiceId, 40);
});
test('Suma przypisań nie przekracza przelewu; ta sama faktura nie występuje dwa razy', () => {
    const rows = [{ target, allocatedAmount: '60', deductedAmount: '0' }, { target: { ...target, id: 41 }, allocatedAmount: '40.01', deductedAmount: '0' }];
    assert.ok(validatePaymentAllocations(form, rows).allocations);
    assert.throws(() => buildPaymentRequest(form, rows));
    assert.ok(validatePaymentAllocations(form, [rows[0], rows[0]]).allocations);
    assert.ok(validatePaymentAllocations(form, [{ ...rows[0], target: null }]).allocations);
});
test('Faktury różnych walut nie mogą być przypisane do jednego przelewu', () => {
    assert.ok(validatePaymentAllocations(form, [{ target: { ...target, currency: 'EUR' }, allocatedAmount: '10', deductedAmount: '0' }]).allocations);
});
test('Potrącenie nie jest samodzielnym przelewem; dla GW musi wynosić zero', () => {
    assert.ok(allocationError('0', '5', '100', 'sub'));
    assert.ok(allocationError('10', '-1', '100', 'sub'));
    assert.ok(allocationError('10', '0.001', '100', 'sub'));
    assert.ok(allocationError('10', '5', '100', 'main'));
    assert.equal(allocationError('10', '0', '100', 'main'), '');
});
test('Przekroczenie wartości faktury jest widoczne w saldzie, bez ukrytego ograniczenia kwoty', () => {
    const rows = [{ ...row, allocatedAmount: '100', deductedAmount: '10' }];
    assert.deepEqual(invoicePaymentTotals(target, rows), { paid: 10000n, deducted: 1000n, remaining: -500n });
    assert.equal(allocationError('100', '10', '100', 'sub'), '');
});
test('Edycja przypisania oddaje do limitu jego poprzednią kwotę', () => {
    const maximum = paymentAvailable(payment, row);
    assert.equal(maximum, '100.00');
    assert.equal(allocationError('100', '0', maximum, 'sub'), '');
    assert.ok(allocationError('100.01', '0', maximum, 'sub'));
    const request = buildAllocationRequest(8, target, '100', '0', maximum, 3);
    assert.deepEqual(request, { method: 'put', url: '/payment-allocations/3', data: { allocatedAmount: '100.00', deductedAmount: '0.00' } });
});
test('POST przypisuje jeden przelew do właściwego typu faktury', () => {
    const request = buildAllocationRequest(8, { ...target, type: 'main' }, '40', '0', '40');
    assert.equal(request.data.mainContractorInvoiceId, 40);
    assert.equal(request.data.subcontractorInvoiceId, undefined);
    assert.equal(request.data.paymentId, 8);
});
test('Przelew bez przypisań jest dozwolony; edycja nie nadpisuje listy faktur', () => {
    assert.deepEqual(buildPaymentRequest(form).data.allocations, []);
    const request = buildPaymentRequest(form, [], 8);
    assert.equal(request.url, '/payments/8'); assert.equal(request.method, 'put');
    assert.equal(request.data.allocations, undefined);
});
test('Niepoprawne odpowiedzi i obce rekordy nie udają pustego rozliczenia', () => {
    assert.equal(readPayment(payment, 8).id, 8);
    assert.throws(() => readPayment(payment, 9));
    assert.throws(() => readPayments({ content: [] }));
    assert.throws(() => readAllocations([row], { paymentId: 9 }));
    assert.throws(() => readAllocations([row], { target: { ...target, id: 41 } }));
    assert.throws(() => readAllocations([row], { currency: 'EUR' }));
    assert.throws(() => readAllocations([row, row]));
    assert.throws(() => readAllocations([{ ...row, invoiceRemainingAmount: null }]));
    assert.deepEqual(readAllocations([], { target }), []);
});
test('Nawigacja zachowuje kontekst rozliczenia GW i nie akceptuje dowolnego adresu powrotu', () => {
    const context = contextFromSearch(new URLSearchParams(targetQuery({ ...target, returnMainInvoiceId: 10 })));
    assert.equal(context.returnMainInvoiceId, '10');
    assert.equal(invoicePaymentsPath(context), '/main-contracts/7/subcontracts/21/invoices/40/payments?returnMainInvoiceId=10');
    assert.equal(contextFromSearch(new URLSearchParams()), null);
    assert.throws(() => contextFromSearch(new URLSearchParams('type=sub&contractId=7&invoiceId=40')));
    assert.throws(() => contextFromSearch(new URLSearchParams('type=main&contractId=https://example.com&invoiceId=10')));
});
test('Odczyt kontekstu faktury weryfikuje umowę i identyfikator', () => {
    const main = { id: 10, mainContractId: 7, invoiceNumber: 'GW/1', issueDate: '2026-10-17', vatRates: ['23'], currency: 'PLN', netAmount: '100', vatAmount: '23', grossAmount: '123' };
    assert.equal(readTarget(main, { type: 'main', contractId: 7, id: 10 }).number, 'GW/1');
    assert.throws(() => readTarget(main, { type: 'main', contractId: 8, id: 10 }));
});
