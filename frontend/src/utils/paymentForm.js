import { parseBudgetAmount } from './budgetForm.js';
import { parseAmendmentAmount } from './contractAmendmentForm.js';
import { validDate } from './mainContractForm.js';
import { emptyInvoice, readInvoice } from './subcontractorInvoiceForm.js';
import { centsToAmount, readMainInvoice } from './mainInvoiceSettlement.js';

export const moneyCents = (value) => parseAmendmentAmount(value)?.cents;
const apiId = (value) => Number.isSafeInteger(Number(value)) ? Number(value) : String(value);
const sameId = (a, b) => String(a) === String(b);
const validId = (value) => /^[1-9]\d*$/.test(String(value ?? ''));
const max = 999999999999999n;

export function emptyPayment() {
    return { paymentDate: emptyInvoice().issueDate, amount: '', currency: 'PLN', bankReference: '', description: '' };
}
export function paymentToForm(payment) {
    return Object.fromEntries(Object.keys(emptyPayment()).map((key) => [key, String(payment[key] ?? '')]));
}
export function validatePaymentForm(form, allocatedAmount = '0') {
    const errors = {};
    if (!validDate(form.paymentDate)) errors.paymentDate = 'Podaj prawidłową datę przelewu.';
    const parsed = parseBudgetAmount(form.amount);
    if (!parsed || parsed.cents <= 0n || parsed.cents > max) errors.amount = 'Podaj dodatnią kwotę, z najwyżej dwoma miejscami po przecinku.';
    else if (moneyCents(allocatedAmount) == null || parsed.cents < moneyCents(allocatedAmount)) errors.amount = 'Kwota przelewu nie może być mniejsza od sumy istniejących przypisań.';
    if (!/^[A-Za-z]{3}$/.test(form.currency.trim())) errors.currency = 'Podaj trzyliterowy kod waluty.';
    if (form.bankReference.trim().length > 255) errors.bankReference = 'Numer może mieć najwyżej 255 znaków.';
    if (form.description.trim().length > 1000) errors.description = 'Opis może mieć najwyżej 1000 znaków.';
    return errors;
}
export function paymentData(form) {
    if (Object.keys(validatePaymentForm(form)).length) throw new Error('Popraw dane przelewu.');
    return { paymentDate: form.paymentDate, amount: parseBudgetAmount(form.amount).amount,
        currency: form.currency.trim().toUpperCase(), bankReference: form.bankReference.trim() || null, description: form.description.trim() || null };
}
export function readPayment(payment, expectedId) {
    if (!validId(payment?.id) || (expectedId != null && !sameId(payment.id, expectedId))
        || !validDate(payment.paymentDate) || !/^[A-Za-z]{3}$/.test(payment.currency ?? '')
        || !parseBudgetAmount(payment.amount) || moneyCents(payment.amount) <= 0n
        || moneyCents(payment.allocatedAmount) == null || moneyCents(payment.unallocatedAmount) == null
        || !Number.isSafeInteger(payment.allocationCount) || payment.allocationCount < 0) {
        throw new Error('Nie można odczytać danych przelewu.');
    }
    return payment;
}
export function readPayments(data) {
    if (!Array.isArray(data)) throw new Error('Nie można odczytać listy przelewów.');
    return data.map((payment) => readPayment(payment));
}
export function readAllocations(data, { paymentId, target, currency } = {}) {
    if (!Array.isArray(data)) throw new Error('Nie można odczytać rozliczeń przelewu.');
    const ids = new Set();
    return data.map((row) => {
        const main = row.mainContractorInvoiceId != null;
        if (!validId(row.id) || ids.has(String(row.id)) || !validId(row.paymentId)
            || main === (row.subcontractorInvoiceId != null)
            || !validDate(row.paymentDate) || !/^[A-Za-z]{3}$/.test(row.paymentCurrency ?? '')
            || !parseBudgetAmount(row.allocatedAmount) || moneyCents(row.allocatedAmount) <= 0n
            || !parseBudgetAmount(row.deductedAmount)
            || (main && moneyCents(row.deductedAmount) !== 0n)
            || ((target?.currency ?? currency) && row.paymentCurrency.toUpperCase() !== (target?.currency ?? currency).toUpperCase())
            || ['paymentAmount', 'remainingPaymentAmount', 'invoiceGrossAmount', 'invoicePaidAmount', 'invoiceDeductedAmount', 'invoiceRemainingAmount']
                .some((key) => moneyCents(row[key]) == null)
            || (paymentId != null && !sameId(row.paymentId, paymentId))
            || (target && (main !== (target.type === 'main')
                || !sameId(main ? row.mainContractorInvoiceId : row.subcontractorInvoiceId, target.id)))) {
            throw new Error('Dane rozliczeń są niekompletne lub dotyczą innej faktury. Odśwież widok.');
        }
        ids.add(String(row.id));
        return row;
    });
}
export function targetKey(target) { return `${target.type}:${target.id}`; }
export function invoicePaymentsPath(target) {
    const path = target.type === 'main' ? `/main-contracts/${target.contractId}/invoices/${target.id}/payments`
        : `/main-contracts/${target.contractId}/subcontracts/${target.subcontractId}/invoices/${target.id}/payments`;
    return path + (target.type === 'sub' && validId(target.returnMainInvoiceId) ? `?returnMainInvoiceId=${target.returnMainInvoiceId}` : '');
}
export function targetQuery(target) {
    return new URLSearchParams({ type: target.type, contractId: String(target.contractId), invoiceId: String(target.id),
        ...(target.type === 'sub' ? { subcontractId: String(target.subcontractId) } : {}),
        ...(validId(target.returnMainInvoiceId) ? { returnMainInvoiceId: String(target.returnMainInvoiceId) } : {}) }).toString();
}
export function contextFromSearch(search) {
    if (!['type', 'contractId', 'invoiceId', 'subcontractId'].some((key) => search.has(key))) return null;
    const type = search.get('type');
    const contractId = search.get('contractId'), id = search.get('invoiceId'), subcontractId = search.get('subcontractId');
    if (!['main', 'sub'].includes(type) || !validId(contractId) || !validId(id) || (type === 'sub' && !validId(subcontractId))) {
        throw new Error('Nieprawidłowy kontekst faktury. Otwórz formularz ponownie z jej rozliczenia.');
    }
    return { type, contractId, id, subcontractId,
        returnMainInvoiceId: validId(search.get('returnMainInvoiceId')) ? search.get('returnMainInvoiceId') : null };
}
export function readTarget(invoice, context) {
    if (context.type === 'main') readMainInvoice(invoice, context.contractId, context.id);
    else {
        readInvoice(invoice, context.subcontractId);
        if (!sameId(invoice.id, context.id) || !sameId(invoice.mainContractId, context.contractId)) throw new Error('Faktura nie należy do tej umowy.');
    }
    return { ...context, number: invoice.invoiceNumber, name: context.type === 'main' ? invoice.mainContractorName : invoice.subcontractorName,
        currency: invoice.currency, grossAmount: invoice.grossAmount, invoice };
}
export async function loadTarget(http, context, signal) {
    const url = context.type === 'main' ? `/main-contractor-invoices/${context.id}` : `/subcontractor-invoices/${context.id}`;
    const response = await http.get(url, { signal });
    return readTarget(response.data, context);
}
export function allocationError(allocatedAmount, deductedAmount, maximum, type) {
    const paid = parseBudgetAmount(allocatedAmount), deducted = parseBudgetAmount(deductedAmount);
    if (!paid || paid.cents <= 0n || paid.cents > max) return 'Podaj dodatnią kwotę przelewu przypisaną do faktury, z dokładnością do grosza.';
    if (!deducted || deducted.cents > max) return 'Podaj nieujemną kwotę potrącenia, z dokładnością do grosza.';
    if (type === 'main' && deducted.cents !== 0n) return 'Potrącenia dotyczą wyłącznie faktur podwykonawców.';
    if (moneyCents(maximum) == null || paid.cents > moneyCents(maximum)) return 'Kwota przekracza dostępną wartość przelewu.';
    return '';
}
export function allocationData(target, allocatedAmount, deductedAmount) {
    return { ...(target.type === 'main' ? { mainContractorInvoiceId: apiId(target.id) } : { subcontractorInvoiceId: apiId(target.id) }),
        allocatedAmount: parseBudgetAmount(allocatedAmount).amount, deductedAmount: parseBudgetAmount(deductedAmount).amount };
}
export function buildAllocationRequest(paymentId, target, amount, deducted, maximum, allocationId = null) {
    const error = allocationError(amount, deducted, maximum, target.type);
    if (error) throw new Error(error);
    const data = allocationData(target, amount, deducted);
    if (allocationId) return { method: 'put', url: `/payment-allocations/${allocationId}`, data: {
        allocatedAmount: data.allocatedAmount, deductedAmount: data.deductedAmount } };
    return { method: 'post', url: '/payment-allocations', data: { ...data, paymentId: apiId(paymentId) } };
}
export function validatePaymentAllocations(form, rows) {
    const errors = {};
    if (rows.length > 100) return { allocations: 'Jednorazowo można zapisać najwyżej 100 pozycji.' };
    const ids = new Set(); let total = 0n;
    for (const row of rows) {
        if (!row.target) { errors.allocations = 'Wybierz fakturę dla każdej pozycji.'; continue; }
        const key = targetKey(row.target);
        if (ids.has(key)) errors.allocations = 'Ta sama faktura występuje więcej niż raz.';
        ids.add(key);
        if (row.target.currency.toUpperCase() !== form.currency.trim().toUpperCase()) errors.allocations = 'Waluta każdej faktury musi odpowiadać walucie przelewu.';
        const error = allocationError(row.allocatedAmount, row.deductedAmount, form.amount, row.target.type);
        if (error) errors.allocations = error;
        total += parseBudgetAmount(row.allocatedAmount)?.cents ?? 0n;
    }
    const amount = parseBudgetAmount(form.amount)?.cents;
    if (amount != null && total > amount) errors.allocations = 'Suma przypisań przekracza kwotę przelewu. Potrącenia nie pomniejszają dostępnej kwoty przelewu.';
    return errors;
}
export function buildPaymentRequest(form, rows = [], paymentId = null) {
    const errors = { ...validatePaymentForm(form), ...(!paymentId ? validatePaymentAllocations(form, rows) : {}) };
    if (Object.keys(errors).length) throw new Error(Object.values(errors)[0]);
    if (paymentId) return { method: 'put', url: `/payments/${paymentId}`, data: paymentData(form) };
    return { method: 'post', url: '/payments/with-allocations', data: { payment: paymentData(form),
        allocations: rows.map((row) => allocationData(row.target, row.allocatedAmount, row.deductedAmount)) } };
}
export function paymentAvailable(payment, row) {
    return centsToAmount(moneyCents(payment.unallocatedAmount) + (row ? moneyCents(row.allocatedAmount) : 0n));
}
export function invoicePaymentTotals(target, rows) {
    const paid = rows.reduce((sum, row) => sum + moneyCents(row.allocatedAmount), 0n);
    const deducted = rows.reduce((sum, row) => sum + moneyCents(row.deductedAmount), 0n);
    return { paid, deducted, remaining: moneyCents(target.grossAmount) - paid - deducted };
}
export function allocationInvoicePath(row) {
    if (!row.mainContractId) return null;
    return invoicePaymentsPath({ type: row.mainContractorInvoiceId != null ? 'main' : 'sub', contractId: row.mainContractId,
        subcontractId: row.subcontractId, id: row.mainContractorInvoiceId ?? row.subcontractorInvoiceId });
}
