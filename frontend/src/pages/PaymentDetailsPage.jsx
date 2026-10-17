import { useEffect, useRef, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import http from '../api/http';
import PaymentInvoicePicker from '../PaymentInvoicePicker';
import PaymentAllocationEditor from '../PaymentAllocationEditor';
import PaymentAllocationCard from '../PaymentAllocationCard';
import { contextFromSearch, invoicePaymentsPath, moneyCents, readAllocations, readPayment, targetKey } from '../utils/paymentForm';
import { formatInvoiceAmount } from '../utils/subcontractorInvoiceForm';
import { formatAmendmentDate } from '../utils/contractAmendmentForm';
import './MainContractFormPage.css';
import './PaymentsPage.css';

export default function PaymentDetailsPage() {
    const { paymentId } = useParams();
    const [search] = useSearchParams();
    let context = null;
    try { context = contextFromSearch(search); } catch { /* Uszkodzony kontekst nie wpływa na odczyt przelewu. */ }
    return <PaymentDetails key={paymentId} paymentId={paymentId} context={context} query={search.toString()} />;
}

function PaymentDetails({ paymentId, context, query }) {
    const [payment, setPayment] = useState(null), [rows, setRows] = useState([]), [target, setTarget] = useState(null);
    const [loading, setLoading] = useState(true), [loadError, setLoadError] = useState(''), [attempt, setAttempt] = useState(0);
    const [saving, setSaving] = useState(false), [saveError, setSaveError] = useState(''), [success, setSuccess] = useState('');
    const busyRef = useRef(false), aliveRef = useRef(false);
    useEffect(() => { aliveRef.current = true; return () => { aliveRef.current = false; }; }, []);
    useEffect(() => {
        let active = true; const controller = new AbortController(); setLoading(true); setLoadError('');
        Promise.all([
            http.get(`/payments/${paymentId}`, { signal: controller.signal }),
            http.get(`/payment-allocations/payment/${paymentId}`, { signal: controller.signal }),
        ]).then(([paymentResponse, allocationResponse]) => {
            if (!active) return;
            const data = readPayment(paymentResponse.data, paymentId);
            const allocations = readAllocations(allocationResponse.data, { paymentId, currency: data.currency });
            if (data.allocationCount !== allocations.length) throw new Error('Rozliczenia zmieniły się podczas odczytu. Odśwież dane.');
            setPayment(data); setRows(allocations);
        }).catch((failure) => { if (active) setLoadError(failure.message); }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [paymentId, attempt]);
    async function mutate(request, message) {
        if (busyRef.current || loading || loadError) return;
        busyRef.current = true; setSaving(true); setSaveError(''); setSuccess('');
        try {
            await http[request.method](request.url, request.data);
            if (aliveRef.current) { setTarget(null); setSuccess(message); setLoading(true); setAttempt((value) => value + 1); }
        } catch (failure) { if (aliveRef.current) setSaveError(failure.message); }
        finally { busyRef.current = false; if (aliveRef.current) setSaving(false); }
    }
    const excluded = rows.map((row) => targetKey({ type: row.mainContractorInvoiceId != null ? 'main' : 'sub', id: row.mainContractorInvoiceId ?? row.subcontractorInvoiceId }));
    return <>
        <Link to={context ? invoicePaymentsPath(context) : '/payments'} className="back-link">← {context ? 'Płatności faktury' : 'Przelewy'}</Link>
        <section className="card invoice-list-card"><div className="invoice-heading"><div><h1>Szczegóły przelewu</h1>
            {payment && <><h2>{formatAmendmentDate(payment.paymentDate)} · {formatInvoiceAmount(payment.amount, payment.currency)}</h2>
                <p className="payment-reference">{payment.bankReference || 'Brak identyfikatora przelewu'}</p></>}
        </div>{!loading && !loadError && !saving && <Link className="main-form-button" to={`/payments/${paymentId}/edit${query ? `?${query}` : ''}`}>Edytuj dane przelewu</Link>}</div>
            {success && <p className="settlement-success" role="status">{success}</p>}{saveError && <p className="error-message" role="alert">{saveError}</p>}
            {loading ? <p role="status">Ładowanie przelewu...</p> : loadError ? <div className="error-message" role="alert">{loadError}
                <button className="retry-button" onClick={() => setAttempt((value) => value + 1)}>Odśwież dane</button></div> : payment && <>
                {payment.description && <p className="invoice-description">{payment.description}</p>}
                <p className="invoice-totals">Przypisano do faktur: <strong>{formatInvoiceAmount(payment.allocatedAmount, payment.currency)}</strong>
                    {' '}· dostępne: <strong>{formatInvoiceAmount(payment.unallocatedAmount, payment.currency)}</strong> · liczba faktur: {rows.length}.</p>
                {moneyCents(payment.unallocatedAmount) < 0n && <p className="invoice-warning">Istniejące przypisania przekraczają wartość przelewu. Popraw ich kwoty.</p>}
                {moneyCents(payment.unallocatedAmount) > 0n && <section className="settlement-panel"><h2>Przypisz do kolejnej faktury</h2>
                    {target ? <><PaymentAllocationEditor key={targetKey(target)} payment={payment} target={target} disabled={saving}
                        onCancel={() => setTarget(null)} onSave={(request) => mutate(request, 'Przypisano przelew do faktury.')} /></>
                        : <PaymentInvoicePicker key={attempt} currency={payment.currency} initialContractId={context?.contractId} excluded={excluded} disabled={saving} onChoose={setTarget} />}
                </section>}
                {saving && <p role="status">Zapisywanie zmian...</p>}
                {!rows.length && <p className="invoice-empty">Ten przelew nie ma jeszcze przypisanych faktur.</p>}
                <div className="invoice-list">{rows.map((row) => <PaymentAllocationCard key={`${row.id}-${attempt}`} row={row} payment={payment}
                    disabled={saving} showPaymentLink={false} onMutate={mutate} />)}</div>
                <Link className="settlement-detail-link" to="/payments">Wszystkie przelewy →</Link>
            </>}
        </section>
    </>;
}
