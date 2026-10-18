import { useEffect, useRef, useState } from 'react';
import http from './api/http';
import { completeInvoiceRequest, readMainInvoiceReview } from './utils/reviews';
import './ReviewPanels.css';

export default function MainInvoiceReviewPanel({ invoiceId, initialReview, onChange, refreshKey }) {
    const [review, setReview] = useState(initialReview ?? null), [loading, setLoading] = useState(!initialReview);
    const [error, setError] = useState(''), [success, setSuccess] = useState(''), [open, setOpen] = useState(false);
    const [waived, setWaived] = useState(false), [reason, setReason] = useState(''), [checked, setChecked] = useState(false);
    const [busy, setBusy] = useState(false), [attempt, setAttempt] = useState(0);
    const alive = useRef(false), lock = useRef(false);
    const url = `/main-contractor-invoices/${invoiceId}/review`;
    useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
    useEffect(() => {
        let active = true; const controller = new AbortController(); setLoading(true); setError(''); setChecked(false);
        const request = initialReview && attempt === 0 ? Promise.resolve({ data: initialReview }) : http.get(url, { signal: controller.signal });
        request.then(({data}) => { const value = readMainInvoiceReview(data, invoiceId); if (active) setReview(value); })
            .catch(failure => { if (active) { setReview(null); setError(failure.message); } })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [invoiceId, initialReview, refreshKey, attempt]);
    async function save(reopen = false) {
        if (lock.current || loading || !review) return;
        let data;
        try { if (!reopen) data = completeInvoiceRequest(review, { checked, waived, reason }); }
        catch (failure) { setError(failure.message); return; }
        lock.current = true; setBusy(true); setError(''); setSuccess('');
        try {
            const response = await http[reopen ? 'delete' : 'post'](url, data);
            const value = readMainInvoiceReview(response.data, invoiceId);
            if (alive.current) { setReview(value); setChecked(false); setSuccess(reopen ? 'Przywrócono fakturę do rozliczenia.' : 'Potwierdzono rozliczenie faktury.'); }
            try { await onChange?.(); } catch { if (alive.current) setError('Status zapisano. Odśwież widok, aby pobrać aktualną listę.'); }
        } catch (failure) { if (alive.current) setError(failure.message); }
        finally { lock.current = false; if (alive.current) setBusy(false); }
    }
    const good = review?.status === 'COMPLETED';
    return <section className="review-panel" aria-label="Status rozliczenia faktury GW">
        <span className={`review-badge ${good ? 'review-badge-good' : review?.status === 'REVIEW_REQUIRED' ? 'review-badge-warning' : ''}`}>
            {loading ? 'Sprawdzanie statusu...' : review?.label ?? 'Nie ustalono statusu'}</span>
        {!loading && review && <>
            <p>Powiązane faktury PW wszystkich szczebli: {review.linkedInvoiceCount} · braki dokumentów: {review.missingDocuments.length}.</p>
            {review.documentsExemptionReason && <p>{review.status === 'COMPLETED' ? 'Dokumenty oznaczone jako niewymagane' : 'Poprzednie uzasadnienie niewymagania dokumentów'}: {review.documentsExemptionReason}</p>}
            {review.status === 'REVIEW_REQUIRED' && <p className="invoice-warning">Po potwierdzeniu zmieniły się dane rozliczenia. Poprzednie potwierdzenie wymaga ponownej weryfikacji.</p>}
            <button type="button" className="main-form-button" disabled={busy} onClick={() => setOpen(!open)}>{open ? 'Zwiń kontrolę' : good ? 'Pokaż potwierdzenie' : 'Sprawdź i oznacz jako rozliczoną'}</button>
            {open && <>
                <p>Status jest potwierdzeniem sprawdzenia rozliczenia i dokumentów. Wartość faktury GW może być wyższa od sumy faktur podwykonawców. Zapłatę na rzecz GW sprawdzisz osobno w płatnościach.</p>
                {review.missingDocuments.length > 0 && <><strong>Brakujące dokumenty</strong><ul>{review.missingDocuments.map((item, i) => <li key={i}>{item}</li>)}</ul></>}
                {review.warnings.length > 0 && <><strong>Do sprawdzenia</strong><ul>{review.warnings.map((item, i) => <li key={i}>{item}</li>)}</ul></>}
                {good ? <><p>Potwierdzono: {new Date(review.reviewedAt).toLocaleString('pl-PL')}.</p>
                    <button type="button" className="main-form-button" disabled={busy} onClick={() => save(true)}>Przywróć do rozliczenia</button></>
                    : <form onSubmit={event => { event.preventDefault(); save(); }}><fieldset disabled={busy}><legend>Potwierdzenie rozliczenia</legend>
                        <label className="review-check"><input type="checkbox" checked={waived} onChange={event => setWaived(event.target.checked)} />Brakujące dokumenty nie są wymagane w tym rozliczeniu</label>
                        {waived && <label>Uzasadnienie *<textarea value={reason} maxLength={1000} rows={3} onChange={event => setReason(event.target.value)} /></label>}
                        <label className="review-check"><input type="checkbox" checked={checked} onChange={event => setChecked(event.target.checked)} />Sprawdziłem powiązania, rozliczenia i kompletność wymaganych dokumentów; potwierdzam rozliczenie faktury</label>
                        <button className="main-form-button main-form-button-primary" disabled={busy || !checked || (!waived && review.missingDocuments.length > 0)}>
                            {busy ? 'Zapisywanie...' : 'Oznacz fakturę jako rozliczoną'}</button>
                    </fieldset></form>}
            </>}
        </>}
        {success && <p className="attachments-success" role="status">{success}</p>}
        {error && <p className="error-message" role="alert">{error} <button type="button" disabled={busy} onClick={() => setAttempt(value => value + 1)}>Odśwież status</button></p>}
    </section>;
}
