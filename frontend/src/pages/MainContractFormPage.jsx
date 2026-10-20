import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import http from '../api/http';
import MainContractAdministrationFields from '../MainContractAdministrationFields';
import { readAdministrationOptions } from '../utils/contractAdministration';
import InitialFinancialLimitsFields from '../InitialFinancialLimitsFields';
import { isMissingBudget } from '../utils/budgetForm';
import { validateInitialFinancialLimits } from '../utils/financialLimitForm';
import {
    buildMainContractPayload, buildMainContractUpdatePayload, contractToForm,
    CONTRACTOR_FIELDS,
    emptyContract, emptyContractor, normalizeTaxId, REMUNERATION_TYPES,
    validateContractData, validateMainContractForm,
} from '../utils/mainContractForm';
import { readContractTypeOptions } from '../utils/contractTypes';
import './MainContractFormPage.css';

export default function MainContractFormPage() {
    const { taskId, contractId } = useParams();
    return <MainContractForm key={contractId ? `edit-${contractId}` : `new-${taskId}`}
        taskId={taskId} contractId={contractId} />;
}

function MainContractForm({ taskId, contractId }) {
    const navigate = useNavigate();
    const isEditing = Boolean(contractId);
    const [task, setTask] = useState(null);
    const [loadedContract, setLoadedContract] = useState(null);
    const [contractTypes, setContractTypes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [loadAttempt, setLoadAttempt] = useState(0);
    const [contract, setContract] = useState(emptyContract);
    const [contractor, setContractor] = useState(emptyContractor);
    const [nip, setNip] = useState('');
    const [lookup, setLookup] = useState({ status: 'idle', nip: '', contractor: null });
    const [lookupAttempt, setLookupAttempt] = useState(0);
    const [selected, setSelected] = useState(null);
    const [errors, setErrors] = useState({});
    const [saveError, setSaveError] = useState('');
    const [saving, setSaving] = useState(false);
    const [budget, setBudget] = useState(null);
    const [initialLimitRows, setInitialLimitRows] = useState(null);
    const [administrationOptions, setAdministrationOptions] = useState({ securityTypes: [], validityModes: [] });
    const limitRows = initialLimitRows ?? [{ year: contract.contractDate.slice(0, 4), amount: contract.initialValue }];
    const formRef = useRef(null);
    const savingRef = useRef(false);
    const mountedRef = useRef(false);
    const taxId = normalizeTaxId(nip);
    const checking = !isEditing && (lookup.status === 'pending' || lookup.status === 'checking');
    const returnPath = isEditing ? `/main-contracts/${contractId}` : `/tasks/${taskId}`;
    const returnLabel = isEditing ? 'Szczegóły umowy' : 'Szczegóły zadania';

    useEffect(() => {
        mountedRef.current = true;
        return () => { mountedRef.current = false; };
    }, []);

    useEffect(() => {
        let active = true;
        const controller = new AbortController();
        setLoading(true);
        setLoadError('');
        const path = isEditing ? `/main-contracts/${contractId}` : `/investment-tasks/${taskId}`;
        Promise.all([
            http.get(path, { signal: controller.signal }),
            http.get('/contract-types', { signal: controller.signal }),
            isEditing ? Promise.resolve(null) : http.get(`/budgets/investment-task/${taskId}`, { signal: controller.signal })
                .catch((error) => { if (isMissingBudget(error, taskId)) return { data: null }; throw error; }),
            http.get('/main-contract-administration/options', { signal: controller.signal }),
        ])
            .then(([response, typesResponse, budgetResponse, administrationResponse]) => {
                if (!active) return;
                const options = readContractTypeOptions(typesResponse.data);
                setContractTypes(options);
                setAdministrationOptions(readAdministrationOptions(administrationResponse.data));
                if (isEditing) {
                    setLoadedContract(response.data);
                    setContract(contractToForm(response.data));
                } else {
                    if (String(response.data?.id) !== String(taskId)) throw new Error('Nie można potwierdzić zadania.');
                    const data = budgetResponse.data;
                    if (data && (String(data.investmentTaskId) !== String(taskId) || !Array.isArray(data.years))) throw new Error('Nieprawidłowe dane budżetu zadania.');
                    setTask(response.data);
                    setBudget(data);
                }
            })
            .catch((error) => { if (active) setLoadError(error.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [taskId, contractId, isEditing, loadAttempt]);

    useEffect(() => {
        if (isEditing) return;
        if (taxId.length !== 10) {
            setLookup({ status: taxId ? 'invalid' : 'idle', nip: taxId, contractor: null });
            return;
        }

        let active = true;
        const controller = new AbortController();
        setLookup({ status: 'pending', nip: taxId, contractor: null });
        const timer = setTimeout(async () => {
            if (!active) return;
            setLookup({ status: 'checking', nip: taxId, contractor: null });
            try {
                const response = await http.get(`/contractors/tax-id/${taxId}`, {
                    signal: controller.signal,
                });
                if (!active) return;
                if (!response.data.id || normalizeTaxId(response.data.taxId) !== taxId) {
                    throw new Error('Nie można potwierdzić danych wykonawcy. Ponów wyszukiwanie.');
                }
                setLookup({ status: 'found', nip: taxId, contractor: response.data });
            } catch (error) {
                if (!active) return;
                setLookup(error.response?.status === 404
                    ? { status: 'missing', nip: taxId, contractor: null }
                    : { status: 'error', nip: taxId, contractor: null, error: error.message });
            }
        }, 400);
        return () => { active = false; clearTimeout(timer); controller.abort(); };
    }, [taxId, lookupAttempt, isEditing]);

    function changeNip(value) {
        setNip(value);
        if (normalizeTaxId(value) === taxId) return;
        if (selected) setContractor(emptyContractor());
        setSelected(null);
        const nextNip = normalizeTaxId(value);
        setLookup({ status: nextNip.length === 10 ? 'pending' : nextNip ? 'invalid' : 'idle',
            nip: nextNip, contractor: null });
        setErrors({});
        setSaveError('');
    }

    function useExistingContractor() {
        const candidate = lookup.contractor;
        if (!candidate || lookup.nip !== taxId || normalizeTaxId(candidate.taxId) !== taxId) return;
        setSelected(candidate);
        setContractor(Object.fromEntries(CONTRACTOR_FIELDS.map((field) => [
            field.name, String(candidate[field.name] ?? ''),
        ])));
        setErrors({});
        setSaveError('');
    }

    function changeContract(field, value) {
        setContract((current) => ({ ...current, [field]: value }));
        setErrors((current) => ({ ...current, [field]: '' }));
        setSaveError('');
    }

    function changeContractor(field, value) {
        setContractor((current) => ({ ...current, [field]: value }));
        setErrors((current) => ({ ...current, [`contractor.${field}`]: '' }));
        setSaveError('');
    }

    async function handleSubmit(event) {
        event.preventDefault();
        if (savingRef.current || loading || loadError || checking) return;

        const validation = isEditing ? validateContractData(contract, contractTypes)
            : validateMainContractForm(contract, contractor, nip, lookup, selected, contractTypes);
        const limitsValidation = !isEditing ? validateInitialFinancialLimits(contract, budget, limitRows) : null;
        if (limitsValidation?.error) validation.financialLimitYears = limitsValidation.error;
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
            const response = isEditing
                ? await http.put(`/main-contracts/${contractId}`, buildMainContractUpdatePayload(contract))
                : await http.post('/main-contracts',
                    { ...buildMainContractPayload(taskId, contract, contractor, nip, selected),
                        financialLimitYears: limitsValidation.years });
            const savedId = isEditing ? contractId : response.data.id;
            if (mountedRef.current) navigate(`/main-contracts/${savedId}`, { replace: true });
        } catch (error) {
            if (!mountedRef.current) return;
            setSaveError(error.message);
            if (!isEditing && !selected && error.response?.status === 409) {
                // NIP mógł zostać zapisany przez inną osobę po naszym sprawdzeniu.
                setLookup({ status: 'pending', nip: taxId, contractor: null });
                setLookupAttempt((value) => value + 1);
            }
        } finally {
            savingRef.current = false;
            if (mountedRef.current) setSaving(false);
        }
    }

    const contractField = (name, label, options = {}) => (
        <ContractFormField name={name} label={label} value={contract[name]}
            error={errors[name]} onChange={(value) => changeContract(name, value)} {...options} />
    );

    return (
        <>
            {saving ? <span className="back-link" aria-disabled="true">← {returnLabel}</span>
                : <Link to={returnPath} className="back-link">← {returnLabel}</Link>}

            <section className="card main-form-card">
                <div className="main-form-heading">
                    <h1>{isEditing ? 'Edytuj umowę główną' : 'Dodaj umowę główną'}</h1>
                    {task && <p>{task.taskNumber} · {task.name}</p>}
                    {loadedContract && <p>Umowa {loadedContract.contractNumber} · Zadanie {loadedContract.investmentTaskNumber}</p>}
                    {isEditing && <p>Edytujesz dane pierwotnej umowy oraz informacje o zakończeniu i zabezpieczeniach. Zmiany wynikające z aneksów wprowadzaj w aneksach.</p>}
                    <p>Pola oznaczone * są wymagane.</p>
                </div>

                {loading ? <div className="loading-message" role="status">
                    {isEditing ? 'Ładowanie umowy...' : 'Ładowanie zadania...'}
                </div>
                    : loadError ? (
                        <div className="error-message" role="alert">
                            {loadError}
                            <button type="button" className="retry-button"
                                onClick={() => setLoadAttempt((value) => value + 1)}>Spróbuj ponownie</button>
                        </div>
                    ) : (
                        <form ref={formRef} onSubmit={handleSubmit} noValidate>
                            {saveError && <div className="error-message main-form-alert" role="alert">{saveError}</div>}
                            <fieldset className="main-form-section" disabled={saving}>
                                <legend>Dane umowy</legend>
                                <div className="main-form-grid">
                                    {contractField('contractNumber', 'Numer umowy', { required: true, maxLength: 255 })}
                                    {contractField('contractDate', 'Data zawarcia', { required: true, type: 'date' })}
                                    {contractField('contractType', 'Rodzaj umowy', { required: true, options: contractTypes })}
                                    {contractField('remunerationType', 'Rodzaj wynagrodzenia', { required: true, options: REMUNERATION_TYPES })}
                                    {contractField('initialValue', 'Wartość początkowa brutto (PLN)', { required: true, inputMode: 'decimal' })}
                                    {contractField('vatRate', 'Stawka VAT (%)', { required: true, inputMode: 'decimal' })}
                                    {contractField('initialStartDate', 'Rozpoczęcie realizacji', { type: 'date' })}
                                    {contractField('initialEndDate', 'Termin zakończenia', { type: 'date' })}
                                    {contractField('shortDescription', 'Krótki opis', { multiline: true, wide: true, maxLength: 500 })}
                                </div>
                            </fieldset>

                            <MainContractAdministrationFields value={contract.administration} options={administrationOptions}
                                errors={errors} disabled={saving} onChange={(administration) => {
                                    setContract(current => ({ ...current, administration }));
                                    setErrors(current => Object.fromEntries(Object.entries(current).filter(([key]) => !key.startsWith('administration.'))));
                                    setSaveError('');
                                }} />

                            {!isEditing && <InitialFinancialLimitsFields contract={contract} budget={budget} rows={limitRows}
                                onChange={(rows) => { setInitialLimitRows(rows); setErrors((current) => ({ ...current, financialLimitYears: '' })); setSaveError(''); }}
                                onReset={() => { setInitialLimitRows(null); setErrors((current) => ({ ...current, financialLimitYears: '' })); }}
                                error={errors.financialLimitYears} disabled={saving} onRefresh={() => setLoadAttempt((value) => value + 1)} />}

                            {isEditing ? (
                                <section className="main-form-section main-form-assigned-contractor">
                                    <h2>Wykonawca przypisany do umowy</h2>
                                    <p>{loadedContract.mainContractorName}</p>
                                    <p>NIP: {loadedContract.mainContractorTaxId || '—'}</p>
                                </section>
                            ) : (
                            <fieldset className="main-form-section" disabled={saving}>
                                <legend>Wykonawca</legend>
                                <div className="main-form-nip">
                                    <ContractFormField name="taxId" label="NIP" required value={nip}
                                        maxLength={255} error={errors.taxId} onChange={changeNip} />
                                    <div className="main-form-lookup" aria-live="polite">
                                        {lookup.status === 'idle' && <p>Wpisz NIP, aby sprawdzić wykonawcę w bazie.</p>}
                                        {lookup.status === 'invalid' && <p>NIP powinien zawierać 10 cyfr.</p>}
                                        {checking && <p>Sprawdzanie NIP...</p>}
                                        {lookup.status === 'missing' && (
                                            <p>Nie znaleziono wykonawcy. Uzupełnij jego dane poniżej.</p>
                                        )}
                                        {lookup.status === 'error' && (
                                            <div className="error-message" role="alert">
                                                {lookup.error}
                                                <button type="button" className="retry-button"
                                                    onClick={() => setLookupAttempt((value) => value + 1)}>
                                                    Ponów wyszukiwanie
                                                </button>
                                            </div>
                                        )}
                                        {lookup.status === 'found' && !selected && (
                                            <div className="main-form-found">
                                                <p>Znaleziono wykonawcę:</p>
                                                <strong>{lookup.contractor.name}</strong>
                                                <span>NIP: {lookup.contractor.taxId}</span>
                                                <button type="button" className="main-form-button"
                                                    onClick={useExistingContractor}>Użyj tego wykonawcy</button>
                                            </div>
                                        )}
                                        {selected && (
                                            <p className="main-form-selected">
                                                Wybrano istniejącego wykonawcę. Jego dane są tylko do odczytu.
                                                Zmień NIP, aby wybrać innego wykonawcę.
                                            </p>
                                        )}
                                    </div>
                                </div>

                                <div className="main-form-grid">
                                    {CONTRACTOR_FIELDS.map((field) => (
                                        <ContractFormField key={field.name} name={`contractor.${field.name}`}
                                            label={field.label} value={contractor[field.name]} type={field.type}
                                            required={field.required} wide={field.wide} maxLength={255}
                                            readOnly={Boolean(selected) || lookup.status !== 'missing'}
                                            error={errors[`contractor.${field.name}`]}
                                            onChange={(value) => changeContractor(field.name, value)} />
                                    ))}
                                </div>
                            </fieldset>
                            )}

                            <div className="main-form-footer">
                                <button type="submit" className="main-form-button main-form-button-primary"
                                    disabled={saving || checking}>
                                    {saving ? 'Zapisywanie...' : isEditing ? 'Zapisz zmiany' : 'Utwórz umowę'}
                                </button>
                                <button type="button" className="main-form-button" disabled={saving}
                                    onClick={() => navigate(returnPath)}>Anuluj</button>
                                <span className="main-form-hint" role="status" aria-live="polite">
                                    {saving ? 'Trwa zapisywanie umowy.' : ''}
                                </span>
                            </div>
                        </form>
                    )}
            </section>
        </>
    );
}

function ContractFormField({ name, label, value, onChange, error, required = false,
    type = 'text', inputMode, readOnly = false, multiline = false, wide = false, maxLength, options }) {
    const id = `main-form-${name.replace('.', '-')}`;
    const props = {
        id, name, value, required, readOnly, maxLength, inputMode,
        'aria-invalid': Boolean(error), 'aria-describedby': error ? `${id}-error` : undefined,
        onChange: (event) => onChange(event.target.value),
    };
    return (
        <div className={`main-form-field${wide ? ' main-form-field-wide' : ''}`}>
            <label htmlFor={id}>{label}{required ? ' *' : ''}</label>
            {options ? <select {...props}>
                <option value="">Wybierz</option>
                {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select> : multiline ? <textarea {...props} rows={3} /> : <input {...props} type={type} />}
            {error && <p id={`${id}-error`} className="main-form-field-error">{error}</p>}
        </div>
    );
}
