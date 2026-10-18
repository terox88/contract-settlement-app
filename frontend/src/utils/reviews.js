import { parseAmendmentAmount } from './contractAmendmentForm.js';
import { parseBudgetAmount } from './budgetForm.js';
const fingerprint = (value) => /^[0-9a-f]{64}$/.test(value ?? '');
export function readMainInvoiceReview(value, invoiceId) {
    if (!value || String(value.invoiceId) !== String(invoiceId) || !['OPEN', 'COMPLETED', 'REVIEW_REQUIRED'].includes(value.status)
        || !fingerprint(value.fingerprint) || typeof value.label !== 'string'
        || !Number.isSafeInteger(value.linkedInvoiceCount) || value.linkedInvoiceCount < 0
        || ![value.missingDocuments, value.warnings].every(list => Array.isArray(list) && list.every(v => typeof v === 'string'))) {
        throw new Error('Nie można odczytać statusu rozliczenia faktury.');
    }
    return value;
}
export function readMainInvoiceReviews(data, invoices) {
    if (!Array.isArray(data) || data.length !== invoices.length) throw new Error('Lista statusów rozliczenia jest niekompletna.');
    const byId = new Map();
    for (const item of data) {
        if (!invoices.some(i => String(i.id) === String(item.invoiceId)) || byId.has(String(item.invoiceId))) throw new Error('Nieprawidłowy status faktury GW.');
        byId.set(String(item.invoiceId), readMainInvoiceReview(item, item.invoiceId));
    }
    return byId;
}
export function completeInvoiceRequest(review, { checked, waived, reason }) {
    readMainInvoiceReview(review, review.invoiceId);
    if (!checked) throw new Error('Potwierdź sprawdzenie rozliczenia.');
    if (!waived && review.missingDocuments.length) throw new Error('Uzupełnij dokumenty lub podaj uzasadnienie, dlaczego nie są wymagane.');
    if (waived && (!(reason ?? '').trim() || reason.trim().length > 1000)) throw new Error('Podaj uzasadnienie do 1000 znaków.');
    return { fingerprint: review.fingerprint, checked: true, documentsNotRequired: Boolean(waived), reason: waived ? reason.trim() : null };
}
export function readDeductionReview(value) {
    if (!value || !['NOT_STARTED', 'MATCHED', 'MISMATCH', 'CONFIRMED'].includes(value.status) || !fingerprint(value.fingerprint)
        || typeof value.label !== 'string' || ['expectedAmount','actualAmount','difference','securityAmount','otherAmount'].some(key => !parseAmendmentAmount(value[key]))) {
        throw new Error('Nie można odczytać kontroli potrąceń.');
    }
    return value;
}
export function projectedDeduction(review, newAmount, oldAmount = '0') {
    if (!review) return null;
    readDeductionReview(review);
    const next = parseBudgetAmount(newAmount), old = parseBudgetAmount(oldAmount);
    if (!next || !old) return null;
    const actual = parseBudgetAmount(review.actualAmount)?.cents, expected = parseBudgetAmount(review.expectedAmount)?.cents;
    if (actual == null || expected == null) return null;
    return { actual: actual - old.cents + next.cents, expected, difference: actual - old.cents + next.cents - expected };
}
