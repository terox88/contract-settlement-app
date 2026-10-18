import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import http from '../api/http';
import PaymentFields from '../PaymentFields';
import PaymentInvoicePicker from '../PaymentInvoicePicker';
import { InvoiceField } from '../InvoiceFields';
import { buildPaymentRequest, contextFromSearch, emptyPayment, invoicePaymentTotals, loadTarget, moneyCents,
    paymentToForm, readAllocations, readPayment, targetKey, validatePaymentAllocations, validatePaymentForm } from '../utils/paymentForm';
import { centsToAmount } from '../utils/mainInvoiceSettlement';
import { formatInvoiceAmount, formatInvoiceCents } from '../utils/subcontractorInvoiceForm';
import './MainContractFormPage.css';
import './PaymentsPage.css';

export default function PaymentFormPage() {
    const { paymentId } = useParams();
    const [search] = useSearchParams();
    let context = null, contextError = '';
    try { context = contextFromSearch(search); } catch (failure) { contextError = failure.message; }
    return <PaymentForm key={`${paymentId ?? 'new'}-${search.toString()}`} paymentId={paymentId}
        context={context} contextError={contextError} query={search.toString()} />;
}

function PaymentForm({ paymentId, context, contextError, query }) {
    const navigate = useNavigate();
    const returnPath = paymentId ? `/payments/${paymentId}${query ? `?${query}` : ''}` : '/payments';
    const [form, setForm] = useState(emptyPayment);
    const [savedPayment, setSavedPayment] = useState(null);
    const [rows, setRows] = useState([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [attempt, setAttempt] = useState(0);
    const [errors, setErrors] = useState({});
    const [saveError, setSaveError] = useState('');
    const [saving, setSaving] = useState(false);
    const busyRef = useRef(false), aliveRef = useRef(false), formRef = useRef(null), nextKey = useRef(1);
    useEffect(() => { aliveRef.current = true; return () => { aliveRef.current = false; }; }, []);
    useEffect(() => {
        let active = true;
        const controller = new AbortController();
        setLoading(true); setLoadError('');
        async function load() {
            if (contextError) throw new Error(contextError);
            if (paymentId) {
                const response = await http.get(`/payments/${paymentId}`, { signal: controller.signal });
                const payment = readPayment(response.data, paymentId);
                if (active) { setSavedPayment(payment); setForm(paymentToForm(payment)); }
            } else if (context) {
                const target = await loadTarget(http, context, controller.signal);
                const endpoint = context.type === 'main' ? 'main-contractor-invoice' : 'subcontractor-invoice';
                const response = await http.get(`/payment-allocations/${endpoint}/${context.id}`, { signal: controller.signal });
                const allocations = readAllocations(response.data, { target });
                const remaining = invoicePaymentTotals(target, allocations).remaining;
                const amount = remaining > 0n ? centsToAmount(remaining) : '';
                if (active) {
                    setForm((current) => ({ ...current, currency: target.currency, amount }));
                    setRows([{ key: 0, target, allocatedAmount: amount, deductedAmount: '0' }]);
                }
            }
        }
        load().catch((failure) => { if (active) setLoadError(failure.message); }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [paymentId, attempt]);
    function change(name, value) {
        setForm((current) => ({ ...current, [name]: value }));
        if (name === 'amount') setRows((current) => current.length === 1 ? current.map((row) => ({ ...row, allocatedAmount: value })) : current);
        setErrors((current) => ({ ...current, [name]: '', allocations: '' })); setSaveError('');
    }
    function changeRow(key, name, value) {
        setRows((current) => current.map((row) => row.key === key ? { ...row, [name]: value } : row));
        setErrors((current) => ({ ...current, allocations: '' })); setSaveError('');
    }
    async function save(event) {
        event.preventDefault();
        if (busyRef.current || loading || loadError) return;
        const validation = { ...validatePaymentForm(form, savedPayment?.allocatedAmount ?? '0'),
            ...(!paymentId ? validatePaymentAllocations(form, rows) : {}) };
        setErrors(validation); setSaveError('');
        const first = Object.keys(validation)[0];
        if (first) { formRef.current?.elements.namedItem(first)?.focus(); return; }
        busyRef.current = true; setSaving(true);
        try {
            const request = buildPaymentRequest(form, rows, paymentId);
            const response = await http[request.method](request.url, request.data);
            if (aliveRef.current) navigate(response.data?.id ? `/payments/${response.data.id}${query ? `?${query}` : ''}` : '/payments', { replace: true });
        } catch (failure) { if (aliveRef.current) setSaveError(failure.message); }
        finally { busyRef.current = false; if (aliveRef.current) setSaving(false); }
    }
    const assigned = rows.reduce((sum, row) => sum + (moneyCents(row.allocatedAmount) ?? 0n), 0n);
    return <>
        {!saving && <Link className="back-link" to={returnPath}>← {paymentId ? 'Szczegóły przelewu' : 'Przelewy'}</Link>}
        <section className="card main-form-card"><div className="main-form-heading"><h1>{paymentId ? 'Edytuj przelew' : 'Dodaj przelew'}</h1>
            <p>Podaj datę i kwotę z potwierdzenia otrzymanego od zleceniodawcy. Pola oznaczone * są wymagane.</p></div>
            {loading ? <p role="status">Ładowanie danych...</p> : loadError ? <div className="error-message" role="alert">{loadError}
                <button className="retry-button" onClick={() => setAttempt((value) => value + 1)}>Spróbuj ponownie</button></div> : <>
                <form onSubmit={save} ref={formRef} noValidate>
                    {saveError && <p className="error-message" role="alert">{saveError}</p>}
                    <fieldset className="main-form-section" disabled={saving}><legend>Dane z potwierdzenia przelewu</legend>
                        <PaymentFields form={form} errors={errors} change={change} currencyLocked={savedPayment?.allocationCount > 0 || rows.length > 0} />
                    </fieldset>
                    {paymentId ? <p className="invoice-muted">Przypisano do faktur: {formatInvoiceAmount(savedPayment.allocatedAmount, savedPayment.currency)}. Kwoty i potrącenia poszczególnych faktur zmienisz w szczegółach przelewu.</p> : <fieldset className="main-form-section" disabled={saving}>
                        <legend>Faktury rozliczane tym przelewem</legend>
                        <p className="invoice-muted">Możesz wskazać kilka faktur tego samego odbiorcy i zleceniodawcy. Suma kwot przelewu przypisanych do faktur nie może przekroczyć wartości przelewu. Potrącenia są zapisywane osobno.</p>
                        <p>Przypisano: <strong>{formatInvoiceCents(assigned, form.currency)}</strong></p>
                        {errors.allocations && <p className="main-form-field-error" role="alert">{errors.allocations}</p>}
                        {rows.map((row) => <div className="payment-allocation-row" key={row.key}>
                            <h3>{row.target.type === 'main' ? 'GW' : 'Podwykonawca'} · {row.target.name} · {row.target.number}</h3>
                            <p className="invoice-muted">Pełne brutto faktury: {formatInvoiceAmount(row.target.grossAmount, row.target.currency)}</p>
                            <div className="main-form-grid">
                                {(rows.length > 1 || moneyCents(row.allocatedAmount) !== moneyCents(form.amount)) && <InvoiceField name={`paid-${row.key}`} label="Kwota z przelewu na tę fakturę" required inputMode="decimal" value={row.allocatedAmount} onChange={(value) => changeRow(row.key, 'allocatedAmount', value)} />}
                                <InvoiceField name={`deducted-${row.key}`} label="Potrącenia przy tym rozliczeniu" required inputMode="decimal" value={row.deductedAmount}
                                    readOnly={row.target.type === 'main'} onChange={(value) => changeRow(row.key, 'deductedAmount', value)} />
                            </div>
                            <button className="main-form-button" type="button" onClick={() => setRows((current) => current.filter((value) => value.key !== row.key))}>Usuń pozycję</button>
                        </div>)}
                        <div className="settlement-panel"><PaymentInvoicePicker currency={form.currency} initialContractId={context?.contractId} scope={rows[0]?.target} excluded={rows.map((row) => targetKey(row.target))}
                            disabled={saving || rows.length >= 100} onChoose={(target) => setRows((current) => [...current, {
                                key: nextKey.current++, target, allocatedAmount: current.length === 0 ? form.amount : '', deductedAmount: '0',
                            }])} /></div>
                        {!rows.length && <p className="invoice-muted">Przelew zostanie zapisany bez przypisań. Faktury można powiązać później.</p>}
                    </fieldset>}
                    <div className="main-form-footer"><button className="main-form-button main-form-button-primary" disabled={saving}>
                        {saving ? 'Zapisywanie...' : paymentId ? 'Zapisz przelew' : 'Zapisz przelew i przypisania'}</button>
                        <button type="button" className="main-form-button" disabled={saving} onClick={() => navigate(returnPath)}>Anuluj</button>
                    </div>
                </form>
            </>}
        </section>
    </>;
}
