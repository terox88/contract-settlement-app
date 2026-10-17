import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import http from '../api/http';
import { readMainInvoices } from '../utils/mainInvoiceSettlement';
import { formatInvoiceAmount, formatInvoiceCents } from '../utils/subcontractorInvoiceForm';
import { formatAmendmentDate, parseAmendmentAmount } from '../utils/contractAmendmentForm';
import './MainContractFormPage.css';
import './MainInvoiceSettlementPage.css';

export default function MainContractorInvoicesPage() {
    const { contractId } = useParams();
    return <MainInvoiceList key={contractId} contractId={contractId} />;
}

function MainInvoiceList({ contractId }) {
    const [contract, setContract] = useState(null);
    const [invoices, setInvoices] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [attempt, setAttempt] = useState(0);
    const [query, setQuery] = useState('');
    const base = `/main-contracts/${contractId}/invoices`;
    useEffect(() => {
        let active = true;
        const controller = new AbortController();
        setLoading(true); setError('');
        Promise.all([
            http.get(`/main-contracts/${contractId}`, { signal: controller.signal }),
            http.get(`/main-contractor-invoices/main-contract/${contractId}`, { signal: controller.signal }),
        ]).then(([contractResponse, invoiceResponse]) => {
            if (!active) return;
            if (String(contractResponse.data?.id) !== String(contractId)) throw new Error('Nie można odczytać umowy głównej.');
            const rows = readMainInvoices(invoiceResponse.data, contractId);
            setContract(contractResponse.data); setInvoices(rows);
        }).catch((failure) => { if (active) setError(failure.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [contractId, attempt]);
    const visible = invoices.filter((invoice) => invoice.invoiceNumber.toLocaleLowerCase('pl-PL').includes(query.trim().toLocaleLowerCase('pl-PL')));
    const totals = new Map();
    for (const invoice of invoices) {
        const currency = invoice.currency.toUpperCase();
        totals.set(currency, (totals.get(currency) ?? 0n) + parseAmendmentAmount(invoice.grossAmount).cents);
    }
    return <>
        <Link className="back-link" to={`/main-contracts/${contractId}`}>← Szczegóły umowy głównej</Link>
        <section className="card invoice-list-card">
            <div className="invoice-heading"><div><h1>Faktury głównego wykonawcy</h1>
                {contract && <p>Umowa {contract.contractNumber} · {contract.mainContractorName}</p>}
                <p>Otwórz rozliczenie faktury, aby sprawdzić powiązane faktury podwykonawców i dokumenty.</p>
            </div>{!loading && !error && <Link className="main-form-button main-form-button-primary" to={`${base}/new`}>Dodaj fakturę GW</Link>}</div>
            {loading ? <p role="status">Ładowanie faktur...</p> : error ? <div role="alert" className="error-message">{error}
                <button className="retry-button" onClick={() => setAttempt((value) => value + 1)}>Spróbuj ponownie</button></div> : <>
                {[...totals].map(([currency, total]) => <p key={currency} className="invoice-totals">Łączna wartość faktur GW: <strong>{formatInvoiceCents(total, currency)}</strong></p>)}
                {invoices.length === 0 ? <p className="invoice-empty">Brak faktur GW. Dodaj pierwszą fakturę, aby przygotować jej rozliczenie.</p> : <>
                    <div className="invoice-filters"><label>Numer faktury<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Szukaj numeru" /></label></div>
                    <p className="invoice-muted" role="status">Widoczne faktury: {visible.length} z {invoices.length}.</p>
                    {!visible.length && <p>Brak faktur pasujących do wyszukiwania.</p>}
                    <div className="invoice-list">{visible.map((invoice) => <article className="invoice-item" key={invoice.id}>
                        <div className="invoice-heading"><div><h2>{invoice.invoiceNumber}</h2>
                            <p>Wystawienie: {formatAmendmentDate(invoice.issueDate)} · termin płatności: {formatAmendmentDate(invoice.dueDate)}</p>
                            <strong>{formatInvoiceAmount(invoice.grossAmount, invoice.currency)} brutto</strong></div>
                            <div className="settlement-actions"><Link className="main-form-button main-form-button-primary" to={`${base}/${invoice.id}`}>Rozliczenie podwykonawców</Link>
                                <Link className="main-form-button" to={`${base}/${invoice.id}/edit`}>Edytuj fakturę GW</Link></div></div>
                    </article>)}</div>
                </>}
            </>}
        </section>
    </>;
}
