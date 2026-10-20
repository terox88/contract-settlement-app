import { changeSecurityType, emptyAdministration, formatAdministrationDate, isDocumentSecurity, warrantyEndDate } from './utils/contractAdministration';

export default function MainContractAdministrationFields({ value, contractDate, options, onChange, errors, disabled }) {
    const form = { ...emptyAdministration(), ...value };
    const document = options.securityTypes.find(option => option.value === form.securityType)?.document ?? isDocumentSecurity(form.securityType);
    const date = warrantyEndDate(form.actualCompletionDate, form.warrantyMonths);
    function change(name, value) {
        if (name === 'securityType') onChange(changeSecurityType(form, value));
        else onChange({ ...form, [name]: value, ...(name === 'securityValidityMode' && value !== 'TWO_STAGE'
            ? { retentionSecurityAmount: '', retentionSecurityValidUntil: '' } : {}) });
    }
    const field = (name, label, { type = 'text', choices, required = false, inputMode, min, max, step } = {}) => {
        const id = `contract-admin-${name}`, error = errors[`administration.${name}`];
        const props = { id, name: `administration.${name}`, value: form[name], required,
            'aria-invalid': Boolean(error), 'aria-describedby': error ? `${id}-error` : undefined,
            onChange: event => change(name, event.target.value) };
        return <div className="main-form-field" key={name}>
            <label htmlFor={id}>{label}{required ? ' *' : ''}</label>
            {choices ? <select {...props}><option value="">Nie uzupełniono</option>{choices.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</select>
                : <input {...props} type={type} inputMode={inputMode} min={min} max={max} step={step} />}
            {error && <p className="main-form-field-error" id={`${id}-error`} role="alert">{error}</p>}
        </div>;
    };
    return <fieldset className="main-form-section" disabled={disabled}>
        <legend>Zakończenie, gwarancja i zabezpieczenia</legend>
        <p className="main-form-hint">Dane opcjonalne. Faktyczne zakończenie może przypadać przed terminem umownym lub po nim. Okres gwarancji i rękojmi liczymy wspólnie od tej daty.</p>
        <div className="main-form-grid">
            {field('actualCompletionDate', 'Faktyczne zakończenie umowy', { type: 'date', min: contractDate || undefined })}
            {field('warrantyMonths', 'Gwarancja i rękojmia — miesiące', { type: 'number', inputMode: 'numeric', min: 0, max: 1200, step: 1 })}
        </div>
        <p className="main-form-hint">Koniec gwarancji i rękojmi: <strong>{date ? formatAdministrationDate(date) : 'Uzupełnij faktyczne zakończenie i liczbę miesięcy.'}</strong></p>
        <div className="main-form-grid">
            {field('ocInsuranceValidUntil', 'Ubezpieczenie OC — ważne do', { type: 'date' })}
            {field('carInsuranceValidUntil', 'Ubezpieczenie CAR — ważne do', { type: 'date' })}
            {field('securityType', 'Zabezpieczenie należytego wykonania', { choices: options.securityTypes })}
            {form.securityType && form.securityType !== 'NOT_ESTABLISHED' && field('securityAmount', 'Pełna kwota zabezpieczenia (PLN)', { inputMode: 'decimal' })}
            {document && <>
                {field('securityValidityMode', 'Wariant ważności dokumentu', { choices: options.validityModes, required: true })}
                {field('securityValidUntil', form.securityValidityMode === 'TWO_STAGE' ? 'Zabezpieczenie realizacji — ważne do' : 'Całe zabezpieczenie — ważne do', { type: 'date', required: true })}
                {form.securityValidityMode === 'TWO_STAGE' && <>
                    {field('retentionSecurityAmount', 'Zredukowana kwota na gwarancję i rękojmię (PLN)', { inputMode: 'decimal' })}
                    {field('retentionSecurityValidUntil', 'Zredukowane zabezpieczenie — ważne do', { type: 'date', required: true })}
                </>}
            </>}
        </div>
    </fieldset>;
}
