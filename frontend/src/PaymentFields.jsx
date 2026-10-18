import { InvoiceField } from './InvoiceFields';

export default function PaymentFields({ form, errors, change, currencyLocked = false }) {
    const field = (name, label, props = {}) => <InvoiceField name={name} label={label} value={form[name]}
        error={errors[name]} onChange={(value) => change(name, value)} {...props} />;
    return <div className="main-form-grid">
        {field('paymentDate', 'Data przelewu', { required: true, type: 'date' })}
        {field('amount', 'Kwota przelewu', { required: true, inputMode: 'decimal' })}
        {field('currency', 'Waluta', { required: true, maxLength: 3, readOnly: currencyLocked })}
        {field('description', 'Uwagi (opcjonalnie)', { maxLength: 1000, multiline: true, wide: true })}
    </div>;
}
