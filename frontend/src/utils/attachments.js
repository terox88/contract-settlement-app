const owners = {
    contract: ['contracts', 'CONTRACT'], amendment: ['contract-amendments', 'AMENDMENT'],
    mainInvoice: ['main-contractor-invoices', 'MAIN_INVOICE'], subInvoice: ['subcontractor-invoices', 'SUB_INVOICE'],
    payment: ['payments', 'PAYMENT'], statement: ['subcontractor-statements', 'STATEMENT'],
};
const validId = (value) => /^[1-9]\d*$/.test(String(value ?? ''));

export function attachmentEndpoint(owner, id) {
    if (!owners[owner] || !validId(id)) throw new Error('Nieprawidłowy kontekst załączników.');
    return `/${owners[owner][0]}/${id}/attachments`;
}
export function attachmentOwner(owner) {
    if (!owners[owner]) throw new Error('Nieprawidłowy rodzaj rekordu.');
    return owners[owner][1];
}
export function attachmentDownloadPath(id) {
    if (!validId(id)) throw new Error('Nieprawidłowy identyfikator załącznika.');
    return `/attachments/${id}/download`;
}
export function readAttachmentTypes(data) {
    const values = new Set();
    if (!Array.isArray(data) || !data.length) throw new Error('Nie można odczytać rodzajów załączników.');
    return data.map((option) => {
        if (!/^[A-Z_]+$/.test(option?.value ?? '') || values.has(option.value) || typeof option.label !== 'string' || !option.label.trim()) {
            throw new Error('Nie można odczytać rodzajów załączników.');
        }
        values.add(option.value); return option;
    });
}
export function readAttachments(data) {
    const ids = new Set();
    if (!Array.isArray(data)) throw new Error('Nie można odczytać listy załączników.');
    return data.map((item) => {
        if (!validId(item?.id) || !validId(item.storedFileId) || ids.has(String(item.id))
            || !/^[A-Z_]+$/.test(item.type ?? '') || typeof item.originalFilename !== 'string' || !item.originalFilename.trim()
            || (item.size != null && (!Number.isSafeInteger(item.size) || item.size < 0))) {
            throw new Error('Dane załączników są niekompletne. Odśwież listę.');
        }
        ids.add(String(item.id)); return item;
    });
}
export function isPdf(file) {
    return /\.pdf$/i.test(file?.name ?? file?.originalFilename ?? '')
        || String(file?.contentType ?? file?.type ?? '').toLowerCase() === 'application/pdf';
}
export function pageSelectionError(selection) {
    if (!String(selection ?? '').trim()) return 'Podaj strony, np. 1-3,7,10-12.';
    for (const token of String(selection).split(',')) {
        const match = token.trim().match(/^(\d+)(?:\s*-\s*(\d+))?$/);
        if (!match) return 'Nieprawidłowy zapis stron. Użyj np. 1-3,7,10-12.';
        const first = BigInt(match[1]), last = BigInt(match[2] ?? match[1]);
        if (first < 1n || last < first || last > 2147483647n) return 'Numery stron muszą być dodatnie, a zakres zapisany od mniejszej do większej strony.';
    }
    return '';
}
export function validateAttachmentForm(form, types) {
    const errors = {};
    if (!form.file || !form.file.name?.trim() || !Number.isSafeInteger(form.file.size) || form.file.size <= 0) errors.file = 'Wybierz niepusty plik.';
    if (!types.some((option) => option.value === form.type)) errors.type = 'Wybierz rodzaj załącznika.';
    if ((form.description ?? '').trim().length > 1000) errors.description = 'Opis może mieć najwyżej 1000 znaków.';
    if (!['all', 'pages', 'split'].includes(form.mode)) errors.pages = 'Wybierz sposób zapisania pliku.';
    if (form.mode !== 'all') {
        if (!isPdf(form.file)) errors.pages = 'Wybór stron i podział są dostępne tylko dla PDF.';
        else if (form.mode === 'pages' && String(form.pages ?? '').trim()) errors.pages = pageSelectionError(form.pages);
        else if (form.mode === 'split') {
            if (!Array.isArray(form.parts) || form.parts.length < 2 || form.parts.length > 20) errors.parts = 'Podaj od 2 do 20 części PDF.';
            else if (form.parts.some((part) => pageSelectionError(part))) errors.parts = 'Sprawdź strony każdej części. Przykład: część 1 — 1-3; część 2 — 4,7-9.';
        }
    }
    return Object.fromEntries(Object.entries(errors).filter(([, value]) => value));
}
export function buildAttachmentRequest(owner, id, form, types) {
    const errors = validateAttachmentForm(form, types);
    if (Object.keys(errors).length) throw new Error(Object.values(errors)[0]);
    const data = new FormData();
    data.append('file', form.file, form.file.name);
    data.append('type', form.type);
    if (form.description?.trim()) data.append('description', form.description.trim());
    if (form.mode === 'pages' && form.pages?.trim()) data.append('pages', form.pages.trim());
    if (form.mode === 'split') data.append('parts', form.parts.map((part) => part.trim()).join(';'));
    return { url: attachmentEndpoint(owner, id) + (form.mode === 'split' ? '/split' : ''), data,
        // Przeglądarka ustawi nagłówek multipart razem z granicą poszczególnych części.
        config: { headers: { 'Content-Type': undefined } } };
}
export async function attachmentError(error) {
    if (error.response?.status === 413) return 'Plik przekracza dopuszczalny rozmiar. Wybierz mniejszy plik lub zwiększ limit przesyłania na serwerze.';
    let data = error.response?.data;
    if (typeof Blob !== 'undefined' && data instanceof Blob) {
        try { data = JSON.parse(await data.text()); } catch { /* Zapasowy komunikat poniżej. */ }
    }
    const message = data?.message ?? data?.detail;
    return typeof message === 'string' && message.trim() ? message : error.message || 'Nie udało się wykonać operacji na załączniku.';
}
export async function loadAttachmentBlob(http, attachment) {
    try {
        const { data } = await http.get(attachmentDownloadPath(attachment.id), { responseType: 'blob' });
        if (!(data instanceof Blob)) throw new Error('Nie można odczytać zawartości pliku.');
        return data;
    } catch (error) { throw new Error(await attachmentError(error)); }
}
export function saveAttachmentBlob(blob, filename) {
    const url = URL.createObjectURL(blob), link = document.createElement('a');
    link.href = url; link.download = filename.replace(/^.*[\\/]/, '');
    document.body.append(link); link.click(); link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export function attachmentSize(size) {
    if (size == null) return '';
    return size < 1024 ? `${size} B` : size < 1048576 ? `${(size / 1024).toFixed(1)} KB` : `${(size / 1048576).toFixed(1)} MB`;
}
