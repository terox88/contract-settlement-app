import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import http from '../api/http';
import InvoiceDocuments from '../InvoiceDocuments';
import { buildInvoiceRequest, calculatedGross, emptyInvoice, invoiceToForm, readInvoice, validateInvoiceForm } from '../utils/subcontractorInvoiceForm';
import './MainContractFormPage.css';
import './SubcontractorInvoicesPage.css';

export default function SubcontractorInvoiceFormPage() {
    const { contractId, subcontractId, invoiceId } = useParams();
    return <InvoiceForm key={`${contractId}-${subcontractId}-${invoiceId ?? 'new'}`}
        contractId={contractId} subcontractId={subcontractId} invoiceId={invoiceId} />;
}

function InvoiceForm({ contractId, subcontractId, invoiceId }) {
    const navigate = useNavigate();
    const returnPath = `/main-contracts/${contractId}/subcontracts/${subcontractId}/invoices`;
    const isEditing = Boolean(invoiceId);
    const [subcontract, setSubcontract] = useState(null);
    const [savedInvoice, setSavedInvoice] = useState(null);
    const [form, setForm] = useState(emptyInvoice);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [attempt, setAttempt] = useState(0);
    const [errors, setErrors] = useState({});
    const [saveError, setSaveError] = useState('');
    const [saving, setSaving] = useState(false);
    const formRef = useRef(null);
    const savingRef = useRef(false);
    const mountedRef = useRef(false);
    const editable = !isEditing || savedInvoice?.editable === true;

    useEffect(() => {
        mountedRef.current = true;
        return () => { mountedRef.current = false; };
    }, []);

    useEffect(() => {
        let active = true;
        const controller = new AbortController();
        setLoading(true);
        setLoadError('');
        const requests = [http.get(`/subcontracts/${subcontractId}`, { signal: controller.signal })];
        if (isEditing) requests.push(http.get(`/subcontractor-invoices/${invoiceId}`, { signal: controller.signal }));
        Promise.all(requests).then(([subResponse, invoiceResponse]) => {
            if (!active) return;
            const sub = subResponse.data;
            if (!sub?.id || String(sub.id) !== String(subcontractId) || String(sub.mainContractId) !== String(contractId)) {
                throw new Error('Nie można odczytać umowy podwykonawczej przypisanej do tej umowy głównej.');
            }
            if (isEditing) {
                const invoice = readInvoice(invoiceResponse.data, subcontractId);
                if (String(invoice.id) !== String(invoiceId)) throw new Error('Nie można odczytać wskazanej faktury.');
                setSavedInvoice(invoice);
                setForm(invoiceToForm(invoice));
            } else {
                const vat = sub.vatRate == null ? '' : String(sub.vatRate).replace(/(\.\d*?[1-9])0+$|\.0+$/, '$1');
                setForm((current) => ({ ...current, vatRates: vat }));
            }
            setSubcontract(sub);
        }).catch((failure) => { if (active) setLoadError(failure.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [contractId, subcontractId, invoiceId, isEditing, attempt]);

    function change(name, value) {
        setForm((current) => ({ ...current, [name]: value }));
        setErrors((current) => ({ ...current, [name]: '' }));
        setSaveError('');
    }

    async function handleSubmit(event) {
        event.preventDefault();
        if (savingRef.current || loading || loadError || !subcontract || !editable) return;
        const validation = validateInvoiceForm(form);
        setErrors(validation);
        setSaveError('');
        const firstInvalid = Object.keys(validation)[0];
        if (firstInvalid) {
            formRef.current?.elements.namedItem(firstInvalid)?.focus();
            return;
        }
        savingRef.current = true;
        setSaving(true);
        try {
            const request = buildInvoiceRequest(subcontractId, invoiceId, form);
            await http[request.method](request.url, request.data);
            if (mountedRef.current) navigate(returnPath, { replace: true });
        } catch (failure) {
            if (mountedRef.current) setSaveError(failure.message);
        } finally {
            savingRef.current = false;
            if (mountedRef.current) setSaving(false);
        }
    }

    const gross = calculatedGross(form);
    const field = (name, label, options = {}) => <InvoiceField name={name} label={label} value={form[name]}
        onChange={(value) => change(name, value)} error={errors[name]} {...options} />;
    return <>
        {saving ? <span className="back-link" aria-disabled="true">← Faktury podwykonawcy</span>
            : <Link className="back-link" to={returnPath}>← Faktury podwykonawcy</Link>}
        <section className="card main-form-card">
            <div className="main-form-heading"><h1>{isEditing ? 'Edytuj fakturę podwykonawcy' : 'Dodaj fakturę podwykonawcy'}</h1>
                {subcontract && <p>{subcontract.subcontractorName} · umowa {subcontract.contractNumber}</p>}
                <p>Pola oznaczone * są wymagane.</p>
            </div>
            {loading ? <p className="loading-message" role="status">Ładowanie danych...</p>
                : loadError ? <div className="error-message" role="alert">{loadError}
                    <button type="button" className="retry-button" onClick={() => setAttempt((value) => value + 1)}>Spróbuj ponownie</button>
                </div> : <>
                    {!editable && <p className="invoice-warning" role="status">{savedInvoice?.editBlockedReason
                        || 'Nie można potwierdzić możliwości edycji tej faktury. Odśwież dane.'}</p>}
                    <form ref={formRef} onSubmit={handleSubmit} noValidate>
                        {saveError && <div className="error-message main-form-alert" role="alert">{saveError}</div>}
                        <fieldset className="main-form-section" disabled={saving || !editable}>
                            <legend>Dane faktury</legend>
                            <div className="main-form-grid">
                                {field('invoiceNumber', 'Numer faktury', { required: true, maxLength: 255 })}
                                {field('issueDate', 'Data wystawienia', { required: true, type: 'date' })}
                                {field('saleDate', 'Data sprzedaży', { type: 'date' })}
                                {field('dueDate', 'Termin płatności', { type: 'date' })}
                                {field('netAmount', 'Kwota netto', { required: true, inputMode: 'decimal' })}
                                {field('vatAmount', 'Kwota VAT', { required: true, inputMode: 'decimal' })}
                                <InvoiceField name="grossAmount" label="Kwota brutto — netto + VAT" value={gross?.amount.replace('.', ',') ?? ''} readOnly />
                                {field('currency', 'Waluta', { required: true, maxLength: 3 })}
                                {field('vatRates', 'Stawki VAT', { required: true, wide: true,
                                    hint: 'Wpisz stawki z faktury, rozdzielając je średnikami, np. 23; 8; ZW.' })}
                                {field('description', 'Opis', { wide: true, multiline: true, maxLength: 1000 })}
                            </div>
                        </fieldset>
                        {isEditing ? <InvoiceDocuments invoice={savedInvoice} /> : <section className="invoice-document-note">
                            <h2>Dokumenty tej faktury</h2>
                            <p>Na liście faktur zobaczysz osobno plik faktury (PDF/XML), plik oświadczenia oraz potwierdzenia powiązanych przelewów.</p>
                            <p>Brak dokumentów pozwala zapisać fakturę do późniejszego uzupełnienia.</p>
                        </section>}
                        <div className="main-form-footer">
                            {editable && <button type="submit" className="main-form-button main-form-button-primary" disabled={saving}>
                                {saving ? 'Zapisywanie...' : isEditing ? 'Zapisz zmiany' : 'Dodaj fakturę'}
                            </button>}
                            <button type="button" className="main-form-button" disabled={saving} onClick={() => navigate(returnPath)}>
                                {editable ? 'Anuluj' : 'Wróć do listy'}
                            </button>
                            <span className="main-form-hint" role="status">{saving ? 'Trwa zapisywanie faktury.' : ''}</span>
                        </div>
                    </form>
                </>}
        </section>
    </>;
}

function InvoiceField({ name, label, value, onChange, error, required = false, type = 'text', inputMode,
    readOnly = false, multiline = false, wide = false, maxLength, hint }) {
    const id = `invoice-form-${name}`;
    const describedBy = [error ? `${id}-error` : '', hint ? `${id}-hint` : ''].filter(Boolean).join(' ') || undefined;
    const props = { id, name, value, required, readOnly, maxLength, inputMode,
        'aria-invalid': Boolean(error), 'aria-describedby': describedBy,
        onChange: onChange ? (event) => onChange(event.target.value) : undefined };
    return <div className={`main-form-field${wide ? ' main-form-field-wide' : ''}`}>
        <label htmlFor={id}>{label}{required ? ' *' : ''}</label>
        {multiline ? <textarea {...props} rows={3} /> : <input {...props} type={type} />}
        {hint && <p className="invoice-field-hint" id={`${id}-hint`}>{hint}</p>}
        {error && <p className="main-form-field-error" id={`${id}-error`}>{error}</p>}
    </div>;
}
