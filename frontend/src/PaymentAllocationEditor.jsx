import { useRef, useState } from 'react';
import { DraftDeductionWarning } from './DeductionReviewPanel';
import { allocationError, buildAllocationRequest, paymentAvailable } from './utils/paymentForm';
import { formatInvoiceAmount } from './utils/subcontractorInvoiceForm';

export default function PaymentAllocationEditor({ payment, target, row, disabled, onSave, onCancel }) {
    const [amount, setAmount] = useState(row ? String(row.allocatedAmount) : '');
    const [deducted, setDeducted] = useState(row ? String(row.deductedAmount) : '0');
    const [error, setError] = useState('');
    const amountRef = useRef(null);
    const maximum = paymentAvailable(payment, row);
    function save(event) {
        event.preventDefault();
        if (disabled) return;
        const validation = allocationError(amount, deducted, maximum, target.type);
        setError(validation);
        if (validation) { amountRef.current?.focus(); return; }
        onSave(buildAllocationRequest(payment.id, target, amount, deducted, maximum, row?.id));
    }
    const prefix = `allocation-editor-${row?.id ?? 'new'}`;
    return <form onSubmit={save} noValidate><fieldset className="settlement-fields" disabled={disabled}>
        <legend>{target.type === 'main' ? 'Faktura GW' : 'Faktura podwykonawcy'} {target.number}</legend>
        <label htmlFor={`${prefix}-paid`}>Z przelewu na tę fakturę *</label>
        <input id={`${prefix}-paid`} name="allocatedAmount" ref={amountRef} value={amount} inputMode="decimal" required
            aria-invalid={Boolean(error)} aria-describedby={error ? `${prefix}-error` : undefined}
            onChange={(event) => { setAmount(event.target.value); setError(''); }} />
        <label htmlFor={`${prefix}-deducted`}>Potrącenia przy tym rozliczeniu *</label>
        <input id={`${prefix}-deducted`} name="deductedAmount" value={deducted} inputMode="decimal" readOnly={target.type === 'main'} required
            onChange={(event) => { setDeducted(event.target.value); setError(''); }} />
        <p className="invoice-muted">Dostępne z przelewu: {formatInvoiceAmount(maximum, payment.currency)}. Potrącenie rozlicza część faktury i nie jest kwotą przelewu.</p>
        {target.type === 'main' && <p className="invoice-muted">Dla faktur GW potrącenia w tym module wynoszą 0.</p>}
        <DraftDeductionWarning target={target} amount={deducted} oldAmount={row?.deductedAmount ?? '0'} />
        {error && <p id={`${prefix}-error`} className="main-form-field-error" role="alert">{error}</p>}
        <div className="settlement-actions"><button className="main-form-button main-form-button-primary">{row ? 'Zapisz rozliczenie' : 'Przypisz przelew'}</button>
            {onCancel && <button type="button" className="main-form-button" onClick={onCancel}>Anuluj</button>}</div>
    </fieldset></form>;
}
