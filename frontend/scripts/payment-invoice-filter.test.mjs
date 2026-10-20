import test from 'node:test';
import assert from 'node:assert/strict';
import { invoiceRemainingToPay, invoicePaymentTotals } from '../src/utils/paymentForm.js';

const target = (remainingAmount, extra = {}) => ({ type: 'main', invoice: { remainingAmount, ...extra } });

test('Saldo nierozliczonej faktury zachowuje grosze i duże kwoty', () => {
    assert.equal(invoiceRemainingToPay(target('0.01')), 1n);
    assert.equal(invoiceRemainingToPay(target('9999999999999.99')), 999999999999999n);
    assert.equal(invoiceRemainingToPay(target('0.00')), 0n);
    assert.equal(invoiceRemainingToPay(target('-0.01')), -1n);
});

test('Brak lub nieprawidłowe saldo nie udaje faktury opłaconej ani nieopłaconej', () => {
    for (const value of [undefined, null, '', 'tekst', '0.001']) {
        assert.equal(invoiceRemainingToPay(target(value)), null);
    }
    assert.equal(invoiceRemainingToPay({}), null);
});

test('Ręczne potwierdzenie dokumentów GW i przypisanie faktur PW nie zastępują zapłaty', () => {
    assert.equal(invoiceRemainingToPay(target('100.00', {
        review: { status: 'COMPLETED' }, fullyAssignedToMainInvoices: true,
    })), 10000n);
    assert.equal(invoiceRemainingToPay(target('0.00', { review: { status: 'OPEN' } })), 0n);
});

test('Potrącenia zmniejszają pozostałą kwotę do rozliczenia faktury PW', () => {
    const invoice = { type: 'sub', grossAmount: '1000.00' };
    const remaining = invoicePaymentTotals(invoice, [{ allocatedAmount: '950.00', deductedAmount: '50.00' }]).remaining;
    assert.equal(remaining, 0n);
    assert.equal(invoiceRemainingToPay({ ...invoice, invoice: { remainingAmount: '0.00' } }), 0n);
    assert.equal(invoicePaymentTotals(invoice, [{ allocatedAmount: '949.99', deductedAmount: '50.00' }]).remaining, 1n);
});
