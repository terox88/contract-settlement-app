import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import http from '../api/http';
import InvoiceFields from '../InvoiceFields';
import { emptyInvoice, invoiceToForm, validateInvoiceForm } from '../utils/subcontractorInvoiceForm';
import { buildMainInvoiceRequest, readMainInvoice } from '../utils/mainInvoiceSettlement';
import './MainContractFormPage.css';
import './MainInvoiceSettlementPage.css';

export default function MainContractorInvoiceFormPage() {
    const { contractId, mainInvoiceId } = useParams();
    return <MainInvoiceForm key={`${contractId}-${mainInvoiceId ?? 'new'}`} contractId={contractId} mainInvoiceId={mainInvoiceId} />;
}

function MainInvoiceForm({ contractId, mainInvoiceId }) {
    const navigate = useNavigate();
    const base = `/main-contracts/${contractId}/invoices`;
    const returnPath = mainInvoiceId ? `${base}/${mainInvoiceId}` : base;
    const [contract, setContract] = useState(null);
    const [form, setForm] = useState(emptyInvoice);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [attempt, setAttempt] = useState(0);
    const [errors, setErrors] = useState({});
    const [saveError, setSaveError] = useState('');
    const [saving, setSaving] = useState(false);
    const busyRef = useRef(false);
    const aliveRef = useRef(false);
    const formRef = useRef(null);
    useEffect(() => { aliveRef.current = true; return () => { aliveRef.current = false; }; }, []);
    useEffect(() => {
        let active = true;
        const controller = new AbortController();
        setLoading(true); setLoadError('');
        const requests = [http.get(`/main-contracts/${contractId}`, { signal: controller.signal })];
        if (mainInvoiceId) requests.push(http.get(`/main-contractor-invoices/${mainInvoiceId}`, { signal: controller.signal }));
        Promise.all(requests).then(([contractResponse, invoiceResponse]) => {
            if (!active) return;
            if (String(contractResponse.data?.id) !== String(contractId)) throw new Error('Nie można odczytać umowy głównej.');
            if (mainInvoiceId) setForm(invoiceToForm(readMainInvoice(invoiceResponse.data, contractId, mainInvoiceId)));
            else setForm((current) => ({ ...current, vatRates: String(contractResponse.data.vatRate ?? '') }));
            setContract(contractResponse.data);
        }).catch((failure) => { if (active) setLoadError(failure.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [contractId, mainInvoiceId, attempt]);

    function change(name, value) {
        setForm((current) => ({ ...current, [name]: value }));
        setErrors((current) => ({ ...current, [name]: '' })); setSaveError('');
    }
    async function save(event) {
        event.preventDefault();
        if (busyRef.current || loading || loadError || !contract) return;
        const validation = validateInvoiceForm(form);
        setErrors(validation); setSaveError('');
        const first = Object.keys(validation)[0];
        if (first) { formRef.current?.elements.namedItem(first)?.focus(); return; }
        busyRef.current = true; setSaving(true);
        try {
            const request = buildMainInvoiceRequest(contractId, mainInvoiceId, form);
            const response = await http[request.method](request.url, request.data);
            // Po udanym zapisie bez identyfikatora wracamy do listy, aby nie ponowić POST.
            if (aliveRef.current) navigate(response.data?.id ? `${base}/${response.data.id}` : base, { replace: true });
        } catch (failure) { if (aliveRef.current) setSaveError(failure.message); }
        finally { busyRef.current = false; if (aliveRef.current) setSaving(false); }
    }
    return <>
        {!saving && <Link className="back-link" to={returnPath}>← {mainInvoiceId ? 'Rozliczenie faktury GW' : 'Faktury GW'}</Link>}
        <section className="card main-form-card"><div className="main-form-heading">
            <h1>{mainInvoiceId ? 'Edytuj fakturę GW' : 'Dodaj fakturę GW'}</h1>
            {contract && <p>Umowa {contract.contractNumber} · {contract.mainContractorName}</p>}<p>Pola oznaczone * są wymagane.</p>
        </div>{loading ? <p role="status">Ładowanie danych...</p> : loadError ? <div className="error-message" role="alert">{loadError}
            <button className="retry-button" onClick={() => setAttempt((value) => value + 1)}>Spróbuj ponownie</button></div> : <form ref={formRef} onSubmit={save} noValidate>
            {saveError && <p className="error-message" role="alert">{saveError}</p>}
            <fieldset className="main-form-section" disabled={saving}><legend>Dane faktury GW</legend>
                <InvoiceFields form={form} errors={errors} change={change} />
            </fieldset>
            <p className="invoice-muted">Po zapisaniu faktury przejdziesz do przypisywania faktur podwykonawców i weryfikacji ich rozliczeń.</p>
            <div className="main-form-footer"><button className="main-form-button main-form-button-primary" disabled={saving}>
                {saving ? 'Zapisywanie...' : 'Zapisz fakturę GW'}</button>
                <button type="button" className="main-form-button" disabled={saving} onClick={() => navigate(returnPath)}>Anuluj</button>
            </div>
        </form>}</section>
    </>;
}
