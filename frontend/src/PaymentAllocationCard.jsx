import { useState } from 'react';
import { Link } from 'react-router-dom';
import PaymentAllocationEditor from './PaymentAllocationEditor';
import { allocationInvoicePath, targetQuery } from './utils/paymentForm';
import { formatInvoiceAmount } from './utils/subcontractorInvoiceForm';
import { formatAmendmentDate } from './utils/contractAmendmentForm';

export default function PaymentAllocationCard({ row, payment: suppliedPayment, target: suppliedTarget, disabled, onMutate,
    showPaymentLink = true, showInvoiceLink = true }) {
    const [editing, setEditing] = useState(false);
    const [removing, setRemoving] = useState(false);
    const main = row.mainContractorInvoiceId != null;
    const target = suppliedTarget ?? { type: main ? 'main' : 'sub', id: main ? row.mainContractorInvoiceId : row.subcontractorInvoiceId,
        contractId: row.mainContractId, subcontractId: row.subcontractId, currency: row.paymentCurrency,
        number: main ? row.mainContractorInvoiceNumber : row.subcontractorInvoiceNumber };
    const payment = suppliedPayment ?? { id: row.paymentId, amount: row.paymentAmount, currency: row.paymentCurrency, unallocatedAmount: row.remainingPaymentAmount };
    const invoicePath = allocationInvoicePath(row);
    return <article className="invoice-item">
        <div className="invoice-heading"><div><h2>{main ? 'Faktura GW' : 'Faktura podwykonawcy'} {target.number}</h2>
            <p>{main ? row.mainContractorName : row.subcontractorName}</p>
            <p>Przelew z {formatAmendmentDate(row.paymentDate)}</p>
        </div><div className="settlement-actions"><button className="main-form-button" disabled={disabled} onClick={() => { setEditing(true); setRemoving(false); }}>Zmień rozliczenie</button>
            <button className="main-form-button" disabled={disabled} onClick={() => { setRemoving(true); setEditing(false); }}>Usuń przypisanie przelewu</button></div></div>
        {row.paymentDescription && <p className="invoice-description">{row.paymentDescription}</p>}
        <dl className="invoice-metrics invoice-meta">
            <Metric label="Z tego przelewu na fakturę" value={formatInvoiceAmount(row.allocatedAmount, row.paymentCurrency)} />
            <Metric label="Potrącono przy tym rozliczeniu" value={formatInvoiceAmount(row.deductedAmount, row.paymentCurrency)} />
            <Metric label="Pełna kwota przelewu" value={formatInvoiceAmount(row.paymentAmount, row.paymentCurrency)} />
            <Metric label="Z przelewu do dalszego przypisania" value={formatInvoiceAmount(row.remainingPaymentAmount, row.paymentCurrency)} />
        </dl>
        <p className="invoice-muted">Cała faktura: brutto {formatInvoiceAmount(row.invoiceGrossAmount, row.paymentCurrency)} · zapłacono {formatInvoiceAmount(row.invoicePaidAmount, row.paymentCurrency)}
            {' '}· potrącono {formatInvoiceAmount(row.invoiceDeductedAmount, row.paymentCurrency)} · saldo {formatInvoiceAmount(row.invoiceRemainingAmount, row.paymentCurrency)}.</p>
        {row.invoiceAmountExceeded && <p className="invoice-warning" role="status">Suma płatności i potrąceń przekracza wartość faktury. Sprawdź zapisane kwoty.</p>}
        {editing && <div className="settlement-panel"><PaymentAllocationEditor payment={payment} target={target} row={row} disabled={disabled}
            onCancel={() => setEditing(false)} onSave={(request) => onMutate(request, 'Zmieniono rozliczenie przelewu.')} /></div>}
        {removing && <div className="invoice-warning"><p>Usunąć tę kwotę płatności i potrącenia z rozliczenia faktury? Przelew pozostanie w systemie.</p>
            <div className="settlement-actions"><button className="main-form-button" disabled={disabled} onClick={() => onMutate({ method: 'delete', url: `/payment-allocations/${row.id}` }, 'Usunięto przypisanie przelewu.')}>Usuń rozliczenie</button>
                <button className="main-form-button" disabled={disabled} onClick={() => setRemoving(false)}>Anuluj</button></div></div>}
        {!disabled && <div className="payment-row-actions">
            {showPaymentLink && <Link to={`/payments/${row.paymentId}${target.contractId ? `?${targetQuery(target)}` : ''}`}>Szczegóły całego przelewu →</Link>}
            {showInvoiceLink && invoicePath && <Link to={invoicePath}>Wszystkie płatności tej faktury →</Link>}
        </div>}
    </article>;
}
function Metric({ label, value }) { return <div><dt>{label}</dt><dd>{value}</dd></div>; }
