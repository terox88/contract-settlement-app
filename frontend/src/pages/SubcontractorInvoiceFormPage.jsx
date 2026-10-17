import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import http from '../api/http';
import InvoiceDocuments from '../InvoiceDocuments';
import InvoiceFields, { InvoiceField } from '../InvoiceFields';
import { assignmentError, buildInvoiceAndAssignmentRequest, readMainInvoice } from '../utils/mainInvoiceSettlement';
import { buildInvoiceRequest, calculatedGross, emptyInvoice, invoiceToForm, readInvoice, validateInvoiceForm } from '../utils/subcontractorInvoiceForm';
import './MainContractFormPage.css';
import './SubcontractorInvoicesPage.css';

export default function SubcontractorInvoiceFormPage() {
    const { contractId, subcontractId, invoiceId } = useParams();
    const [searchParams] = useSearchParams();
    const mainInvoiceId = invoiceId ? null : searchParams.get('mainInvoiceId');
    return <InvoiceForm key={`${contractId}-${subcontractId}-${invoiceId ?? 'new'}-${mainInvoiceId ?? ''}`}
        contractId={contractId} subcontractId={subcontractId} invoiceId={invoiceId} mainInvoiceId={mainInvoiceId} />;
}

function InvoiceForm({ contractId, subcontractId, invoiceId, mainInvoiceId }) {
    const navigate = useNavigate();
    const returnPath = mainInvoiceId ? `/main-contracts/${contractId}/invoices/${mainInvoiceId}`
        : `/main-contracts/${contractId}/subcontracts/${subcontractId}/invoices`;
    const [mainInvoice, setMainInvoice] = useState(null);
    const [assignFull, setAssignFull] = useState(true);
    const [assignmentAmount, setAssignmentAmount] = useState('');
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
        if (mainInvoiceId) requests.push(http.get(`/main-contractor-invoices/${mainInvoiceId}`, { signal: controller.signal }));
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
                const main = mainInvoiceId ? readMainInvoice(invoiceResponse.data, contractId, mainInvoiceId) : null;
                if (main) setMainInvoice(main);
                setForm((current) => ({ ...current, vatRates: vat, currency: main?.currency ?? current.currency }));
            }
            setSubcontract(sub);
        }).catch((failure) => { if (active) setLoadError(failure.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [contractId, subcontractId, invoiceId, isEditing, mainInvoiceId, attempt]);

    function change(name, value) {
        setForm((current) => ({ ...current, [name]: value }));
        setErrors((current) => ({ ...current, [name]: '' }));
        setSaveError('');
    }

    async function handleSubmit(event) {
        event.preventDefault();
        if (savingRef.current || loading || loadError || !subcontract || !editable) return;
        const validation = validateInvoiceForm(form);
        if (mainInvoiceId) {
            if (!mainInvoice) return;
            if (form.currency.trim().toUpperCase() !== mainInvoice.currency.toUpperCase()) validation.currency = 'Waluta musi być zgodna z fakturą GW.';
            const amountError = assignmentError(assignFull ? calculatedGross(form)?.amount : assignmentAmount, calculatedGross(form)?.amount);
            if (amountError) validation.assignmentAmount = amountError;
        }
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
            const request = mainInvoiceId
                ? buildInvoiceAndAssignmentRequest(mainInvoiceId, subcontractId, form, assignFull ? calculatedGross(form).amount : assignmentAmount)
                : buildInvoiceRequest(subcontractId, invoiceId, form);
            await http[request.method](request.url, request.data);
            if (mountedRef.current) navigate(returnPath, { replace: true });
        } catch (failure) {
            if (mountedRef.current) setSaveError(failure.message);
        } finally {
            savingRef.current = false;
            if (mountedRef.current) setSaving(false);
        }
    }

    return <>
        {saving ? <span className="back-link" aria-disabled="true">← {mainInvoiceId ? 'Rozliczenie faktury GW' : 'Faktury podwykonawcy'}</span>
            : <Link className="back-link" to={returnPath}>← {mainInvoiceId ? 'Rozliczenie faktury GW' : 'Faktury podwykonawcy'}</Link>}
        <section className="card main-form-card">
            <div className="main-form-heading"><h1>{isEditing ? 'Edytuj fakturę podwykonawcy' : 'Dodaj fakturę podwykonawcy'}</h1>
                {subcontract && <p>{subcontract.subcontractorName} · umowa {subcontract.contractNumber}</p>}
                {mainInvoice && <p>Przypisanie do faktury GW {mainInvoice.invoiceNumber}.</p>}
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
                            <InvoiceFields form={form} errors={errors} change={change} />
                        </fieldset>
                        {mainInvoice && <fieldset className="main-form-section" disabled={saving}>
                            <legend>Przypisanie do faktury GW {mainInvoice.invoiceNumber}</legend>
                            <label className="invoice-filter-check"><input type="checkbox" checked={assignFull}
                                onChange={(event) => { setAssignFull(event.target.checked); setErrors((current) => ({ ...current, assignmentAmount: '' })); }} /> Przypisz całą wartość brutto</label>
                            <InvoiceField name="assignmentAmount" label="Kwota przypisana" inputMode="decimal" required readOnly={assignFull}
                                value={assignFull ? calculatedGross(form)?.amount ?? '' : assignmentAmount}
                                onChange={(value) => { setAssignmentAmount(value); setErrors((current) => ({ ...current, assignmentAmount: '' })); }}
                                error={errors.assignmentAmount} hint="Faktura i jej przypisanie zostaną zapisane razem. Przypisanie nie oznacza zapłaty." />
                        </fieldset>}
                        {isEditing ? <InvoiceDocuments invoice={savedInvoice} /> : <section className="invoice-document-note">
                            <h2>Dokumenty tej faktury</h2>
                            <p>Na liście faktur zobaczysz osobno plik faktury (PDF/XML), plik oświadczenia oraz potwierdzenia powiązanych przelewów.</p>
                            <p>Brak dokumentów pozwala zapisać fakturę do późniejszego uzupełnienia.</p>
                        </section>}
                        <div className="main-form-footer">
                            {editable && <button type="submit" className="main-form-button main-form-button-primary" disabled={saving}>
                                {saving ? 'Zapisywanie...' : isEditing ? 'Zapisz zmiany' : mainInvoiceId ? 'Zapisz fakturę i przypisz' : 'Dodaj fakturę'}
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

