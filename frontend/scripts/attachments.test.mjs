import test from 'node:test';
import assert from 'node:assert/strict';
import axios from 'axios';
import { attachmentEndpoint, attachmentOwner, attachmentDownloadPath, readAttachmentTypes, readAttachments,
    isPdf, pageSelectionError, validateAttachmentForm, buildAttachmentRequest, attachmentError, loadAttachmentBlob } from '../src/utils/attachments.js';
import { uniqueInvoicePayments, buildStatementRequest } from '../src/utils/invoiceAttachments.js';

const options = [{ value: 'SUBCONTRACTOR_INVOICE', label: 'Faktura podwykonawcy — PDF/XML' },
    { value: 'SUBCONTRACTOR_STATEMENT', label: 'Oświadczenie podwykonawcy' }];
const file = new File(['%PDF-1.7 test'], 'pakiet.pdf', { type: 'application/pdf' });
const form = (changes = {}) => ({ file, type: options[0].value, description: '', mode: 'all', pages: '', parts: ['', ''], ...changes });
const attachment = { id: 8, storedFileId: 12, type: 'SUBCONTRACTOR_INVOICE', originalFilename: 'faktura.pdf', contentType: 'application/pdf', size: 123 };

test('Załączniki mają właściwe endpointy dla wszystkich sześciu właścicieli', () => {
    for (const [owner, route, kind] of [['contract', 'contracts', 'CONTRACT'], ['amendment', 'contract-amendments', 'AMENDMENT'],
        ['mainInvoice', 'main-contractor-invoices', 'MAIN_INVOICE'], ['subInvoice', 'subcontractor-invoices', 'SUB_INVOICE'],
        ['payment', 'payments', 'PAYMENT'], ['statement', 'subcontractor-statements', 'STATEMENT']]) {
        assert.equal(attachmentEndpoint(owner, 21), `/${route}/21/attachments`);
        assert.equal(attachmentOwner(owner), kind);
    }
    assert.equal(attachmentDownloadPath(8), '/attachments/8/download');
});
test('Błędny właściciel i identyfikator nie tworzą adresu żądania', () => {
    for (const id of [0, -1, null, '../payments/8', '2?type=PAYMENT_CONFIRMATION']) assert.throws(() => attachmentEndpoint('subInvoice', id));
    assert.throws(() => attachmentEndpoint('unknown', 2)); assert.throws(() => attachmentDownloadPath('../3'));
});
test('Polskie etykiety i nowe rodzaje dokumentów są pobierane z backendu', () => {
    const data = [{ value: 'EXTRA_REPORT', label: 'Nowy dokument' }];
    assert.deepEqual(readAttachmentTypes(data), data);
    assert.throws(() => readAttachmentTypes([])); assert.throws(() => readAttachmentTypes([data[0], data[0]]));
});
test('Lista odrzuca powtórzone lub niekompletne metadane dokumentów', () => {
    assert.deepEqual(readAttachments([attachment]), [attachment]);
    assert.throws(() => readAttachments([attachment, attachment]));
    assert.throws(() => readAttachments([{ ...attachment, storedFileId: null }]));
    assert.throws(() => readAttachments([{ ...attachment, size: -1 }]));
});
test('PDF jest rozpoznawany po nazwie lub MIME, także przy polu type oznaczającym rodzaj załącznika', () => {
    assert.equal(isPdf({ name: 'SCAN.PDF', type: '' }), true);
    assert.equal(isPdf({ ...attachment, originalFilename: 'scan' }), true);
    assert.equal(isPdf({ name: 'invoice.xml', type: 'application/xml' }), false);
});
test('Zakresy stron akceptują listy, odstępy, powtórzenia i kolejność źródłowego dokumentu', () => {
    for (const value of ['1-3,7,10-12', '3,1-2,2', ' 1 - 3 , 7 ', '1', '2147483647']) assert.equal(pageSelectionError(value), '');
    for (const value of ['', '0', '-1', '3-1', '1,,3', '1,', '1-2-3', '1;2', '2147483648', '1.5']) assert.ok(pageSelectionError(value));
});
test('Puste strony zapisują cały plik, bez parametru pages', () => {
    const request = buildAttachmentRequest('subInvoice', 40, form({ mode: 'pages', pages: '  ' }), options);
    assert.equal(request.url, '/subcontractor-invoices/40/attachments'); assert.equal(request.data.has('pages'), false);
    assert.equal(request.data.get('file').name, 'pakiet.pdf');
});
test('Wybrane strony i opis są przesyłane jako multipart, z oryginalnym plikiem', () => {
    const request = buildAttachmentRequest('subInvoice', 40, form({ mode: 'pages', pages: ' 1-3,7 ', description: '  Skan  ' }), options);
    assert.equal(request.data.get('pages'), '1-3,7'); assert.equal(request.data.get('description'), 'Skan');
    assert.equal(request.data.get('type'), 'SUBCONTRACTOR_INVOICE');
    assert.equal(request.config.headers['Content-Type'], undefined);
});
test('Axios zachowuje FormData zamiast serializować plik do JSON', async () => {
    const client = axios.create({ headers: { 'Content-Type': 'application/json' }, adapter: async (config) => {
        assert.ok(config.data instanceof FormData); assert.equal(config.data.get('file').name, 'pakiet.pdf');
        assert.notEqual(config.headers.getContentType(), 'application/json');
        return { data: [], status: 200, statusText: 'OK', headers: {}, config };
    } });
    const request = buildAttachmentRequest('subInvoice', 40, form(), options);
    await client.post(request.url, request.data, request.config);
});
test('Podział PDF wysyła wszystkie części w jednym żądaniu', () => {
    const request = buildAttachmentRequest('payment', 8, form({ type: 'PAYMENT_CONFIRMATION', mode: 'split', parts: [' 1-3 ', '4,7-9'] }),
        [{ value: 'PAYMENT_CONFIRMATION', label: 'Potwierdzenie przelewu' }]);
    assert.equal(request.url, '/payments/8/attachments/split'); assert.equal(request.data.get('parts'), '1-3;4,7-9');
    assert.equal(request.data.has('pages'), false);
});
test('Nieprawidłowa lub pusta część i ponad 20 części blokują wysłanie formularza', () => {
    for (const parts of [['1'], ['1', ''], ['1-3', '7-2'], Array(21).fill('1')]) {
        assert.ok(validateAttachmentForm(form({ mode: 'split', parts }), options).parts);
    }
});
test('XML faktury jest obsługiwany bez wyboru stron PDF', () => {
    const xml = new File(['<Faktura/>'], 'faktura.xml', { type: 'application/xml' });
    assert.deepEqual(validateAttachmentForm(form({ file: xml }), options), {});
    assert.ok(validateAttachmentForm(form({ file: xml, mode: 'pages', pages: '1' }), options).pages);
});
test('Plik, rodzaj i długość opisu są weryfikowane przed zapisem', () => {
    assert.ok(validateAttachmentForm(form({ file: null }), options).file);
    assert.ok(validateAttachmentForm(form({ file: new File([], 'empty.pdf') }), options).file);
    assert.ok(validateAttachmentForm(form({ type: 'PAYMENT_CONFIRMATION' }), options).type);
    assert.ok(validateAttachmentForm(form({ description: 'a'.repeat(1001) }), options).description);
});
test('Błędy pobrania pliku odczytują komunikat JSON również z odpowiedzi Blob', async () => {
    const error = { message: 'Nieoczekiwany błąd', response: { status: 404, data: new Blob([JSON.stringify({ message: 'Nie znaleziono dokumentu.' })], { type: 'application/json' }) } };
    assert.equal(await attachmentError(error), 'Nie znaleziono dokumentu.');
    await assert.rejects(() => loadAttachmentBlob({ get: async () => { throw error; } }, attachment), /Nie znaleziono dokumentu/);
    assert.match(await attachmentError({ response: { status: 413 } }), /rozmiar/);
});
test('Pobieranie używa identyfikatora załącznika, a nie identyfikatora StoredFile', async () => {
    const blob = new Blob(['PDF']);
    const http = { get: async (url, config) => { assert.equal(url, '/attachments/8/download'); assert.equal(config.responseType, 'blob'); return { data: blob }; } };
    assert.equal(await loadAttachmentBlob(http, attachment), blob);
});
test('Ten sam przelew występuje przy fakturze tylko raz', () => {
    assert.equal(uniqueInvoicePayments([{ paymentId: 8 }, { paymentId: '8' }, { paymentId: 9 }]).length, 2);
});
test('Rejestracja i edycja oświadczenia zachowują właściwe powiązanie z fakturą', () => {
    const invoice = { id: 40, issueDate: '2026-10-01' }, form = { statementDate: '2026-10-17', remarks: '  Uwagi  ' };
    const create = buildStatementRequest(invoice, null, form), edit = buildStatementRequest(invoice, 9, form);
    assert.equal(create.method, 'post'); assert.equal(create.data.subcontractorInvoiceId, 40);
    assert.equal(edit.method, 'put'); assert.equal(edit.url, '/subcontractor-statements/9');
    assert.equal(edit.data.subcontractorInvoiceId, undefined); assert.equal(edit.data.remarks, 'Uwagi');
});
test('Niepoprawne daty oświadczenia i data sprzed wystawienia faktury są odrzucane', () => {
    for (const statementDate of ['', '2026-02-30', '2026-09-30']) {
        assert.throws(() => buildStatementRequest({ id: 40, issueDate: '2026-10-01' }, null, { statementDate }));
    }
});

test('Dokumenty polis i gwarancji zachowują kategorię przy wysyłaniu do umowy i aneksu', () => {
    const choices = readAttachmentTypes([
        { value: 'INSURANCE_DOCUMENT', label: 'Dokument ubezpieczenia' },
        { value: 'GUARANTEE_DOCUMENT', label: 'Dokument gwarancji bankowej/ubezpieczeniowej' },
    ]);
    for (const owner of ['contract', 'amendment']) for (const choice of choices) {
        const request = buildAttachmentRequest(owner, 7, form({ type: choice.value }), choices);
        assert.equal(request.url, attachmentEndpoint(owner, 7));
        assert.equal(request.data.get('type'), choice.value);
    }
});
