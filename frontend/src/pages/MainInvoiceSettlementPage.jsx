import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import http from '../api/http';
import MainInvoiceReviewPanel from '../MainInvoiceReviewPanel';
import InvoiceDocuments from '../InvoiceDocuments';
import AttachmentDisclosure from '../AttachmentDisclosure';
import InvoiceAttachmentWorkspace from '../InvoiceAttachmentWorkspace';
import InvoiceDescendants from '../InvoiceDescendants';
import { formatAmendmentDate } from '../utils/contractAmendmentForm';
import { formatInvoiceAmount, formatInvoiceCents, getInvoiceDocumentItems } from '../utils/subcontractorInvoiceForm';
import { assignmentError, assignmentMaximum, availableInvoices, buildAssignmentRequest,
    loadSettlementWorkspace, settlementSummary } from '../utils/mainInvoiceSettlement';
import './MainContractFormPage.css';
import './MainInvoiceSettlementPage.css';

export default function MainInvoiceSettlementPage() {
    const { contractId, mainInvoiceId, subcontractId, invoiceId } = useParams();
    return <SettlementPage key={`${contractId}-${subcontractId ?? "main"}-${mainInvoiceId ?? invoiceId}`} contractId={contractId} mainInvoiceId={mainInvoiceId ?? invoiceId} parentSubcontractId={subcontractId} />;
}

function SettlementPage({ contractId, mainInvoiceId, parentSubcontractId }) {
    const [workspace, setWorkspace] = useState(null);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [attempt, setAttempt] = useState(0);
    const [saving, setSaving] = useState(false);
    const [saveError, setSaveError] = useState('');
    const [success, setSuccess] = useState('');
    const [editingId, setEditingId] = useState(null);
    const [removingId, setRemovingId] = useState(null);
    const [subcontractId, setSubcontractId] = useState('');
    const [query, setQuery] = useState('');
    const [onlyGaps, setOnlyGaps] = useState(false);
    const busyRef = useRef(false);
    const aliveRef = useRef(false);
    const base = parentSubcontractId ? `/main-contracts/${contractId}/subcontracts/${parentSubcontractId}/invoices` : `/main-contracts/${contractId}/invoices`;
    const parentType = parentSubcontractId ? 'sub' : 'main';
    useEffect(() => { aliveRef.current = true; return () => { aliveRef.current = false; }; }, []);
    useEffect(() => {
        let active = true;
        const controller = new AbortController();
        setLoading(true); setLoadError('');
        loadSettlementWorkspace(http, contractId, mainInvoiceId, controller.signal, parentSubcontractId).then((data) => {
            if (active) setWorkspace(data);
        }).catch((failure) => { if (active) setLoadError(failure.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [contractId, mainInvoiceId, parentSubcontractId, attempt]);

    async function mutate(request, message) {
        if (busyRef.current || loading || loadError) return;
        busyRef.current = true; setSaving(true); setSaveError(''); setSuccess('');
        try {
            await http[request.method](request.url, request.data);
            if (!aliveRef.current) return;
            setSuccess(message); setEditingId(null); setRemovingId(null);
            // Ponowny odczyt odświeża również kwoty dostępne do przypisania.
            setLoading(true); setAttempt((value) => value + 1);
        } catch (failure) { if (aliveRef.current) setSaveError(failure.message); }
        finally { busyRef.current = false; if (aliveRef.current) setSaving(false); }
    }

    async function refreshDocuments() {
        const data = await loadSettlementWorkspace(http, contractId, mainInvoiceId, undefined, parentSubcontractId);
        if (aliveRef.current) setWorkspace(data);
    }
    const summary = workspace ? settlementSummary(workspace) : null;
    const needle = query.trim().toLocaleLowerCase('pl-PL');
    const visible = workspace?.rows.filter(({ invoice }) =>
        `${invoice.invoiceNumber} ${invoice.subcontractorName} ${invoice.subcontractNumber}`.toLocaleLowerCase('pl-PL').includes(needle)
        && (!onlyGaps || getInvoiceDocumentItems(invoice).some((item) => ['missing', 'unknown'].includes(item.tone)))) ?? [];
    const main = workspace?.main;
    return <>
        <Link to={base} className="back-link">← Faktury zleceniodawcy</Link>
        <section className="card invoice-list-card">
            <div className="invoice-heading"><div><h1>Rozliczenie podwykonawców</h1>
                {main && <><h2>Faktura {main.invoiceNumber}</h2><p>{main.subcontractorName ?? main.mainContractorName} · umowa {main.mainContractNumber}</p></>}
            </div>{!loading && !loadError && !saving && <div className="settlement-actions">
                <Link to={`${base}/${mainInvoiceId}/payments`} className="main-form-button">Płatności faktury</Link>
                <Link to={`${base}/${mainInvoiceId}/edit`} className="main-form-button">Edytuj fakturę</Link></div>}</div>
            {success && <p className="settlement-success" role="status">{success}</p>}
            {saveError && <p className="error-message" role="alert">{saveError}</p>}
            {saving && <p role="status">Zapisywanie zmian...</p>}
            {loading ? <p role="status">Ładowanie rozliczenia...</p> : loadError ? <div className="error-message" role="alert">{loadError}
                <button className="retry-button" onClick={() => setAttempt((value) => value + 1)}>Odśwież dane</button></div> : workspace && <>
                <dl className="invoice-metrics invoice-meta">
                    <Metric label="Wartość brutto faktury zleceniodawcy" value={formatInvoiceAmount(main.grossAmount, main.currency)} />
                    <Metric label="Data wystawienia" value={formatAmendmentDate(main.issueDate)} />
                    <Metric label="Termin płatności" value={formatAmendmentDate(main.dueDate)} />
                    <Metric label="Stawki VAT" value={main.vatRates.join('; ')} />
                </dl>
                {!parentSubcontractId && <MainInvoiceReviewPanel invoiceId={mainInvoiceId} refreshKey={workspace} />}
                {main.description && <p className="invoice-description">{main.description}</p>}
                <AttachmentDisclosure title="Dokumenty faktury zleceniodawcy">
                    <InvoiceAttachmentWorkspace invoice={main} type={parentType} onChange={refreshDocuments} />
                </AttachmentDisclosure>
                <p className="invoice-muted">Podsumowanie obejmuje bezpośrednie faktury. Łańcuch dalszych rozliczeń znajdziesz przy każdej pozycji poniżej.</p>
                <section className="invoice-totals"><h2>Podwykonawcy w tej fakturze GW</h2><dl className="invoice-metrics">
                    <Metric label={`Kwota przypisana · ${workspace.rows.length} faktur`} value={summary.assigned == null ? 'Nie ustalono — różne waluty' : formatInvoiceCents(summary.assigned, main.currency)} />
                    <Metric label="Faktury z saldem do rozliczenia" value={summary.unpaid} />
                    <Metric label="Faktury z brakami dokumentów" value={summary.documentGaps} />
                    <Metric label="Faktury z rozbieżnościami" value={summary.inconsistencies} />
                </dl><p className="invoice-muted">Kwota przypisana wskazuje zakres tej faktury zleceniodawcy. Zapłata, potrącenia i dokumenty dotyczą całych faktur podwykonawców.</p></section>
                {summary.exceedsMainInvoice && <p className="invoice-warning" role="status">Suma przypisań przekracza wartość faktury zleceniodawcy. Sprawdź zakres rozliczenia.</p>}
                {!workspace.rows.length && <p className="invoice-warning">Nie przypisano faktur podwykonawców. Sprawdź, czy są wymagane w tym rozliczeniu.</p>}

                <div className="settlement-add-grid">
                    <section className="settlement-panel"><h2>Przypisz istniejącą fakturę</h2>
                        <AssignmentEditor key={`new-${attempt}`} workspace={workspace} disabled={saving}
                            onSave={(request) => mutate(request, 'Przypisano fakturę podwykonawcy.')} />
                    </section>
                    <section className="settlement-panel"><h2>Dodaj nową fakturę i przypisz</h2>
                        <p className="invoice-muted">Wybierz umowę podwykonawczą. W formularzu określisz dane faktury i kwotę przypisania.</p>
                        {workspace.subcontracts.length ? <><label htmlFor="new-invoice-subcontract">Umowa podwykonawcza</label>
                            <select id="new-invoice-subcontract" value={subcontractId} disabled={saving} onChange={(event) => setSubcontractId(event.target.value)}>
                                <option value="">Wybierz umowę</option>{workspace.subcontracts.map((sub) => <option key={sub.id} value={sub.id}>
                                    {sub.subcontractorName} · {sub.contractNumber}</option>)}
                            </select>{subcontractId && !saving && <Link className="main-form-button" to={`/main-contracts/${contractId}/subcontracts/${subcontractId}/invoices/new?${parentSubcontractId ? `parentInvoiceId=${mainInvoiceId}&parentSubcontractId=${parentSubcontractId}` : `mainInvoiceId=${mainInvoiceId}`}`}>
                                Dodaj fakturę i przypisz</Link>}</> : <p>Najpierw dodaj umowę podwykonawczą w widoku umowy głównej.</p>}
                    </section>
                </div>

                <div className="invoice-filters"><label>Podwykonawca, faktura lub umowa<input value={query} onChange={(event) => setQuery(event.target.value)} /></label>
                    <label className="invoice-filter-check"><input type="checkbox" checked={onlyGaps} onChange={(event) => setOnlyGaps(event.target.checked)} /> Braki w dokumentach</label>
                    <button className="main-form-button" disabled={saving} onClick={() => { setSaveError(''); setAttempt((value) => value + 1); }}>Odśwież rozliczenie</button>
                </div>
                <p className="invoice-muted" role="status">Widoczne przypisania: {visible.length} z {workspace.rows.length}.</p>
                {workspace.rows.length > 0 && !visible.length && <p>Brak przypisań pasujących do filtrów.</p>}
                <div className="invoice-list">{visible.map((row) => {
                    const invoice = row.invoice;
                    return <article className="invoice-item" key={row.id}>
                        <div className="invoice-heading"><div><h2>{invoice.subcontractorName} · {invoice.invoiceNumber}</h2>
                            <p>Umowa {invoice.subcontractNumber} · NIP {invoice.subcontractorTaxId}</p>
                            <span className={`invoice-settlement-badge ${invoice.amountExceeded ? 'invoice-badge-warning' : invoice.settled ? 'invoice-badge-settled' : ''}`}>
                                {invoice.amountExceeded ? 'Przekroczona kwota rozliczenia' : invoice.settled ? 'Cała faktura rozliczona' : 'Faktura z saldem do rozliczenia'}</span>
                        </div><div className="settlement-actions">{!saving && <Link className="main-form-button main-form-button-primary"
                            to={`/main-contracts/${contractId}/subcontracts/${invoice.subcontractId}/invoices/${invoice.id}/payments${parentType === 'main' ? `?returnMainInvoiceId=${mainInvoiceId}` : ''}`}>Płatności i potrącenia</Link>}
                            <button className="main-form-button" disabled={saving} onClick={() => { setEditingId(row.id); setRemovingId(null); }}>Zmień przypisaną kwotę</button>
                            <button className="main-form-button" disabled={saving} onClick={() => { setRemovingId(row.id); setEditingId(null); }}>Usuń przypisanie</button></div></div>
                        <dl className="invoice-metrics invoice-meta">
                            <Metric label="Przypisano do tej faktury zleceniodawcy" value={formatInvoiceAmount(row.settledAmount, main.currency)} />
                            <Metric label="Pełna wartość faktury podwykonawcy" value={formatInvoiceAmount(invoice.grossAmount, invoice.currency)} />
                            <Metric label="Przypisano łącznie do faktur zleceniodawcy" value={formatInvoiceAmount(invoice.assignedToMainInvoicesAmount, invoice.currency)} />
                            <Metric label="Pozostało do przypisania" value={formatInvoiceAmount(invoice.remainingToAssignToMainInvoices, invoice.currency)} />
                        </dl>
                        <div className="invoice-finances"><h3>Rozliczenie całej faktury podwykonawcy</h3><dl className="invoice-metrics">
                            <Metric label="Zapłacono" value={formatInvoiceAmount(invoice.paidAmount, invoice.currency)} />
                            <Metric label="Potrącono" value={formatInvoiceAmount(invoice.deductedAmount, invoice.currency)} />
                            <Metric label="Saldo do rozliczenia" value={formatInvoiceAmount(invoice.remainingAmount, invoice.currency)} />
                            <Metric label="Termin płatności" value={formatAmendmentDate(invoice.dueDate)} />
                        </dl></div>
                        {invoice.statementSettlementMismatch && <p className="invoice-warning">Oświadczenie potwierdza pełne rozliczenie, ale płatności i potrącenia go nie potwierdzają.</p>}
                        {invoice.currency.toUpperCase() !== main.currency.toUpperCase() && <p className="invoice-warning">Waluta tej faktury różni się od waluty faktury zleceniodawcy. Popraw przypisanie.</p>}
                        {invoice.hasStatement && <p className="invoice-statement-note invoice-muted">Oświadczenie z {formatAmendmentDate(invoice.statementDate)}{invoice.statementRemarks ? ` · ${invoice.statementRemarks}` : ''}</p>}
                        <InvoiceDocuments invoice={invoice} />
                        <AttachmentDisclosure title="Pliki faktury, oświadczenie i potwierdzenia przelewów">
                            <InvoiceAttachmentWorkspace invoice={invoice} onChange={refreshDocuments} />
                        </AttachmentDisclosure>
                        <Link className="main-form-button" to={`/main-contracts/${contractId}/subcontracts/${invoice.subcontractId}/invoices/${invoice.id}/settlements`}>Rozliczenie dalszych podwykonawców</Link>
                        <InvoiceDescendants key={`${invoice.id}-${attempt}`} contractId={contractId} invoice={invoice} />
                        {editingId === row.id && <div className="settlement-panel"><h3>Zmiana kwoty przypisania</h3><AssignmentEditor key={row.id} workspace={workspace} row={row} disabled={saving}
                            onCancel={() => setEditingId(null)} onSave={(request) => mutate(request, 'Zmieniono kwotę przypisania.')} /></div>}
                        {removingId === row.id && <div className="invoice-warning"><p>Usunąć powiązanie faktury {invoice.invoiceNumber} z tą fakturą GW? Sama faktura, płatności i dokumenty pozostaną w systemie.</p>
                            <div className="settlement-actions"><button className="main-form-button" disabled={saving} onClick={() => mutate({ method: 'delete', url: `/main-invoice-settlements/${row.id}` }, 'Usunięto przypisanie.')}>Usuń powiązanie</button>
                                <button className="main-form-button" disabled={saving} onClick={() => setRemovingId(null)}>Anuluj</button></div></div>}
                        {!saving && <Link className="settlement-detail-link" to={`/main-contracts/${contractId}/subcontracts/${invoice.subcontractId}/invoices`}>Wszystkie faktury tej umowy podwykonawczej →</Link>}
                    </article>;
                })}</div>
            </>}
        </section>
    </>;
}

function AssignmentEditor({ workspace, row, disabled, onSave, onCancel }) {
    const [query, setQuery] = useState('');
    const [selectedId, setSelectedId] = useState(row ? String(row.invoice.id) : '');
    const [amount, setAmount] = useState(row ? String(row.settledAmount) : '');
    const [error, setError] = useState('');
    const amountRef = useRef(null);
    const candidates = availableInvoices(workspace, query);
    const selected = row?.invoice ?? candidates.find((invoice) => String(invoice.id) === selectedId);
    const maximum = row ? assignmentMaximum(row) : selected?.remainingToAssignToMainInvoices;
    function save(event) {
        event.preventDefault();
        if (disabled) return;
        const validation = !selected ? 'Wybierz fakturę podwykonawcy.' : assignmentError(amount, maximum);
        setError(validation);
        if (validation) { amountRef.current?.focus(); return; }
        onSave(buildAssignmentRequest(workspace.main.id, selected.id, amount, maximum, row?.id, workspace.parentType));
    }
    const prefix = row ? `assignment-${row.id}` : 'new-assignment';
    return <form onSubmit={save} noValidate><fieldset className="settlement-fields" disabled={disabled}>
        <legend className="settlement-sr-only">{row ? 'Zmień kwotę' : 'Wybierz fakturę i kwotę'}</legend>
        {!row && <><label htmlFor={`${prefix}-search`}>Szukaj faktury, wykonawcy lub NIP</label>
            <input id={`${prefix}-search`} value={query} onChange={(event) => { setQuery(event.target.value); setSelectedId(''); setAmount(''); setError(''); }} />
            <label htmlFor={`${prefix}-invoice`}>Faktura podwykonawcy *</label>
            <select id={`${prefix}-invoice`} required value={selectedId} onChange={(event) => {
                const id = event.target.value; setSelectedId(id); setError('');
                setAmount(String(candidates.find((invoice) => String(invoice.id) === id)?.remainingToAssignToMainInvoices ?? ''));
            }}><option value="">Wybierz fakturę</option>{candidates.map((invoice) => <option key={invoice.id} value={invoice.id}>
                {invoice.subcontractorName} · {invoice.invoiceNumber} · dostępne {formatInvoiceAmount(invoice.remainingToAssignToMainInvoices, invoice.currency)}</option>)}</select>
            {!candidates.length && <p className="invoice-muted">Brak pasujących faktur z dostępną kwotą w walucie {workspace.main.currency}. Faktury już przypisane zmienisz na liście poniżej.</p>}
        </>}
        <label htmlFor={`${prefix}-amount`}>Kwota przypisana do tej faktury zleceniodawcy *</label>
        <input ref={amountRef} id={`${prefix}-amount`} name="settledAmount" inputMode="decimal" required value={amount}
            aria-invalid={Boolean(error)} aria-describedby={`${prefix}-hint${error ? ` ${prefix}-error` : ''}`}
            onChange={(event) => { setAmount(event.target.value); setError(''); }} />
        <p id={`${prefix}-hint`} className="invoice-muted">Dostępna kwota: {formatInvoiceAmount(maximum, workspace.main.currency)}. Przypisanie nie rejestruje zapłaty.</p>
        {error && <p id={`${prefix}-error`} className="main-form-field-error" role="alert">{error}</p>}
        <div className="settlement-actions"><button className="main-form-button main-form-button-primary" disabled={!selected || disabled}>{row ? 'Zapisz kwotę' : 'Przypisz fakturę'}</button>
            {onCancel && <button type="button" className="main-form-button" onClick={onCancel}>Anuluj</button>}</div>
    </fieldset></form>;
}

function Metric({ label, value }) { return <div><dt>{label}</dt><dd>{value}</dd></div>; }
