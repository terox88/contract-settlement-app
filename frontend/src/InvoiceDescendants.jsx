import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import http from './api/http';
import InvoiceDocuments from './InvoiceDocuments';
import AttachmentDisclosure from './AttachmentDisclosure';
import InvoiceAttachmentWorkspace from './InvoiceAttachmentWorkspace';
import { formatInvoiceAmount, readInvoice } from './utils/subcontractorInvoiceForm';

export default function InvoiceDescendants({ contractId, invoice, ancestors = [] }) {
    const [open, setOpen] = useState(false);
    return <details className="invoice-chain" onToggle={(event) => setOpen(event.currentTarget.open)}>
        <summary>Dalsi podwykonawcy faktury {invoice.invoiceNumber}</summary>
        {open && <Children contractId={contractId} invoice={invoice} ancestors={ancestors} />}
    </details>;
}

function Children({ contractId, invoice, ancestors }) {
    const [rows, setRows] = useState(null), [error, setError] = useState(''), [attempt, setAttempt] = useState(0);
    useEffect(() => {
        let active = true; const controller = new AbortController(); setError(''); setRows(null);
        async function load() {
            if (ancestors.map(String).includes(String(invoice.id))) throw new Error('Wykryto cykl rozliczeń faktur.');
            const { data } = await http.get(`/main-invoice-settlements/parent-invoice/${invoice.id}`, { signal: controller.signal });
            if (!Array.isArray(data)) throw new Error('Nieprawidłowa lista rozliczeń dalszych podwykonawców.');
            return Promise.all(data.map(async (assignment) => {
                if (String(assignment.parentSubcontractorInvoiceId) !== String(invoice.id)) throw new Error('Nieprawidłowe powiązanie faktury zleceniodawcy.');
                const { data: child } = await http.get(`/subcontractor-invoices/${assignment.subcontractorInvoiceId}`, { signal: controller.signal });
                readInvoice(child, assignment.subcontractId);
                if (String(child.id) !== String(assignment.subcontractorInvoiceId) || String(child.mainContractId) !== String(contractId)
                    || String(child.parentSubcontractId) !== String(invoice.subcontractId)) throw new Error('Nieprawidłowy poziom podwykonawstwa.');
                return { ...assignment, invoice: child };
            }));
        }
        load().then((data) => { if (active) setRows(data); }).catch((failure) => { if (active) setError(failure.message); });
        return () => { active = false; controller.abort(); };
    }, [contractId, invoice.id, attempt]);
    if (error) return <p className="error-message" role="alert">{error} <button onClick={() => setAttempt((value) => value + 1)}>Ponów</button></p>;
    if (!rows) return <p role="status">Ładowanie dalszych rozliczeń...</p>;
    return <div className="invoice-chain-children">
        {!rows.length && <p className="invoice-muted">Brak przypisanych faktur dalszych podwykonawców.</p>}
        {rows.map((row) => <article className="invoice-item" key={row.id}>
            <h3>{row.invoice.subcontractorName} · {row.invoice.invoiceNumber}</h3>
            <p>W tej fakturze zleceniodawcy: {formatInvoiceAmount(row.settledAmount, row.invoice.currency)}</p>
            <p>Cała faktura: zapłacono {formatInvoiceAmount(row.invoice.paidAmount, row.invoice.currency)} · potrącono {formatInvoiceAmount(row.invoice.deductedAmount, row.invoice.currency)} · saldo {formatInvoiceAmount(row.invoice.remainingAmount, row.invoice.currency)}</p>
            <InvoiceDocuments invoice={row.invoice} />
            <AttachmentDisclosure title="Pliki faktury, oświadczenie i potwierdzenia przelewów">
                <InvoiceAttachmentWorkspace invoice={row.invoice} onChange={() => setAttempt((value) => value + 1)} />
            </AttachmentDisclosure>
            <Link to={`/main-contracts/${contractId}/subcontracts/${row.invoice.subcontractId}/invoices/${row.invoice.id}/settlements`}>Otwórz rozliczenie tej faktury →</Link>
            <InvoiceDescendants contractId={contractId} invoice={row.invoice} ancestors={[...ancestors, invoice.id]} />
        </article>)}
    </div>;
}
