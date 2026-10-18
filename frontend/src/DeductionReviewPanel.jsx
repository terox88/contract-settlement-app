import { useEffect, useRef, useState } from 'react';
import http from './api/http';
import { projectedDeduction, readDeductionReview } from './utils/reviews';
import { formatInvoiceAmount, formatInvoiceCents } from './utils/subcontractorInvoiceForm';
import './ReviewPanels.css';

export function DeductionReviewSummary({ review, currency = 'PLN' }) {
    if (!review) return <p className="invoice-muted">Kontrola potrąceń: brak danych.</p>;
    const warning = review.status === 'MISMATCH', confirmed = review.status === 'CONFIRMED';
    return <div className="deduction-summary">
        <span className={`review-badge ${warning ? 'review-badge-warning' : ['CONFIRMED','MATCHED'].includes(review.status) ? 'review-badge-good' : ''}`}>{review.label}</span>
        <p>Oczekiwane dla całej faktury: <strong>{formatInvoiceAmount(review.expectedAmount, currency)}</strong> · zapisane łącznie: <strong>{formatInvoiceAmount(review.actualAmount, currency)}</strong>.</p>
        {(warning || confirmed) && <p>Różnica (zapisane minus oczekiwane): {formatInvoiceAmount(review.difference, currency)}.</p>}
        {review.confirmationOutdated && <p className="invoice-warning">Zmiana kwot lub reguł unieważniła wcześniejsze potwierdzenie.</p>}
        {confirmed && review.confirmedAt && <p>Potwierdzono: {new Date(review.confirmedAt).toLocaleString('pl-PL')}.</p>}
    </div>;
}
export function DraftDeductionWarning({ target, amount, oldAmount = '0' }) {
    if (target?.type !== 'sub') return null;
    const result = projectedDeduction(target.invoice?.deductionReview, amount, oldAmount);
    if (!result || result.difference === 0n) return null;
    return <p className="invoice-warning" role="status">Po zapisie suma potrąceń tej faktury wyniesie {formatInvoiceCents(result.actual, target.currency)},
        {' '}a według umowy dla całej faktury oczekiwane jest {formatInvoiceCents(result.expected, target.currency)}.
        {' '}Przy częściowych przelewach porównujemy sumę wszystkich potrąceń. Ostrzeżenie nie blokuje zapisu.</p>;
}
export default function DeductionReviewPanel({ invoice, onChange }) {
    const [review, setReview] = useState(invoice.deductionReview ?? null), [checked, setChecked] = useState(false);
    const [busy, setBusy] = useState(false), [error, setError] = useState('');
    const lock = useRef(false), alive = useRef(false);
    useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
    useEffect(() => { setReview(invoice.deductionReview ?? null); setChecked(false); }, [invoice]);
    async function save(revoke) {
        if (lock.current || !review || (!revoke && !checked)) return;
        lock.current = true; setBusy(true); setError('');
        try {
            const response = await http[revoke ? 'delete' : 'post'](`/subcontractor-invoices/${invoice.id}/deduction-review`, { fingerprint: review.fingerprint });
            const value = readDeductionReview(response.data);
            if (alive.current) { setReview(value); setChecked(false); }
            try { await onChange?.(); } catch { if (alive.current) setError('Potwierdzenie zapisano. Odśwież podsumowanie faktury.'); }
        } catch (failure) { if (alive.current) setError(failure.message); }
        finally { lock.current = false; if (alive.current) setBusy(false); }
    }
    return <section className="review-panel"><h3>Kontrola potrąceń według umowy</h3>
        <DeductionReviewSummary review={review} currency={invoice.currency} />
        {review && <p className="invoice-muted">Zabezpieczenie: {formatInvoiceAmount(review.securityAmount, invoice.currency)}; inne potrącenia: {formatInvoiceAmount(review.otherAmount, invoice.currency)}.
            {' '}Każdy składnik zaokrąglamy do grosza, a następnie sumujemy. Podstawa netto/brutto i procent pochodzą z umowy tej faktury.</p>}
        {review?.status === 'MISMATCH' && <><label className="review-check"><input type="checkbox" checked={checked} disabled={busy} onChange={event => setChecked(event.target.checked)} />Sprawdziłem dokumenty — potrącenia są prawidłowe mimo różnicy</label>
            <button type="button" className="main-form-button" disabled={busy || !checked} onClick={() => save(false)}>Potwierdź poprawność potrąceń</button></>}
        {review?.status === 'CONFIRMED' && <button type="button" className="main-form-button" disabled={busy} onClick={() => save(true)}>Cofnij potwierdzenie</button>}
        {error && <p className="error-message" role="alert">{error}</p>}
    </section>;
}
