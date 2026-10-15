import { parseBudgetAmount, formatBudgetCents } from './budgetForm.js';
import { parseAmendmentAmount } from './contractAmendmentForm.js';
import { validDate } from './mainContractForm.js';

const maxCents = 999999999999999n;

export function emptyInvoice() {
    const now = new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    return { invoiceNumber: '', issueDate: today, saleDate: '', dueDate: '',
        netAmount: '', vatAmount: '', currency: 'PLN', vatRates: '', description: '' };
}

export function invoiceToForm(invoice) {
    return { ...Object.fromEntries(Object.keys(emptyInvoice()).map((name) => [name, String(invoice[name] ?? '')])),
        vatRates: Array.isArray(invoice.vatRates) ? invoice.vatRates.join('; ') : '' };
}

export function normalizeVatRates(value) {
    return [...new Set(String(value ?? '').split(';').map((rate) => rate.trim().replace(/\s+/g, ' ').toUpperCase()))];
}

export function calculatedGross(invoice) {
    const net = parseBudgetAmount(invoice.netAmount);
    const vat = parseBudgetAmount(invoice.vatAmount);
    if (!net || !vat) return null;
    const cents = net.cents + vat.cents;
    return { cents, amount: `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}` };
}

export function validateInvoiceForm(invoice) {
    const errors = {};
    if (!invoice.invoiceNumber.trim()) errors.invoiceNumber = 'Podaj numer faktury.';
    else if (invoice.invoiceNumber.trim().length > 255) errors.invoiceNumber = 'Numer może mieć maksymalnie 255 znaków.';
    if (!validDate(invoice.issueDate)) errors.issueDate = 'Podaj prawidłową datę wystawienia.';
    for (const name of ['saleDate', 'dueDate']) {
        if (invoice[name] && !validDate(invoice[name])) errors[name] = 'Podaj prawidłową datę.';
    }
    for (const name of ['netAmount', 'vatAmount']) {
        const amount = parseBudgetAmount(invoice[name]);
        if (!amount) errors[name] = 'Podaj kwotę nieujemną, z maksymalnie dwoma miejscami po przecinku.';
        else if (amount.cents > maxCents) errors[name] = 'Kwota przekracza dopuszczalny zakres.';
    }
    const gross = calculatedGross(invoice);
    if (gross && (gross.cents <= 0n || gross.cents > maxCents)) {
        errors.netAmount = 'Suma netto i VAT musi być dodatnia i mieścić się w dopuszczalnym zakresie kwoty.';
    }
    if (!/^[A-Za-z]{3}$/.test(invoice.currency.trim())) errors.currency = 'Podaj trzyliterowy kod waluty, np. PLN.';
    const rates = normalizeVatRates(invoice.vatRates);
    if (rates.some((rate) => !rate || rate.length > 20)) {
        errors.vatRates = 'Podaj stawki rozdzielone średnikami, np. 23; 8; ZW. Każda może mieć maksymalnie 20 znaków.';
    }
    if (invoice.description.trim().length > 1000) errors.description = 'Opis może mieć maksymalnie 1000 znaków.';
    return errors;
}

export function buildInvoiceRequest(subcontractId, invoiceId, invoice) {
    if (Object.keys(validateInvoiceForm(invoice)).length) throw new Error('Popraw dane faktury przed zapisem.');
    const data = { invoiceNumber: invoice.invoiceNumber.trim(), issueDate: invoice.issueDate,
        saleDate: invoice.saleDate || null, dueDate: invoice.dueDate || null,
        netAmount: parseBudgetAmount(invoice.netAmount).amount,
        vatAmount: parseBudgetAmount(invoice.vatAmount).amount,
        grossAmount: calculatedGross(invoice).amount,
        currency: invoice.currency.trim().toUpperCase(), vatRates: normalizeVatRates(invoice.vatRates),
        description: invoice.description.trim() || null };
    if (invoiceId != null) return { method: 'put', url: `/subcontractor-invoices/${invoiceId}`, data };
    const id = Number(subcontractId);
    data.subcontractId = Number.isSafeInteger(id) ? id : String(subcontractId);
    return { method: 'post', url: '/subcontractor-invoices', data };
}

export function readInvoice(invoice, subcontractId) {
    if (!invoice?.id || String(invoice.subcontractId) !== String(subcontractId)
        || !validDate(invoice.issueDate) || typeof invoice.invoiceNumber !== 'string'
        || typeof invoice.currency !== 'string' || !/^[A-Za-z]{3}$/.test(invoice.currency)
        || !Array.isArray(invoice.vatRates)
        || ['netAmount', 'vatAmount', 'grossAmount', 'paidAmount', 'deductedAmount', 'remainingAmount']
            .some((name) => !parseAmendmentAmount(invoice[name]))) {
        throw new Error('Nie można odczytać faktury tej umowy. Spróbuj ponownie.');
    }
    return invoice;
}

export function readInvoices(data, subcontractId) {
    if (!Array.isArray(data)) throw new Error('Nie można odczytać listy faktur. Spróbuj ponownie.');
    return data.map((invoice) => readInvoice(invoice, subcontractId));
}

export function formatInvoiceAmount(value, currency = 'PLN') {
    const cents = parseAmendmentAmount(value)?.cents;
    if (cents == null) return '—';
    const amount = formatBudgetCents(cents);
    return currency === 'PLN' ? amount : amount.replace(/zł$/, currency);
}

export function invoiceTotals(invoices) {
    const currencies = new Map();
    for (const invoice of invoices) {
        const currency = invoice.currency.toUpperCase();
        if (!currencies.has(currency)) currencies.set(currency, {
            currency, grossAmount: 0n, paidAmount: 0n, deductedAmount: 0n, remainingAmount: 0n,
        });
        const row = currencies.get(currency);
        for (const name of ['grossAmount', 'paidAmount', 'deductedAmount', 'remainingAmount']) {
            row[name] += parseAmendmentAmount(invoice[name]).cents;
        }
    }
    return [...currencies.values()];
}

export function formatInvoiceCents(cents, currency) {
    return formatBudgetCents(cents).replace(/zł$/, currency === 'PLN' ? 'zł' : currency);
}

export function getInvoiceDocumentItems(invoice) {
    const docs = invoice?.documents;
    const keys = ['invoiceFileCount', 'statementFileCount', 'paymentConfirmationFileCount',
        'paymentCount', 'paymentsWithConfirmationCount', 'unassignedPaymentConfirmationFileCount'];
    const titles = ['Faktura · PDF / XML', 'Oświadczenie podwykonawcy', 'Potwierdzenia przelewów'];
    if (!docs || keys.some((name) => !Number.isSafeInteger(docs[name]) || docs[name] < 0)
        || docs.paymentsWithConfirmationCount > docs.paymentCount) {
        return titles.map((title) => ({ title, tone: 'unknown', label: 'Nie ustalono', detail: 'Brak informacji o plikach.' }));
    }
    const invoiceItem = { title: titles[0], tone: docs.invoiceFileCount ? 'good' : 'missing',
        label: docs.invoiceFileCount ? `Dołączono: ${docs.invoiceFileCount}` : 'Brak pliku',
        detail: 'PDF, XML lub inny zapisany plik faktury.' };
    const statementItem = { title: titles[1], tone: docs.statementFileCount ? 'good' : 'missing',
        label: docs.statementFileCount ? `Dołączono: ${docs.statementFileCount}` : 'Brak pliku',
        detail: invoice.hasStatement ? 'Oświadczenie zarejestrowane.' : docs.statementFileCount
            ? 'Plik dołączony; oświadczenie nie zostało zarejestrowane.' : 'Oświadczenie nie zostało zarejestrowane.' };
    const paymentItem = { title: titles[2], tone: docs.paymentCount ? docs.paymentsWithConfirmationCount === docs.paymentCount
        ? 'good' : 'missing' : 'neutral',
        label: docs.paymentCount ? `${docs.paymentsWithConfirmationCount} z ${docs.paymentCount} przelewów` : 'Brak przelewów',
        detail: docs.paymentConfirmationFileCount ? `Pliki potwierdzeń: ${docs.paymentConfirmationFileCount}.` : 'Brak plików potwierdzeń.' };
    if (docs.unassignedPaymentConfirmationFileCount) {
        paymentItem.detail += ` Bez powiązania z przelewem: ${docs.unassignedPaymentConfirmationFileCount}.`;
        if (!docs.paymentCount) paymentItem.tone = 'missing';
    }
    return [invoiceItem, statementItem, paymentItem];
}

export function filterInvoices(invoices, query, settlement, onlyDocumentGaps) {
    const needle = query.trim().toLocaleLowerCase('pl-PL');
    return invoices.filter((invoice) => (!needle || invoice.invoiceNumber.toLocaleLowerCase('pl-PL').includes(needle))
        && (settlement === 'all' || (settlement === 'settled' ? invoice.settled === true : invoice.settled === false))
        && (!onlyDocumentGaps || getInvoiceDocumentItems(invoice).some((item) => ['missing', 'unknown'].includes(item.tone))));
}
