import test from 'node:test';
import assert from 'node:assert/strict';
import axios from 'axios';
import { applyKsefPreview, buildKsefPreviewRequest, ksefFileError, ksefSellerWarning, readKsefPreview, withKsefXml, KSEF_MAX_BYTES } from '../src/utils/ksefImport.js';
import { emptyInvoice, validateInvoiceForm } from '../src/utils/subcontractorInvoiceForm.js';
import { buildInvoiceRequest } from '../src/utils/subcontractorInvoiceForm.js';
import { buildInvoiceAndAssignmentRequest, buildMainInvoiceRequest } from '../src/utils/mainInvoiceSettlement.js';

const file = new File(['<Faktura />'], 'faktura.xml', { type: 'application/xml' });
const preview = { schema: 'FA(3)', invoiceKind: 'VAT', invoiceNumber: 'FV/10/2026', issueDate: '2026-10-18', saleDate: null, dueDate: '2026-11-18',
    netAmount: '1000.00', vatAmount: '230.00', grossAmount: '1230.00', currency: 'PLN', vatRates: ['23'],
    seller: { name: 'Sprzedawca', taxId: '1234563218' }, buyer: { name: 'Nabywca', taxId: '1111111111' }, warnings: [] };
const form = () => applyKsefPreview(emptyInvoice(), preview);

test('Rozmiar XML jest ograniczony przed przesłaniem, bez polegania na MIME', () => {
    assert.ok(ksefFileError(null)); assert.ok(ksefFileError(new File([], 'empty.xml')));
    assert.ok(ksefFileError({ size: KSEF_MAX_BYTES + 1 })); assert.equal(ksefFileError({ size: KSEF_MAX_BYTES }), '');
    assert.equal(ksefFileError(new File(['<Faktura/>'], 'download', { type: 'application/octet-stream' })), '');
});
test('Podgląd przesyła tylko plik, bez danych umowy i bez żądania zapisu', () => {
    const request = buildKsefPreviewRequest(file);
    assert.equal(request.url, '/invoice-imports/ksef/preview'); assert.deepEqual([...request.data.keys()], ['file']);
    assert.equal(request.data.get('file').name, 'faktura.xml'); assert.equal(request.config.headers['Content-Type'], undefined);
});
test('Odczyt podglądu nie zmienia formularza', () => {
    const current = emptyInvoice(), copy = { ...current }; assert.equal(readKsefPreview(preview), preview); assert.deepEqual(current, copy);
});
test('Zastosowanie zastępuje daty i stawki, zachowując ręczny opis', () => {
    const before = { ...emptyInvoice(), saleDate: '2026-10-01', vatRates: '8', description: 'Protokół odbioru 7' };
    const result = applyKsefPreview(before, preview);
    assert.equal(result.saleDate, ''); assert.equal(result.vatRates, '23'); assert.equal(result.description, before.description);
    assert.equal(result.netAmount, '1000.00'); assert.equal(before.vatRates, '8'); assert.deepEqual(validateInvoiceForm(result), {});
});
test('Brak stawek usuwa domyślną stawkę z umowy i wymaga jej wpisania', () => {
    const result = applyKsefPreview({ ...emptyInvoice(), vatRates: '23' }, { ...preview, vatRates: [] });
    assert.equal(result.vatRates, ''); assert.ok(validateInvoiceForm(result).vatRates);
});
test('Porównanie NIP normalizuje polski prefiks, odstępy i myślniki', () => {
    assert.equal(ksefSellerWarning(preview, 'PL 123-456-32-18'), '');
    assert.match(ksefSellerWarning(preview, '1111111111'), /różni się/);
    assert.match(ksefSellerWarning(preview, null), /Nie można porównać/);
    assert.match(ksefSellerWarning({ ...preview, seller: { name: 'Firma' } }, '1234563218'), /Nie można porównać/);
});
test('Błędny podgląd nie może nadpisać pól formularza', () => {
    for (const change of [{ grossAmount: '1230.01' }, { netAmount: '-1' }, { currency: 'EUR' }, { issueDate: '2026-02-30' },
        { saleDate: 'wczoraj' }, { schema: 'FA(1)' }, { invoiceKind: 'KOR' }, { warnings: [null] }, { seller: null },
        { vatRates: [null] }, { invoiceNumber: '' }, { grossAmount: '10000000000000.00' }]) {
        assert.throws(() => applyKsefPreview(emptyInvoice(), { ...preview, ...change }));
    }
});
test('Kwoty zachowują dokładność groszy przy maksymalnym zakresie', () => {
    const data = { ...preview, netAmount: '9999999999999.99', vatAmount: '0.00', grossAmount: '9999999999999.99' };
    assert.equal(applyKsefPreview(emptyInvoice(), data).netAmount, data.netAmount);
});
test('Brak wybranego XML zachowuje ręczny zapis JSON', () => {
    const request = buildMainInvoiceRequest(7, null, form()); assert.equal(withKsefXml(request, null), request);
});
test('Tworzenie i edycja faktur obu typów zachowują JSON i oryginalny XML', async () => {
    for (const [request, path] of [
        [buildMainInvoiceRequest(7, null, form()), '/main-invoices'],
        [buildMainInvoiceRequest(7, 40, form()), '/main-invoices/40'],
        [buildInvoiceRequest(21, null, form()), '/sub-invoices'],
        [buildInvoiceRequest(21, 41, form()), '/sub-invoices/41'],
    ]) {
        const result = withKsefXml(request, file); assert.equal(result.method, request.method);
        assert.equal(result.url, `/invoice-imports/ksef${path}`);
        assert.equal(result.data.get('invoice').type, 'application/json');
        assert.deepEqual(JSON.parse(await result.data.get('invoice').text()), request.data);
        assert.equal(result.data.get('file').name, 'faktura.xml'); assert.equal(await result.data.get('file').text(), await file.text());
    }
});
test('Import z przypisaniem działa dla faktury GW oraz bezpośredniego podwykonawcy', async () => {
    for (const parent of ['main', 'sub']) {
        const request = buildInvoiceAndAssignmentRequest(40, 21, form(), '1230', parent);
        const result = withKsefXml(request, file), data = JSON.parse(await result.data.get('invoice').text());
        assert.equal(result.url, '/invoice-imports/ksef/settlements');
        assert.equal(data[parent === 'main' ? 'mainContractorInvoiceId' : 'parentSubcontractorInvoiceId'], 40);
        assert.equal(data.invoice.subcontractId, 21); assert.equal(data.settledAmount, '1230.00');
    }
});
test('Niepoprawny endpoint albo metoda nie może wysłać XML', () => {
    for (const request of [{ method: 'delete', url: '/subcontractor-invoices/1' }, { method: 'post', url: '/payments' },
        { method: 'post', url: '/subcontractor-invoices/1' }, { method: 'put', url: '/main-contractor-invoices' }]) assert.throws(() => withKsefXml(request, file));
});
test('Axios nie zmienia multipart importu na JSON, mimo domyślnych nagłówków aplikacji', async () => {
    const client = axios.create({ headers: { 'Content-Type': 'application/json' }, adapter: async (config) => {
        assert.ok(config.data instanceof FormData); assert.equal(config.data.get('invoice').type, 'application/json');
        assert.notEqual(config.headers.getContentType(), 'application/json');
        return { data: {}, status: 201, statusText: 'Created', headers: {}, config };
    } });
    const request = withKsefXml(buildMainInvoiceRequest(7, null, form()), file);
    await client[request.method](request.url, request.data, request.config);
});
