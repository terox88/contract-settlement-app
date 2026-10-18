import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import http from '../api/http';
import { moneyCents, readPayments } from '../utils/paymentForm';
import { formatInvoiceAmount } from '../utils/subcontractorInvoiceForm';
import { formatAmendmentDate } from '../utils/contractAmendmentForm';
import './MainContractFormPage.css';
import './PaymentsPage.css';

export default function PaymentsPage() {
    const [payments, setPayments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [attempt, setAttempt] = useState(0);
    const [query, setQuery] = useState('');
    const [onlyAvailable, setOnlyAvailable] = useState(false);
    useEffect(() => {
        let active = true; const controller = new AbortController();
        setLoading(true); setError('');
        http.get('/payments', { signal: controller.signal }).then((response) => {
            if (active) setPayments(readPayments(response.data));
        }).catch((failure) => { if (active) setError(failure.message); }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [attempt]);
    const needle = query.trim().toLocaleLowerCase('pl-PL');
    const visible = payments.filter((payment) => `${payment.paymentDate} ${payment.description ?? ''}`.toLocaleLowerCase('pl-PL').includes(needle)
        && (!onlyAvailable || moneyCents(payment.unallocatedAmount) > 0n));
    return <>
        <Link className="back-link" to="/tasks">← Zadania inwestycyjne</Link>
        <section className="card invoice-list-card"><div className="invoice-heading"><div><h1>Przelewy</h1><p>Przelewy oraz ich przypisania do faktur GW i podwykonawców.</p></div>
            <Link className="main-form-button main-form-button-primary" to="/payments/new">Dodaj przelew</Link></div>
            {loading ? <p role="status">Ładowanie przelewów...</p> : error ? <div role="alert" className="error-message">{error}
                <button className="retry-button" onClick={() => setAttempt((value) => value + 1)}>Spróbuj ponownie</button></div> : <>
                <div className="invoice-filters"><label>Data lub uwagi<input value={query} onChange={(event) => setQuery(event.target.value)} /></label>
                    <label className="invoice-filter-check"><input type="checkbox" checked={onlyAvailable} onChange={(event) => setOnlyAvailable(event.target.checked)} /> Z kwotą do przypisania</label></div>
                <p className="invoice-muted" role="status">Widoczne przelewy: {visible.length} z {payments.length}.</p>
                {!payments.length ? <p>Brak przelewów. Dodaj pierwszy przelew lub otwórz płatności konkretnej faktury.</p> : !visible.length && <p>Brak przelewów pasujących do filtrów.</p>}
                <div className="invoice-list">{visible.map((payment) => <article className="invoice-item" key={payment.id}>
                    <div className="invoice-heading"><div><h2>{formatAmendmentDate(payment.paymentDate)} · {formatInvoiceAmount(payment.amount, payment.currency)}</h2>
                        {payment.description && <p className="invoice-description">{payment.description}</p>}
                        <p>Przypisano: {formatInvoiceAmount(payment.allocatedAmount, payment.currency)} · dostępne: {formatInvoiceAmount(payment.unallocatedAmount, payment.currency)}</p>
                        {moneyCents(payment.unallocatedAmount) < 0n && <p className="invoice-warning">Istniejące przypisania przekraczają kwotę przelewu. Sprawdź rozliczenia.</p>}
                    </div><Link className="main-form-button" to={`/payments/${payment.id}`}>Szczegóły i przypisania</Link></div>
                </article>)}</div>
            </>}
        </section>
    </>;
}
