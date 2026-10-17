import { parseBudgetAmount } from './budgetForm.js';
import { parseAmendmentAmount } from './contractAmendmentForm.js';
import { validDate } from './mainContractForm.js';
import { buildInvoiceRequest, getInvoiceDocumentItems, readInvoice } from './subcontractorInvoiceForm.js';

const cents = (value) => parseAmendmentAmount(value)?.cents;
const sameId = (left, right) => String(left) === String(right);
const apiId = (value) => Number.isSafeInteger(Number(value)) ? Number(value) : String(value);

export function readMainInvoice(invoice, contractId, invoiceId) {
    if (!invoice?.id || !sameId(invoice.mainContractId, contractId)
        || (invoiceId != null && !sameId(invoice.id, invoiceId))
        || typeof invoice.invoiceNumber !== 'string' || !validDate(invoice.issueDate)
        || !/^[A-Za-z]{3}$/.test(invoice.currency ?? '') || !Array.isArray(invoice.vatRates)
        || ['netAmount', 'vatAmount', 'grossAmount'].some((key) => cents(invoice[key]) == null)) {
        throw new Error('Nie można odczytać faktury GW przypisanej do tej umowy.');
    }
    return invoice;
}

export function readMainInvoices(data, contractId) {
    if (!Array.isArray(data)) throw new Error('Nie można odczytać listy faktur GW.');
    return data.map((invoice) => readMainInvoice(invoice, contractId));
}

export function buildMainInvoiceRequest(contractId, invoiceId, form) {
    const { data } = buildInvoiceRequest(contractId, null, form);
    delete data.subcontractId;
    if (invoiceId) return { method: 'put', url: `/main-contractor-invoices/${invoiceId}`, data };
    data.mainContractId = apiId(contractId);
    return { method: 'post', url: '/main-contractor-invoices', data };
}

export function readSettlementWorkspace(main, assignments, invoices, subcontracts, contractId, mainInvoiceId) {
    readMainInvoice(main, contractId, mainInvoiceId);
    if (![assignments, invoices, subcontracts].every(Array.isArray)) {
        throw new Error('Nie można odczytać danych rozliczenia.');
    }
    for (const sub of subcontracts) {
        if (!sub?.id || !sameId(sub.mainContractId, contractId)) throw new Error('Nieprawidłowa lista umów podwykonawczych.');
    }
    const byId = new Map();
    for (const invoice of invoices) {
        readInvoice(invoice, invoice.subcontractId);
        if (!sameId(invoice.mainContractId, contractId) || byId.has(String(invoice.id))
            || cents(invoice.assignedToMainInvoicesAmount) == null || cents(invoice.remainingToAssignToMainInvoices) == null) {
            throw new Error('Lista faktur podwykonawców nie odpowiada wskazanej umowie.');
        }
        byId.set(String(invoice.id), invoice);
    }
    const used = new Set();
    const rows = assignments.map((assignment) => {
        const invoice = byId.get(String(assignment.subcontractorInvoiceId));
        if (!assignment.id || !sameId(assignment.mainContractorInvoiceId, mainInvoiceId)
            || !invoice || used.has(String(invoice.id)) || !parseBudgetAmount(assignment.settledAmount)
            || cents(assignment.settledAmount) <= 0n) {
            throw new Error('Dane przypisań są niekompletne. Odśwież rozliczenie.');
        }
        used.add(String(invoice.id));
        return { ...assignment, invoice };
    });
    return { main, rows, invoices, subcontracts };
}

export async function loadSettlementWorkspace(http, contractId, mainInvoiceId, signal) {
    const [main, assignments, invoices, subcontracts] = await Promise.all([
        http.get(`/main-contractor-invoices/${mainInvoiceId}`, { signal }),
        http.get(`/main-invoice-settlements/main-invoice/${mainInvoiceId}`, { signal }),
        http.get('/subcontractor-invoices', { signal, params: { mainContractId: contractId } }),
        http.get('/subcontracts', { signal, params: { mainContractId: contractId } }),
    ]);
    return readSettlementWorkspace(main.data, assignments.data, invoices.data, subcontracts.data, contractId, mainInvoiceId);
}

export function availableInvoices(workspace, query = '') {
    const assigned = new Set(workspace.rows.map((row) => String(row.invoice.id)));
    const needle = query.trim().toLocaleLowerCase('pl-PL');
    return workspace.invoices.filter((invoice) => !assigned.has(String(invoice.id))
        && invoice.currency.toUpperCase() === workspace.main.currency.toUpperCase()
        && cents(invoice.remainingToAssignToMainInvoices) > 0n
        && `${invoice.invoiceNumber} ${invoice.subcontractorName} ${invoice.subcontractNumber} ${invoice.subcontractorTaxId}`
            .toLocaleLowerCase('pl-PL').includes(needle));
}

export function assignmentError(amount, maximum) {
    const parsed = parseBudgetAmount(amount);
    if (!parsed || parsed.cents <= 0n || parsed.cents > 999999999999999n) {
        return 'Podaj dodatnią kwotę z najwyżej dwoma miejscami po przecinku.';
    }
    const limit = cents(maximum);
    if (limit == null) return 'Nie można ustalić dostępnej kwoty. Odśwież dane.';
    if (parsed.cents > limit) return 'Kwota przekracza wartość dostępną do przypisania.';
    return '';
}

export function assignmentMaximum(row) {
    const total = cents(row.invoice.remainingToAssignToMainInvoices) + cents(row.settledAmount);
    return centsToAmount(total);
}

export function centsToAmount(value) {
    const sign = value < 0n ? '-' : '';
    const absolute = value < 0n ? -value : value;
    return `${sign}${absolute / 100n}.${String(absolute % 100n).padStart(2, '0')}`;
}

export function buildAssignmentRequest(mainInvoiceId, invoiceId, amount, maximum, assignmentId = null) {
    const error = assignmentError(amount, maximum);
    if (error) throw new Error(error);
    const data = { settledAmount: parseBudgetAmount(amount).amount };
    if (assignmentId) return { method: 'put', url: `/main-invoice-settlements/${assignmentId}`, data };
    return { method: 'post', url: '/main-invoice-settlements', data: { ...data,
        mainContractorInvoiceId: apiId(mainInvoiceId), subcontractorInvoiceId: apiId(invoiceId) } };
}

export function buildInvoiceAndAssignmentRequest(mainInvoiceId, subcontractId, form, amount) {
    const invoice = buildInvoiceRequest(subcontractId, null, form).data;
    const error = assignmentError(amount, invoice.grossAmount);
    if (error) throw new Error(error);
    return { method: 'post', url: '/main-invoice-settlements/with-new-invoice', data: {
        mainContractorInvoiceId: apiId(mainInvoiceId), invoice, settledAmount: parseBudgetAmount(amount).amount,
    } };
}

export function settlementSummary(workspace) {
    const currenciesMatch = workspace.rows.every(({ invoice }) => invoice.currency.toUpperCase() === workspace.main.currency.toUpperCase());
    const assigned = currenciesMatch ? workspace.rows.reduce((total, row) => total + cents(row.settledAmount), 0n) : null;
    return { assigned, exceedsMainInvoice: assigned != null && assigned > cents(workspace.main.grossAmount),
        unpaid: workspace.rows.filter(({ invoice }) => cents(invoice.remainingAmount) > 0n).length,
        documentGaps: workspace.rows.filter(({ invoice }) => getInvoiceDocumentItems(invoice)
            .some((item) => ['missing', 'unknown'].includes(item.tone))).length,
        inconsistencies: workspace.rows.filter(({ invoice }) => invoice.statementSettlementMismatch || invoice.amountExceeded
            || invoice.currency.toUpperCase() !== workspace.main.currency.toUpperCase()
            || cents(invoice.remainingToAssignToMainInvoices) < 0n).length };
}
