import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import http from '../api/http';
import InvoiceDocuments from '../InvoiceDocuments';
import { formatAmendmentDate } from '../utils/contractAmendmentForm';
import { filterInvoices, formatInvoiceAmount, formatInvoiceCents, invoiceTotals, readInvoices } from '../utils/subcontractorInvoiceForm';
import './MainContractFormPage.css';
import './SubcontractorInvoicesPage.css';

export default function SubcontractorInvoicesPage() {
    const { contractId, subcontractId } = useParams();
    return <InvoiceList key={`${contractId}-${subcontractId}`} contractId={contractId} subcontractId={subcontractId} />;
}

function InvoiceList({ contractId, subcontractId }) {
    const [subcontract, setSubcontract] = useState(null);
    const [invoices, setInvoices] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [attempt, setAttempt] = useState(0);
    const [query, setQuery] = useState('');
    const [settlement, setSettlement] = useState('all');
    const [onlyDocumentGaps, setOnlyDocumentGaps] = useState(false);
    const base = `/main-contracts/${contractId}/subcontracts/${subcontractId}/invoices`;

    useEffect(() => {
        let active = true;
        const controller = new AbortController();
        setLoading(true);
        setError('');
        Promise.all([
            http.get(`/subcontracts/${subcontractId}`, { signal: controller.signal }),
            http.get('/subcontractor-invoices', { params: { subcontractId }, signal: controller.signal }),
        ]).then(([subResponse, invoiceResponse]) => {
            if (!active) return;
            const sub = subResponse.data;
            if (!sub?.id || String(sub.id) !== String(subcontractId) || String(sub.mainContractId) !== String(contractId)) {
                throw new Error('Nie można odczytać umowy podwykonawczej przypisanej do tej umowy głównej.');
            }
            const rows = readInvoices(invoiceResponse.data, subcontractId);
            setSubcontract(sub);
            setInvoices(rows);
        }).catch((failure) => { if (active) setError(failure.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [contractId, subcontractId, attempt]);

    const visible = filterInvoices(invoices, query, settlement, onlyDocumentGaps);
    const totals = invoiceTotals(invoices);
    return <>
        <Link to={`/main-contracts/${contractId}`} className="back-link">← Szczegóły umowy głównej</Link>
        <section className="card invoice-list-card">
            <div className="invoice-heading">
                <div><h1>Faktury podwykonawcy</h1>
                    {subcontract && <><p>{subcontract.subcontractorName} · NIP {subcontract.subcontractorTaxId}</p>
                        <p>Umowa {subcontract.contractNumber} · umowa główna {subcontract.mainContractNumber}</p></>}
                </div>
                {!loading && !error && <Link to={`${base}/new`} className="main-form-button main-form-button-primary">Dodaj fakturę</Link>}
            </div>
            {loading ? <p className="loading-message" role="status">Ładowanie faktur...</p>
                : error ? <div className="error-message" role="alert">{error}
                    <button type="button" className="retry-button" onClick={() => setAttempt((value) => value + 1)}>Spróbuj ponownie</button>
                </div> : <>
                    {totals.map((total) => <section key={total.currency} className="invoice-totals" aria-label={`Podsumowanie faktur w ${total.currency}`}>
                        <h2>Wszystkie faktury · {total.currency}</h2>
                        <dl className="invoice-metrics">
                            <Metric label="Wartość brutto" value={formatInvoiceCents(total.grossAmount, total.currency)} />
                            <Metric label="Zapłacono" value={formatInvoiceCents(total.paidAmount, total.currency)} />
                            <Metric label="Potrącono" value={formatInvoiceCents(total.deductedAmount, total.currency)} />
                            <Metric label="Saldo do rozliczenia" value={formatInvoiceCents(total.remainingAmount, total.currency)} />
                        </dl>
                    </section>)}
                    {invoices.length === 0 ? <div className="invoice-empty"><h2>Brak faktur tej umowy</h2>
                        <p>Dodaj pierwszą fakturę. Przy każdej zobaczysz rozliczenia i stan powiązanych dokumentów.</p></div> : <>
                        <div className="invoice-filters">
                            <label>Numer faktury<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Szukaj numeru" /></label>
                            <label>Rozliczenie<select value={settlement} onChange={(event) => setSettlement(event.target.value)}>
                                <option value="all">Wszystkie</option><option value="unsettled">Do rozliczenia</option><option value="settled">Rozliczone</option>
                            </select></label>
                            <label className="invoice-filter-check"><input type="checkbox" checked={onlyDocumentGaps}
                                onChange={(event) => setOnlyDocumentGaps(event.target.checked)} /> Braki w dokumentach</label>
                        </div>
                        <p className="invoice-muted" role="status">Widoczne faktury: {visible.length} z {invoices.length}.</p>
                        {visible.length === 0 && <p className="invoice-empty">Brak faktur pasujących do wybranych filtrów.</p>}
                        <div className="invoice-list">
                            {visible.map((invoice) => <article key={invoice.id} className="invoice-item">
                                <div className="invoice-heading">
                                    <div><h2>{invoice.invoiceNumber}</h2>
                                        <span className={`invoice-settlement-badge ${invoice.amountExceeded ? 'invoice-badge-warning' : invoice.settled ? 'invoice-badge-settled' : ''}`}>
                                            {invoice.amountExceeded ? 'Kwota rozliczenia przekroczona' : invoice.settled ? 'Rozliczona' : 'Do rozliczenia'}
                                        </span></div>
                                    {invoice.editable === true ? <Link to={`${base}/${invoice.id}/edit`} className="main-form-button">Edytuj fakturę</Link>
                                        : <span className="invoice-edit-blocked">{invoice.editBlockedReason || 'Edycja niedostępna.'}</span>}
                                </div>
                                {invoice.description && <p className="invoice-description">{invoice.description}</p>}
                                <dl className="invoice-metrics invoice-meta">
                                    <Metric label="Wystawienie" value={formatAmendmentDate(invoice.issueDate)} />
                                    <Metric label="Sprzedaż" value={formatAmendmentDate(invoice.saleDate)} />
                                    <Metric label="Termin płatności" value={formatAmendmentDate(invoice.dueDate)} />
                                    <Metric label="Stawki VAT" value={invoice.vatRates.join('; ') || '—'} />
                                    <Metric label="Netto" value={formatInvoiceAmount(invoice.netAmount, invoice.currency)} />
                                    <Metric label="VAT" value={formatInvoiceAmount(invoice.vatAmount, invoice.currency)} />
                                    <Metric label="Brutto" value={formatInvoiceAmount(invoice.grossAmount, invoice.currency)} />
                                    <Metric label="W rozliczeniach faktur GW" value={formatInvoiceAmount(invoice.assignedToMainInvoicesAmount, invoice.currency)} />
                                </dl>
                                <dl className="invoice-metrics invoice-finances">
                                    <Metric label="Zapłacono" value={formatInvoiceAmount(invoice.paidAmount, invoice.currency)} />
                                    <Metric label="Potrącono" value={formatInvoiceAmount(invoice.deductedAmount, invoice.currency)} />
                                    <Metric label="Saldo do rozliczenia" value={formatInvoiceAmount(invoice.remainingAmount, invoice.currency)} />
                                    <Metric label="Do przypisania do faktur GW" value={formatInvoiceAmount(invoice.remainingToAssignToMainInvoices, invoice.currency)} />
                                </dl>
                                {invoice.statementSettlementMismatch && <p className="invoice-warning" role="status">
                                    Oświadczenie potwierdza pełne rozliczenie, ale zapisane płatności i potrącenia go nie potwierdzają.
                                </p>}
                                {invoice.hasStatement && <p className="invoice-muted invoice-statement-note">
                                    Oświadczenie z {formatAmendmentDate(invoice.statementDate)}{invoice.statementRemarks ? ` · ${invoice.statementRemarks}` : ''}
                                </p>}
                                <InvoiceDocuments invoice={invoice} />
                            </article>)}
                        </div>
                    </>}
                </>}
        </section>
    </>;
}

function Metric({ label, value }) {
    return <div><dt>{label}</dt><dd>{value}</dd></div>;
}
