import { parseBudgetAmount } from './budgetForm.js';
import { validDate } from './mainContractForm.js';

export const KSEF_MAX_BYTES = 5 * 1024 * 1024;
const prefix = '/invoice-imports/ksef';

export function ksefFileError(file) {
    if (!file || !file.size) return 'Wybierz niepusty plik XML KSeF.';
    if (file.size > KSEF_MAX_BYTES) return 'Plik XML KSeF może mieć maksymalnie 5 MB.';
    return '';
}

export function buildKsefPreviewRequest(file) {
    const error = ksefFileError(file);
    if (error) throw new Error(error);
    const data = new FormData(); data.append('file', file);
    return { url: `${prefix}/preview`, data, config: { headers: { 'Content-Type': undefined } } };
}

export function readKsefPreview(data) {
    const amounts = ['netAmount', 'vatAmount', 'grossAmount'].map((name) => parseBudgetAmount(data?.[name]));
    if (!data || !['FA(2)', 'FA(3)'].includes(data.schema) || !['VAT', 'ZAL', 'UPR'].includes(data.invoiceKind)
        || typeof data.invoiceNumber !== 'string' || !data.invoiceNumber.trim() || data.invoiceNumber.length > 255
        || !validDate(data.issueDate) || ['saleDate', 'dueDate'].some((name) => data[name] != null && !validDate(data[name]))
        || data.currency !== 'PLN' || amounts.some((value) => !value || value.cents > 999999999999999n)
        || amounts[2].cents <= 0n || amounts[0].cents + amounts[1].cents !== amounts[2].cents
        || !Array.isArray(data.vatRates) || data.vatRates.some((value) => typeof value !== 'string' || !value.trim() || value.length > 20)
        || !Array.isArray(data.warnings) || data.warnings.some((value) => typeof value !== 'string')
        || ['seller', 'buyer'].some((name) => !data[name] || (data[name].name != null && typeof data[name].name !== 'string')
            || (data[name].taxId != null && !/^[0-9]{10}$/.test(data[name].taxId)))) {
        throw new Error('Nie można odczytać podglądu faktury z XML. Formularz nie został zmieniony.');
    }
    return data;
}

export function ksefSellerWarning(preview, expectedTaxId) {
    const normalize = (value) => String(value ?? '').replace(/^PL/i, '').replace(/[\s-]/g, '');
    const actual = normalize(preview?.seller?.taxId), expected = normalize(expectedTaxId);
    if (!actual || !expected) return 'Nie można porównać NIP sprzedawcy z wykonawcą umowy. Sprawdź podmiot przed zapisem.';
    return actual === expected ? '' : `NIP sprzedawcy (${actual}) różni się od NIP wykonawcy w umowie (${expected}). Sprawdź, czy wybrano właściwą fakturę.`;
}

export function applyKsefPreview(current, preview) {
    const data = readKsefPreview(preview);
    return { ...current, invoiceNumber: data.invoiceNumber, issueDate: data.issueDate,
        saleDate: data.saleDate ?? '', dueDate: data.dueDate ?? '', netAmount: data.netAmount,
        vatAmount: data.vatAmount, currency: data.currency, vatRates: data.vatRates.join('; ') };
}

/** Ten sam walidowany JSON i oryginalny XML są zapisywane w jednej transakcji. */
export function withKsefXml(request, file) {
    if (!file) return request;
    const error = ksefFileError(file);
    if (error) throw new Error(error);
    let route;
    if (request.method === 'post' && request.url === '/main-invoice-settlements/with-new-invoice') route = '/settlements';
    else {
        const match = /^\/(main-contractor-invoices|subcontractor-invoices)(\/\d+)?$/.exec(request.url);
        if (!match || (request.method === 'post' ? Boolean(match[2]) : request.method !== 'put' || !match[2])) {
            throw new Error('Nieprawidłowy adres zapisu faktury z XML.');
        }
        route = `/${match[1] === 'main-contractor-invoices' ? 'main-invoices' : 'sub-invoices'}${match[2] ?? ''}`;
    }
    const data = new FormData();
    data.append('invoice', new Blob([JSON.stringify(request.data)], { type: 'application/json' }));
    data.append('file', file);
    return { method: request.method, url: `${prefix}${route}`, data, config: { headers: { 'Content-Type': undefined } } };
}
