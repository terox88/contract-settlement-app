import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import http from '../api/http';
import { formatBudgetCents } from '../utils/budgetForm';
import { validDate } from '../utils/mainContractForm';
import {
    amendmentToForm, buildAmendmentRequest, emptyAmendment, formatAmendmentDate,
    getAmendmentPreview, readAmendmentList, validateAmendmentForm,
} from '../utils/contractAmendmentForm';
import './MainContractFormPage.css';
import './ContractAmendmentFormPage.css';

export default function ContractAmendmentFormPage() {
    const { contractId, amendmentId, subcontractId } = useParams();
    return <ContractAmendmentForm key={`${contractId}-${subcontractId ?? "main"}-${amendmentId ?? 'new'}`}
        contractId={subcontractId ?? contractId} amendmentId={amendmentId} rootId={contractId} subcontractId={subcontractId} />;
}

function ContractAmendmentForm({ contractId, amendmentId, rootId, subcontractId }) {
    const navigate = useNavigate();
    const isEditing = Boolean(amendmentId);
    const returnPath = subcontractId ? `/main-contracts/${rootId}/subcontracts/${subcontractId}` : `/main-contracts/${contractId}`;
    const [contract, setContract] = useState(null);
    const [amendments, setAmendments] = useState([]);
    const [form, setForm] = useState(emptyAmendment);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [loadAttempt, setLoadAttempt] = useState(0);
    const [errors, setErrors] = useState({});
    const [saveError, setSaveError] = useState('');
    const [saving, setSaving] = useState(false);
    const formRef = useRef(null);
    const savingRef = useRef(false);
    const mountedRef = useRef(false);

    useEffect(() => {
        mountedRef.current = true;
        return () => { mountedRef.current = false; };
    }, []);

    useEffect(() => {
        let active = true;
        const controller = new AbortController();
        setLoading(true);
        setLoadError('');
        Promise.all([
            http.get(subcontractId ? `/subcontracts/${contractId}` : `/main-contracts/${contractId}`, { signal: controller.signal }),
            http.get(`/contract-amendments/contract/${contractId}`, { signal: controller.signal }),
        ])
            .then(([contractResponse, amendmentsResponse]) => {
                if (!active) return;
                const data = contractResponse.data;
                if (!data?.id || String(data.id) !== String(contractId) || !validDate(data.contractDate) || (subcontractId && String(data.mainContractId) !== String(rootId))) {
                    throw new Error('Nie można odczytać danych tej umowy. Spróbuj ponownie.');
                }
                const rows = readAmendmentList(amendmentsResponse.data, contractId);
                const edited = isEditing ? rows.find((entry) => String(entry.id) === String(amendmentId)) : null;
                if (isEditing && !edited) throw new Error('Nie znaleziono aneksu przypisanego do tej umowy.');
                setContract(data);
                setAmendments(rows);
                if (edited) setForm(amendmentToForm(edited));
            })
            .catch((error) => { if (active) setLoadError(error.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [contractId, rootId, subcontractId, amendmentId, isEditing, loadAttempt]);

    function changeField(name, value) {
        setForm((current) => ({ ...current, [name]: value }));
        setErrors((current) => ({ ...current, [name]: '' }));
        setSaveError('');
    }

    async function handleSubmit(event) {
        event.preventDefault();
        if (savingRef.current || loading || loadError || !contract) return;
        const validation = validateAmendmentForm(form, contract, amendments, amendmentId);
        setErrors(validation);
        setSaveError('');
        const firstInvalid = Object.keys(validation)[0];
        if (firstInvalid) {
            formRef.current?.elements.namedItem(firstInvalid)?.focus();
            return;
        }

        savingRef.current = true;
        setSaving(true);
        try {
            const request = buildAmendmentRequest(contractId, amendmentId, form);
            await http[request.method](request.url, request.data);
            // Powrót montuje ponownie szczegóły, listę aneksów i limity finansowe.
            if (mountedRef.current) navigate(returnPath, { replace: true });
        } catch (error) {
            if (mountedRef.current) setSaveError(error.message);
        } finally {
            savingRef.current = false;
            if (mountedRef.current) setSaving(false);
        }
    }

    const preview = contract ? getAmendmentPreview(form, contract, amendments, amendmentId) : null;
    const field = (name, label, options = {}) => <AmendmentField name={name} label={label}
        value={form[name]} error={errors[name]} onChange={(value) => changeField(name, value)} {...options} />;

    return (
        <>
            {saving ? <span className="back-link" aria-disabled="true">← Szczegóły umowy</span>
                : <Link to={returnPath} className="back-link">← Szczegóły umowy</Link>}
            <section className="card main-form-card">
                <div className="main-form-heading">
                    <h1>{isEditing ? 'Edytuj aneks' : 'Dodaj aneks'}</h1>
                    {contract && <p>Umowa {contract.contractNumber} · {contract.subcontractorName ?? contract.mainContractorName}</p>}
                    <p>Pola oznaczone * są wymagane.</p>
                </div>

                {loading ? <div className="loading-message" role="status">Ładowanie umowy i aneksów...</div>
                    : loadError ? <div className="error-message" role="alert">
                        {loadError}
                        <button type="button" className="retry-button"
                            onClick={() => setLoadAttempt((value) => value + 1)}>Spróbuj ponownie</button>
                    </div> : (
                        <form ref={formRef} onSubmit={handleSubmit} noValidate>
                            {saveError && <div className="error-message main-form-alert" role="alert">{saveError}</div>}
                            <fieldset className="main-form-section" disabled={saving}>
                                <legend>Dane aneksu</legend>
                                <div className="main-form-grid">
                                    {field('amendmentNumber', 'Numer aneksu', { required: true, maxLength: 255 })}
                                    {field('amendmentDate', 'Data aneksu', { required: true, type: 'date' })}
                                    {field('effectiveDate', 'Data obowiązywania', { required: true, type: 'date',
                                        hint: 'Każdy aneks tej umowy musi mieć inną datę obowiązywania.' })}
                                    {field('valueChange', 'Zmiana wartości brutto (PLN)', { inputMode: 'decimal',
                                        hint: 'Podaj różnicę, np. 5000 lub -2000. Puste pole oznacza brak zmiany wartości.' })}
                                    {field('newEndDate', 'Nowy termin zakończenia', { type: 'date',
                                        hint: 'Puste pole oznacza, że ten aneks nie zmienia terminu.' })}
                                    {field('description', 'Opis zmian', { multiline: true, wide: true, maxLength: 2000 })}
                                </div>
                            </fieldset>

                            {preview && <section className="amendment-preview" aria-label="Podgląd wpływu aneksu" aria-live="polite">
                                <h2>Wpływ na umowę</h2>
                                <dl>
                                    <div><dt>Obecna wartość brutto</dt><dd>{formatBudgetCents(preview.currentValueCents)}</dd></div>
                                    <div><dt>Wartość brutto po zapisie</dt><dd className={preview.negativeAtDate ? 'value-warning' : ''}>
                                        {formatBudgetCents(preview.proposedValueCents)}</dd></div>
                                    <div><dt>Obecny termin zakończenia</dt><dd>{formatAmendmentDate(preview.currentEndDate)}</dd></div>
                                    <div><dt>Termin po zapisie</dt><dd>{formatAmendmentDate(preview.proposedEndDate)}</dd></div>
                                </dl>
                                <p>Podgląd uwzględnia wszystkie aneksy w kolejności dat obowiązywania.
                                    Po zmianie wartości sprawdź podział limitów finansowych.</p>
                            </section>}

                            <div className="main-form-footer">
                                <button type="submit" className="main-form-button main-form-button-primary" disabled={saving}>
                                    {saving ? 'Zapisywanie...' : isEditing ? 'Zapisz zmiany' : 'Dodaj aneks'}
                                </button>
                                <button type="button" className="main-form-button" disabled={saving}
                                    onClick={() => navigate(returnPath)}>Anuluj</button>
                                <span className="main-form-hint" role="status">{saving ? 'Trwa zapisywanie aneksu.' : ''}</span>
                            </div>
                        </form>
                    )}
            </section>
        </>
    );
}

function AmendmentField({ name, label, value, onChange, error, hint, required = false,
    type = 'text', inputMode, multiline = false, wide = false, maxLength }) {
    const id = `amendment-form-${name}`;
    const description = [hint ? `${id}-hint` : null, error ? `${id}-error` : null].filter(Boolean).join(' ');
    const props = { id, name, value, required, maxLength, inputMode,
        'aria-invalid': Boolean(error), 'aria-describedby': description || undefined,
        onChange: (event) => onChange(event.target.value) };
    return (
        <div className={`main-form-field${wide ? ' main-form-field-wide' : ''}`}>
            <label htmlFor={id}>{label}{required ? ' *' : ''}</label>
            {multiline ? <textarea {...props} rows={4} /> : <input {...props} type={type} />}
            {hint && <p id={`${id}-hint`} className="amendment-field-hint">{hint}</p>}
            {error && <p id={`${id}-error`} className="main-form-field-error">{error}</p>}
        </div>
    );
}
