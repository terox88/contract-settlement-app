import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import http from './api/http';
import AttachmentsPanel from './AttachmentsPanel';
import AttachmentDisclosure from './AttachmentDisclosure';
import { attachmentError } from './utils/attachments';
import { buildStatementRequest, uniqueInvoicePayments } from './utils/invoiceAttachments';
import { invoicePaymentsPath, readAllocations } from './utils/paymentForm';
import { formatAmendmentDate } from './utils/contractAmendmentForm';
import { formatInvoiceAmount } from './utils/subcontractorInvoiceForm';

export default function InvoiceAttachmentWorkspace({ invoice, type = 'sub', allocations, onChange }) {
    const [rows, setRows] = useState(null), [error, setError] = useState(''), [attempt, setAttempt] = useState(0);
    const context = { type, id: invoice.id, contractId: invoice.mainContractId, subcontractId: invoice.subcontractId, currency: invoice.currency };
    useEffect(() => {
        if (allocations !== undefined) return;
        let active = true; const controller = new AbortController(); setRows(null); setError('');
        http.get(`/payment-allocations/${type === 'main' ? 'main-contractor-invoice' : 'subcontractor-invoice'}/${invoice.id}`, { signal: controller.signal })
            .then(({ data }) => { const list = readAllocations(data, { target: context }); if (active) setRows(list); })
            .catch((failure) => { if (active) setError(failure.message); });
        return () => { active = false; controller.abort(); };
    }, [invoice.id, type, allocations, attempt]);
    const payments = uniqueInvoicePayments(allocations ?? rows ?? []);
    return <div className="invoice-attachment-workspace">
        <AttachmentsPanel ownerType={type === 'main' ? 'mainInvoice' : 'subInvoice'} ownerId={invoice.id}
            title="Plik faktury i dokumenty powiązane bezpośrednio" onChange={onChange} />
        {type === 'sub' && <>
            <StatementEditor key={`${invoice.id}-${invoice.statementId ?? 'new'}`} invoice={invoice} onChange={onChange} />
            {invoice.statementId && <AttachmentsPanel ownerType="statement" ownerId={invoice.statementId} title="Pliki zarejestrowanego oświadczenia" onChange={onChange} />}
        </>}
        <section className="attachments-panel"><h3>Potwierdzenia przelewów tej faktury</h3>
            <p className="attachments-muted">Potwierdzenie jest przypisane do przelewu i dostępne przy wszystkich fakturach rozliczanych tym przelewem.</p>
            {error ? <p className="error-message" role="alert">{error} <button type="button" onClick={() => setAttempt((value) => value + 1)}>Ponów odczyt przelewów</button></p>
                : allocations === undefined && rows === null ? <p role="status">Ładowanie przelewów tej faktury...</p>
                    : !payments.length ? <p>Najpierw zapisz przelew i przypisz go do tej faktury. <Link to={invoicePaymentsPath(context)}>Otwórz płatności faktury →</Link></p>
                        : payments.map((payment) => <AttachmentDisclosure key={payment.paymentId} ownerType="payment" ownerId={payment.paymentId} onChange={onChange}
                            title={`Potwierdzenia · ${formatAmendmentDate(payment.paymentDate)} · z przelewu na tę fakturę ${formatInvoiceAmount(payment.allocatedAmount, payment.paymentCurrency)}`} />)}
        </section>
    </div>;
}

function StatementEditor({ invoice, onChange }) {
    const prefix = useId(), alive = useRef(false), busy = useRef(false);
    const [id, setId] = useState(invoice.statementId ?? null);
    const [form, setForm] = useState({ statementDate: invoice.statementDate ?? '', remarks: invoice.statementRemarks ?? '' });
    const [saving, setSaving] = useState(false), [error, setError] = useState(''), [success, setSuccess] = useState('');
    useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
    async function save(event) {
        event.preventDefault(); if (busy.current) return;
        let request;
        try { request = buildStatementRequest(invoice, id, form); }
        catch (failure) { setError(failure.message); return; }
        busy.current = true; setSaving(true); setError(''); setSuccess('');
        try {
            const response = await http[request.method](request.url, request.data);
            if (!/^[1-9]\d*$/.test(String(response.data?.id ?? ''))) throw new Error('Nie można potwierdzić zapisu oświadczenia. Odśwież widok przed ponowną próbą.');
            if (!alive.current) { try { await onChange?.(); } catch { /* Widok nadrzędny obsłuży ponowny odczyt. */ } return; }
            setId(response.data.id); setSuccess('Zapisano dane oświadczenia. Dołącz również jego plik.');
            try { await onChange?.(); }
            catch { if (alive.current) setError('Oświadczenie zapisano. Odśwież widok, aby pobrać aktualne podsumowanie.'); }
        } catch (failure) { const message = await attachmentError(failure); if (alive.current) setError(message); }
        finally { busy.current = false; if (alive.current) setSaving(false); }
    }
    return <section className="attachments-panel"><h3>{id ? 'Dane oświadczenia podwykonawcy' : 'Zarejestruj oświadczenie podwykonawcy'}</h3>
        <p className="attachments-muted">Wpisz dane z otrzymanego oświadczenia. Oświadczenie potwierdza pełne rozliczenie tej faktury; uwagi zapiszesz poniżej. Plik możesz też dołączyć bezpośrednio do faktury.</p>
        {error && <p className="error-message" role="alert">{error}</p>}{success && <p className="attachments-success" role="status">{success}</p>}
        <form className="attachments-upload" onSubmit={save} noValidate><fieldset disabled={saving}>
            <label htmlFor={`${prefix}-date`}>Data oświadczenia *</label><input id={`${prefix}-date`} type="date" value={form.statementDate} required
                onChange={(event) => { setForm((current) => ({ ...current, statementDate: event.target.value })); setError(''); }} />
            <label htmlFor={`${prefix}-remarks`}>Uwagi</label><textarea id={`${prefix}-remarks`} value={form.remarks} maxLength={1000} rows={2}
                onChange={(event) => { setForm((current) => ({ ...current, remarks: event.target.value })); setError(''); }} />
            <button className="main-form-button" disabled={saving}>{saving ? 'Zapisywanie...' : id ? 'Zapisz dane oświadczenia' : 'Zarejestruj oświadczenie'}</button>
        </fieldset></form>
    </section>;
}
