import { useEffect, useRef, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import http from '../api/http';
import InvoiceDocuments from '../InvoiceDocuments';
import InvoiceAttachmentWorkspace from '../InvoiceAttachmentWorkspace';
import PaymentAllocationCard from '../PaymentAllocationCard';
import PaymentAllocationEditor from '../PaymentAllocationEditor';
import { invoicePaymentTotals, loadTarget, moneyCents, readAllocations, readPayments, paymentMatchesTarget, targetKey, targetQuery } from '../utils/paymentForm';
import { formatInvoiceAmount, formatInvoiceCents } from '../utils/subcontractorInvoiceForm';
import { formatDeductionRule } from '../utils/subcontractForm';
import './MainContractFormPage.css';
import './PaymentsPage.css';

export default function InvoicePaymentsPage() {
    const { contractId, subcontractId, invoiceId, mainInvoiceId } = useParams();
    const [search] = useSearchParams();
    const context = { contractId, subcontractId, type: invoiceId ? 'sub' : 'main', id: invoiceId ?? mainInvoiceId };
    const returnMainInvoiceId = /^[1-9]\d*$/.test(search.get('returnMainInvoiceId') ?? '') ? search.get('returnMainInvoiceId') : null;
    return <InvoicePayments key={`${context.type}-${context.id}`} context={{ ...context, returnMainInvoiceId }} returnMainInvoiceId={returnMainInvoiceId} />;
}

function InvoicePayments({ context, returnMainInvoiceId }) {
    const [target, setTarget] = useState(null), [rows, setRows] = useState([]), [payments, setPayments] = useState([]), [subcontract, setSubcontract] = useState(null);
    const [selectedPaymentId, setSelectedPaymentId] = useState(''), [loading, setLoading] = useState(true), [loadError, setLoadError] = useState('');
    const [attempt, setAttempt] = useState(0), [saving, setSaving] = useState(false), [saveError, setSaveError] = useState(''), [success, setSuccess] = useState('');
    const busyRef = useRef(false), aliveRef = useRef(false);
    const endpoint = context.type === 'main' ? 'main-contractor-invoice' : 'subcontractor-invoice';
    const returnPath = context.type === 'main' ? `/main-contracts/${context.contractId}/invoices/${context.id}`
        : returnMainInvoiceId ? `/main-contracts/${context.contractId}/invoices/${returnMainInvoiceId}`
            : `/main-contracts/${context.contractId}/subcontracts/${context.subcontractId}/invoices`;
    useEffect(() => { aliveRef.current = true; return () => { aliveRef.current = false; }; }, []);
    useEffect(() => {
        let active = true; const controller = new AbortController(); setLoading(true); setLoadError('');
        Promise.all([
            loadTarget(http, context, controller.signal),
            http.get(`/payment-allocations/${endpoint}/${context.id}`, { signal: controller.signal }),
            http.get('/payments', { signal: controller.signal }),
            context.type === 'sub' ? http.get(`/subcontracts/${context.subcontractId}`, { signal: controller.signal }) : Promise.resolve(null),
        ]).then(([invoiceTarget, allocationResponse, paymentResponse, subcontractResponse]) => {
            if (!active) return;
            const allocations = readAllocations(allocationResponse.data, { target: invoiceTarget });
            const list = readPayments(paymentResponse.data);
            if (subcontractResponse && (String(subcontractResponse.data?.id) !== String(context.subcontractId)
                || String(subcontractResponse.data.mainContractId) !== String(context.contractId))) throw new Error('Nie można odczytać umowy tej faktury.');
            setTarget(invoiceTarget); setRows(allocations); setPayments(list); setSubcontract(subcontractResponse?.data ?? null);
        }).catch((failure) => { if (active) setLoadError(failure.message); }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [context.type, context.id, attempt]);
    async function mutate(request, message) {
        if (busyRef.current || loading || loadError) return;
        busyRef.current = true; setSaving(true); setSaveError(''); setSuccess('');
        try {
            await http[request.method](request.url, request.data);
            if (aliveRef.current) { setSelectedPaymentId(''); setSuccess(message); setLoading(true); setAttempt((value) => value + 1); }
        } catch (failure) { if (aliveRef.current) setSaveError(failure.message); }
        finally { busyRef.current = false; if (aliveRef.current) setSaving(false); }
    }
    async function refreshDocuments() {
        const fresh = await loadTarget(http, context);
        if (aliveRef.current) setTarget(fresh);
    }
    const used = new Set(rows.map((row) => String(row.paymentId)));
    const available = target ? payments.filter((payment) => paymentMatchesTarget(payment, target) && !used.has(String(payment.id)) && payment.currency.toUpperCase() === target.currency.toUpperCase()
        && moneyCents(payment.unallocatedAmount) > 0n) : [];
    const selected = available.find((payment) => String(payment.id) === selectedPaymentId);
    const total = target ? invoicePaymentTotals(target, rows) : null;
    return <>
        <Link className="back-link" to={returnPath}>← {context.type === 'main' || returnMainInvoiceId ? 'Rozliczenie faktury GW' : 'Faktury podwykonawcy'}</Link>
        <section className="card invoice-list-card"><div className="invoice-heading"><div><h1>Płatności i potrącenia</h1>
            {target && <><h2>{target.type === 'main' ? 'Faktura GW' : 'Faktura podwykonawcy'} {target.number}</h2><p>{target.name}</p></>}
        </div>{!loading && !loadError && !saving && <Link className="main-form-button main-form-button-primary" to={`/payments/new?${targetQuery(target)}`}>Dodaj nowy przelew</Link>}</div>
            {success && <p className="settlement-success" role="status">{success}</p>}{saveError && <p className="error-message" role="alert">{saveError}</p>}
            {loading ? <p role="status">Ładowanie płatności...</p> : loadError ? <div className="error-message" role="alert">{loadError}
                <button className="retry-button" onClick={() => setAttempt((value) => value + 1)}>Odśwież dane</button></div> : target && <>
                <section className="invoice-totals"><dl className="invoice-metrics">
                    <Metric label="Brutto faktury" value={formatInvoiceAmount(target.grossAmount, target.currency)} />
                    <Metric label="Zapłacono" value={formatInvoiceCents(total.paid, target.currency)} />
                    <Metric label="Potrącono" value={formatInvoiceCents(total.deducted, target.currency)} />
                    <Metric label="Saldo do rozliczenia" value={formatInvoiceCents(total.remaining, target.currency)} />
                </dl></section>
                {total.remaining < 0n && <p className="invoice-warning" role="status">Suma zapłaty i potrąceń przekracza wartość faktury. Sprawdź kwoty rozliczeń.</p>}
                {subcontract && <p className="invoice-muted">Warunki umowy: zabezpieczenie — {formatDeductionRule(subcontract.performanceSecurityDeduction)}; inne potrącenia — {formatDeductionRule(subcontract.otherDeduction)}.
                    {' '}W tym widoku zapisujesz łączną kwotę potrąceń przy danym rozliczeniu.</p>}
                {target.type === 'sub' && <><InvoiceDocuments invoice={target.invoice} />
                    {target.invoice.statementSettlementMismatch && <p className="invoice-warning">Oświadczenie potwierdza pełne rozliczenie, ale płatności i potrącenia go nie potwierdzają.</p>}</>}
                <InvoiceAttachmentWorkspace invoice={target.invoice} type={target.type} allocations={rows} onChange={refreshDocuments} />
                <section className="settlement-panel"><h2>Przypisz istniejący przelew</h2>
                    <label htmlFor="invoice-existing-payment">Przelew w walucie {target.currency}</label>
                    <select id="invoice-existing-payment" disabled={saving} value={selectedPaymentId} onChange={(event) => setSelectedPaymentId(event.target.value)}>
                        <option value="">Wybierz przelew</option>{available.map((payment) => <option key={payment.id} value={payment.id}>
                            {payment.paymentDate} · dostępne {formatInvoiceAmount(payment.unallocatedAmount, payment.currency)}</option>)}</select>
                    {!available.length && <p className="invoice-muted">Brak kolejnych przelewów z dostępną kwotą. Przelewy już powiązane z fakturą zmienisz poniżej.</p>}
                    {selected && <PaymentAllocationEditor key={selected.id} payment={selected} target={target} disabled={saving}
                        onSave={(request) => mutate(request, 'Przypisano przelew do faktury.')} />}
                </section>
                {saving && <p role="status">Zapisywanie zmian...</p>}
                {!rows.length && <p className="invoice-empty">Brak zapisanych płatności tej faktury.</p>}
                <div className="invoice-list">{rows.map((row) => <PaymentAllocationCard key={`${row.id}-${attempt}`} row={row} target={target}
                    disabled={saving} showInvoiceLink={false} onMutate={mutate} />)}</div>
                <Link className="settlement-detail-link" to="/payments">Wszystkie przelewy →</Link>
            </>}
        </section>
    </>;
}
function Metric({ label, value }) { return <div><dt>{label}</dt><dd>{value}</dd></div>; }
