import { calculatedGross } from './utils/subcontractorInvoiceForm';

export default function InvoiceFields({ form, errors, change }) {
    const field = (name, label, options = {}) => <InvoiceField name={name} label={label} value={form[name]}
        onChange={(value) => change(name, value)} error={errors[name]} {...options} />;
    return <div className="main-form-grid">
        {field('invoiceNumber', 'Numer faktury', { required: true, maxLength: 255 })}
        {field('issueDate', 'Data wystawienia', { required: true, type: 'date' })}
        {field('saleDate', 'Data sprzedaży', { type: 'date' })}
        {field('dueDate', 'Termin płatności', { type: 'date' })}
        {field('netAmount', 'Kwota netto', { required: true, inputMode: 'decimal' })}
        {field('vatAmount', 'Kwota VAT', { required: true, inputMode: 'decimal' })}
        <InvoiceField name="grossAmount" label="Kwota brutto — netto + VAT" value={calculatedGross(form)?.amount.replace('.', ',') ?? ''} readOnly />
        {field('currency', 'Waluta', { required: true, maxLength: 3 })}
        {field('vatRates', 'Stawki VAT', { required: true, wide: true,
            hint: 'Wpisz stawki z faktury, rozdzielając je średnikami, np. 23; 8; ZW.' })}
        {field('description', 'Opis', { wide: true, multiline: true, maxLength: 1000 })}
    </div>;
}

export function InvoiceField({ name, label, value, onChange, error, required = false, type = 'text', inputMode,
    readOnly = false, multiline = false, wide = false, maxLength, hint }) {
    const id = `invoice-form-${name}`;
    const describedBy = [error ? `${id}-error` : '', hint ? `${id}-hint` : ''].filter(Boolean).join(' ') || undefined;
    const props = { id, name, value, required, readOnly, maxLength, inputMode,
        'aria-invalid': Boolean(error), 'aria-describedby': describedBy,
        onChange: onChange ? (event) => onChange(event.target.value) : undefined };
    return <div className={`main-form-field${wide ? ' main-form-field-wide' : ''}`}>
        <label htmlFor={id}>{label}{required ? ' *' : ''}</label>
        {multiline ? <textarea {...props} rows={3} /> : <input {...props} type={type} />}
        {hint && <p className="invoice-field-hint" id={`${id}-hint`}>{hint}</p>}
        {error && <p className="main-form-field-error" id={`${id}-error`}>{error}</p>}
    </div>;
}
