import { useEffect, useId, useRef, useState } from 'react';
import http from './api/http';
import { buildKsefPreviewRequest, ksefSellerWarning, readKsefPreview } from './utils/ksefImport';
import { formatInvoiceAmount } from './utils/subcontractorInvoiceForm';
import './KsefInvoiceImport.css';

export default function KsefInvoiceImport({ expectedTaxId, disabled = false, onApply, onBusyChange }) {
    const [file, setFile] = useState(null);
    const [preview, setPreview] = useState(null);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const id = useId();
    const busyRef = useRef(false);
    const aliveRef = useRef(false);
    const controllerRef = useRef(null);
    useEffect(() => {
        aliveRef.current = true;
        return () => { aliveRef.current = false; controllerRef.current?.abort(); };
    }, []);

    async function load() {
        if (busyRef.current || disabled) return;
        setError(''); setPreview(null);
        let request;
        try { request = buildKsefPreviewRequest(file); }
        catch (failure) { setError(failure.message); return; }
        busyRef.current = true; setBusy(true); onBusyChange?.(true);
        const controller = new AbortController(); controllerRef.current = controller;
        try {
            const response = await http.post(request.url, request.data, { ...request.config, signal: controller.signal });
            const data = readKsefPreview(response.data);
            if (aliveRef.current) setPreview(data);
        } catch (failure) { if (aliveRef.current && !controller.signal.aborted) setError(failure.message); }
        finally {
            busyRef.current = false;
            if (aliveRef.current) { setBusy(false); onBusyChange?.(false); }
        }
    }

    const sellerWarning = preview ? ksefSellerWarning(preview, expectedTaxId) : '';
    return <fieldset className="ksef-import" disabled={disabled || busy}>
        <legend>Uzupełnij z XML KSeF</legend>
        <p>Wybierz oryginalny XML FA(2) lub FA(3), do 5 MB. Najpierw zobaczysz podgląd, a potem zdecydujesz o uzupełnieniu formularza.</p>
        <label htmlFor={id}>Plik XML faktury</label>
        <input id={id} type="file" accept=".xml,application/xml,text/xml" onChange={(event) => {
            setFile(event.target.files?.[0] ?? null); setPreview(null); setError('');
        }} />
        <button type="button" className="main-form-button" onClick={load}>{busy ? 'Odczytywanie XML...' : 'Odczytaj XML'}</button>
        {busy && <p role="status">Trwa odczytywanie faktury.</p>}
        {error && <p className="error-message" role="alert">{error}</p>}
        {preview && <div className="ksef-preview">
            <h2>Podgląd {preview.schema}: {preview.invoiceNumber}</h2>
            <dl>
                <dt>Sprzedawca</dt><dd>{preview.seller.name || 'Brak nazwy'} · NIP {preview.seller.taxId || 'brak'}</dd>
                <dt>Nabywca</dt><dd>{preview.buyer.name || 'Brak nazwy'} · NIP {preview.buyer.taxId || 'brak'}</dd>
                <dt>Rodzaj</dt><dd>{preview.invoiceKind === 'ZAL' ? 'Zaliczkowa' : preview.invoiceKind === 'UPR' ? 'Uproszczona' : 'VAT'}</dd>
                <dt>Wystawienie</dt><dd>{preview.issueDate}</dd>
                <dt>Sprzedaż</dt><dd>{preview.saleDate || 'Do uzupełnienia'}</dd>
                <dt>Termin płatności</dt><dd>{preview.dueDate || 'Do uzupełnienia'}</dd>
                <dt>Netto</dt><dd>{formatInvoiceAmount(preview.netAmount)}</dd>
                <dt>VAT</dt><dd>{formatInvoiceAmount(preview.vatAmount)}</dd>
                <dt>Brutto</dt><dd>{formatInvoiceAmount(preview.grossAmount)}</dd>
                <dt>Stawki VAT</dt><dd>{preview.vatRates.join('; ') || 'Do uzupełnienia'}</dd>
            </dl>
            {sellerWarning && <p className="invoice-warning" role="alert">{sellerWarning}</p>}
            {preview.warnings.length > 0 && <ul className="ksef-warnings">{preview.warnings.map((warning, index) => <li key={index}>{warning}</li>)}</ul>}
            <p>Przycisk poniżej zastąpi dane faktury w formularzu. Opis i ustawienia przypisania pozostaną zachowane. Przed zapisem możesz poprawić dane.</p>
            <button type="button" className="main-form-button" onClick={() => { if (!disabled && !busyRef.current) onApply(preview, file); }}>
                Uzupełnij formularz i dołącz XML przy zapisie</button>
        </div>}
    </fieldset>;
}
