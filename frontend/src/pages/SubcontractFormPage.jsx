import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import http from '../api/http';
import {
    CONTRACTOR_FIELDS, contractToForm, emptyContract, emptyContractor, normalizeTaxId, REMUNERATION_TYPES,
} from '../utils/mainContractForm';
import { readContractTypeOptions } from '../utils/contractTypes';
import { formatBudgetCents } from '../utils/budgetForm';
import {
    buildSubcontractRequest, deductionsToForm, DEDUCTION_FIELDS, emptyDeductions,
    getSubcontractPreview, getProposedSubcontractsSummary, getSubcontractEndWarning,
    proposedSubcontractEndDate, validateSubcontractDates, validateSubcontractForm, VALUE_BASES,
} from '../utils/subcontractForm';
import './MainContractFormPage.css';
import './SubcontractFormPage.css';

export default function SubcontractFormPage() {
    const { contractId, subcontractId, parentSubcontractId } = useParams();
    return <SubcontractForm key={`${contractId}-${subcontractId ?? 'new'}-${parentSubcontractId ?? 'root'}`}
        contractId={contractId} subcontractId={subcontractId} parentSubcontractId={parentSubcontractId} />;
}

function SubcontractForm({ contractId, subcontractId, parentSubcontractId }) {
    const navigate = useNavigate();
    const isEditing = Boolean(subcontractId);
    const returnPath = subcontractId ? `/main-contracts/${contractId}/subcontracts/${subcontractId}`
        : parentSubcontractId ? `/main-contracts/${contractId}/subcontracts/${parentSubcontractId}` : `/main-contracts/${contractId}`;
    const [mainContract, setMainContract] = useState(null);
    const [savedSubcontract, setSavedSubcontract] = useState(null);
    const [contractTypes, setContractTypes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [loadAttempt, setLoadAttempt] = useState(0);
    const [contract, setContract] = useState(emptyContract);
    const [deductions, setDeductions] = useState(emptyDeductions);
    const [contractor, setContractor] = useState(emptyContractor);
    const [nip, setNip] = useState('');
    const [lookup, setLookup] = useState({ status: 'idle', nip: '', contractor: null });
    const [lookupAttempt, setLookupAttempt] = useState(0);
    const [selected, setSelected] = useState(null);
    const [errors, setErrors] = useState({});
    const [saveError, setSaveError] = useState('');
    const [saving, setSaving] = useState(false);
    const formRef = useRef(null);
    const savingRef = useRef(false);
    const mountedRef = useRef(false);
    const taxId = normalizeTaxId(nip);
    const checking = !isEditing && (lookup.status === 'pending' || lookup.status === 'checking');

    useEffect(() => {
        mountedRef.current = true;
        return () => { mountedRef.current = false; };
    }, []);

    useEffect(() => {
        let active = true;
        const controller = new AbortController();
        setLoading(true);
        setLoadError('');
        const requests = [
            http.get(`/main-contracts/${contractId}`, { signal: controller.signal }),
            http.get('/contract-types', { signal: controller.signal }),
        ];
        if (isEditing) requests.push(http.get(`/subcontracts/${subcontractId}`, { signal: controller.signal }));
        Promise.all(requests)
            .then(async ([mainResponse, typesResponse, subResponse]) => {
                if (!active) return;
                const main = mainResponse.data;
                if (!main?.id || String(main.id) !== String(contractId)) {
                    throw new Error('Nie można odczytać tej umowy głównej. Spróbuj ponownie.');
                }
                const types = readContractTypeOptions(typesResponse.data);
                const sub = subResponse?.data;
                if (isEditing && (!sub?.id || String(sub.id) !== String(subcontractId)
                    || String(sub.mainContractId) !== String(contractId))) {
                    throw new Error('Umowa podwykonawcza nie jest przypisana do tej umowy głównej.');
                }
                const parentId = isEditing ? sub.parentSubcontractId : parentSubcontractId;
                const parent = parentId ? (await http.get(`/subcontracts/${parentId}`, { signal: controller.signal })).data : null;
                if (parent && (String(parent.id) !== String(parentId) || String(parent.mainContractId) !== String(contractId))) throw new Error('Nieprawidłowa umowa zleceniodawcy.');
                if (!active) return;
                setMainContract(parent ? { ...parent, mainContractorId: parent.subcontractorId, mainContractorTaxId: parent.subcontractorTaxId,
                    mainContractorName: parent.subcontractorName } : main);
                setContractTypes(types);
                if (isEditing) {
                    setSavedSubcontract(sub);
                    setContract(contractToForm(sub));
                    setDeductions(deductionsToForm(sub));
                }
            })
            .catch((error) => { if (active) setLoadError(error.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [contractId, subcontractId, parentSubcontractId, isEditing, loadAttempt]);

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
                const response = await http.get(`/contractors/tax-id/${taxId}`, { signal: controller.signal });
                if (!active) return;
                if (!response.data.id || normalizeTaxId(response.data.taxId) !== taxId) {
                    throw new Error('Nie można potwierdzić danych podwykonawcy. Ponów wyszukiwanie.');
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
        setLookup({ status: nextNip.length === 10 ? 'pending' : nextNip ? 'invalid' : 'idle', nip: nextNip, contractor: null });
        setErrors({});
        setSaveError('');
    }

    function useExistingContractor() {
        const candidate = lookup.contractor;
        if (!candidate || lookup.nip !== taxId || normalizeTaxId(candidate.taxId) !== taxId) return;
        setSelected(candidate);
        setContractor(Object.fromEntries(CONTRACTOR_FIELDS.map((field) => [field.name, String(candidate[field.name] ?? '')])));
        setErrors({});
        setSaveError('');
    }

    function changeContract(name, value) {
        setContract((current) => ({ ...current, [name]: value }));
        setErrors((current) => ({ ...current, [name]: '' }));
        setSaveError('');
    }
    function changeContractor(name, value) {
        setContractor((current) => ({ ...current, [name]: value }));
        setErrors((current) => ({ ...current, [`contractor.${name}`]: '' }));
        setSaveError('');
    }
    function changeDeduction(name, field, value) {
        setDeductions((current) => ({ ...current, [name]: { ...current[name], [field]: value } }));
        setErrors((current) => ({ ...current, [`${name}.percentage`]: '', [`${name}.basis`]: '' }));
        setSaveError('');
    }

    async function handleSubmit(event) {
        event.preventDefault();
        if (savingRef.current || loading || loadError || checking || !mainContract) return;
        const validation = {
            ...validateSubcontractForm(contract, deductions, contractor, nip, lookup, selected, contractTypes, isEditing),
            ...validateSubcontractDates(contract, mainContract, savedSubcontract),
        };
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
            const request = buildSubcontractRequest(contractId, subcontractId, contract, deductions, contractor, nip, selected);
            if (!isEditing && parentSubcontractId) request.data.parentSubcontractId = Number(parentSubcontractId);
            await http[request.method](request.url, request.data);
            if (mountedRef.current) navigate(returnPath, { replace: true });
        } catch (error) {
            if (!mountedRef.current) return;
            setSaveError(error.message);
            if (!isEditing && !selected && error.response?.status === 409) {
                setLookup({ status: 'pending', nip: taxId, contractor: null });
                setLookupAttempt((value) => value + 1);
            }
        } finally {
            savingRef.current = false;
            if (mountedRef.current) setSaving(false);
        }
    }

    const preview = getSubcontractPreview(contract, deductions, savedSubcontract);
    const summary = getProposedSubcontractsSummary(mainContract, contract, savedSubcontract);
    const endWarning = getSubcontractEndWarning(mainContract?.currentEndDate,
        proposedSubcontractEndDate(contract, savedSubcontract));
    const field = (name, label, options = {}) => <SubcontractField name={name} label={label} value={contract[name]}
        error={errors[name]} onChange={(value) => changeContract(name, value)} {...options} />;

    return (
        <>
            {saving ? <span className="back-link" aria-disabled="true">← Szczegóły umowy zleceniodawcy</span>
                : <Link to={returnPath} className="back-link">← Szczegóły umowy zleceniodawcy</Link>}
            <section className="card main-form-card">
                <div className="main-form-heading">
                    <h1>{isEditing ? 'Edytuj umowę podwykonawczą' : 'Dodaj umowę podwykonawczą'}</h1>
                    {mainContract && <p>Umowa zleceniodawcy {mainContract.contractNumber} · {mainContract.mainContractorName}</p>}
                    {isEditing && <p>Edytujesz dane pierwotne umowy podwykonawczej i zasady potrąceń.</p>}
                    <p>Pola oznaczone * są wymagane.</p>
                </div>
                {loading ? <div className="loading-message" role="status">Ładowanie danych umowy...</div>
                    : loadError ? <div className="error-message" role="alert">
                        {loadError}<button type="button" className="retry-button"
                            onClick={() => setLoadAttempt((value) => value + 1)}>Spróbuj ponownie</button>
                    </div> : (
                        <form ref={formRef} onSubmit={handleSubmit} noValidate>
                            {saveError && <div className="error-message main-form-alert" role="alert">{saveError}</div>}
                            {endWarning && <p className="subcontract-end-warning" role="status">{endWarning}</p>}
                            {summary && <section className="subcontract-preview subcontract-budget-preview" aria-label="Wartość podwykonawstwa po zapisie">
                                <h2>Wartość podwykonawstwa po zapisie</h2>
                                <dl>
                                    <div><dt>Suma wszystkich umów brutto</dt><dd>{formatBudgetCents(summary.totalCents)}</dd></div>
                                    <div><dt>Pozostanie do rozdysponowania</dt><dd>{formatBudgetCents(summary.remainingCents)}</dd></div>
                                </dl>
                                {summary.remainingCents < 0n && <p className="subcontract-budget-exceeded" role="alert">
                                    Suma przekracza aktualną wartość umowy zleceniodawcy. Zmniejsz wartość umowy przed zapisem.
                                </p>}
                            </section>}
                            <fieldset className="main-form-section" disabled={saving}>
                                <legend>Dane umowy podwykonawczej</legend>
                                <div className="main-form-grid">
                                    {field('contractNumber', 'Numer umowy', { required: true, maxLength: 255 })}
                                    {field('contractDate', 'Data zawarcia', { required: true, type: 'date' })}
                                    {field('contractType', 'Rodzaj umowy', { required: true, options: contractTypes })}
                                    {field('remunerationType', 'Rodzaj wynagrodzenia', { required: true, options: REMUNERATION_TYPES })}
                                    {field('initialValue', 'Wartość początkowa brutto (PLN)', { required: true, inputMode: 'decimal' })}
                                    {field('vatRate', 'Stawka VAT (%)', { required: true, inputMode: 'decimal' })}
                                    {field('initialStartDate', 'Rozpoczęcie realizacji', { type: 'date' })}
                                    {field('initialEndDate', 'Termin zakończenia', {
                                        type: 'date', required: !savedSubcontract?.endDateOverriddenByAmendment,
                                    })}
                                    {field('shortDescription', 'Krótki opis', { multiline: true, wide: true, maxLength: 500 })}
                                </div>
                                {savedSubcontract?.endDateOverriddenByAmendment && <p className="subcontract-rules-hint">
                                    Aktualny termin zakończenia wynika z aneksu. Edycja terminu pierwotnego go nie zmienia.
                                </p>}
                            </fieldset>

                            {isEditing ? <section className="main-form-section main-form-assigned-contractor">
                                <h2>Podwykonawca przypisany do umowy</h2>
                                <p>{savedSubcontract.subcontractorName}</p>
                                <p>NIP: {savedSubcontract.subcontractorTaxId || '—'}</p>
                            </section> : <fieldset className="main-form-section" disabled={saving}>
                                <legend>Podwykonawca</legend>
                                <div className="main-form-nip">
                                    <SubcontractField name="taxId" label="NIP" required value={nip} maxLength={255}
                                        error={errors.taxId} onChange={changeNip} />
                                    <div className="main-form-lookup" aria-live="polite">
                                        {lookup.status === 'idle' && <p>Wpisz NIP, aby sprawdzić podwykonawcę w bazie.</p>}
                                        {lookup.status === 'invalid' && <p>NIP powinien zawierać 10 cyfr.</p>}
                                        {checking && <p>Sprawdzanie NIP...</p>}
                                        {lookup.status === 'missing' && <p>Nie znaleziono firmy. Uzupełnij jej dane poniżej.</p>}
                                        {lookup.status === 'error' && <div className="error-message" role="alert">
                                            {lookup.error}<button type="button" className="retry-button"
                                                onClick={() => setLookupAttempt((value) => value + 1)}>Ponów wyszukiwanie</button>
                                        </div>}
                                        {lookup.status === 'found' && !selected && <div className="main-form-found">
                                            <p>Znaleziono podwykonawcę:</p><strong>{lookup.contractor.name}</strong>
                                            <span>NIP: {lookup.contractor.taxId}</span>
                                            <button type="button" className="main-form-button" onClick={useExistingContractor}>Użyj tego podwykonawcy</button>
                                        </div>}
                                        {selected && <p className="main-form-selected">Wybrano istniejącego podwykonawcę.
                                            Jego dane są do odczytu. Zmień NIP, aby wybrać inną firmę.</p>}
                                    </div>
                                </div>
                                <div className="main-form-grid">
                                    {CONTRACTOR_FIELDS.map((item) => <SubcontractField key={item.name} name={`contractor.${item.name}`}
                                        label={item.name === 'name' ? 'Nazwa podwykonawcy' : item.label} value={contractor[item.name]}
                                        type={item.type} required={item.required} wide={item.wide} maxLength={255}
                                        readOnly={Boolean(selected) || lookup.status !== 'missing'} error={errors[`contractor.${item.name}`]}
                                        onChange={(value) => changeContractor(item.name, value)} />)}
                                </div>
                            </fieldset>}

                            <fieldset className="main-form-section" disabled={saving}>
                                <legend>Potrącenia umowne</legend>
                                {DEDUCTION_FIELDS.map((item) => {
                                    const rule = deductions[item.name];
                                    return <div key={item.name} className="subcontract-rule">
                                        <label className="subcontract-rule-toggle">
                                            <input type="checkbox" name={`${item.name}.enabled`} checked={rule.enabled}
                                                onChange={(event) => changeDeduction(item.name, 'enabled', event.target.checked)} />
                                            {item.label}
                                        </label>
                                        {rule.enabled && <div className="main-form-grid">
                                            <SubcontractField name={`${item.name}.percentage`} label="Procent" required inputMode="decimal"
                                                value={rule.percentage} error={errors[`${item.name}.percentage`]}
                                                onChange={(value) => changeDeduction(item.name, 'percentage', value)} />
                                            <SubcontractField name={`${item.name}.basis`} label="Podstawa naliczenia" required options={VALUE_BASES}
                                                value={rule.basis} error={errors[`${item.name}.basis`]}
                                                onChange={(value) => changeDeduction(item.name, 'basis', value)} />
                                        </div>}
                                    </div>;
                                })}
                                <p className="subcontract-rules-hint">Każde potrącenie ustawiasz osobno jako procent od netto lub brutto.
                                    Wyłączenie potrącenia usuwa jego regułę z umowy.</p>
                            </fieldset>

                            {preview && <section className="subcontract-preview" aria-label="Podgląd potrąceń umownych" aria-live="polite">
                                <h2>Podgląd kwot umownych</h2>
                                <dl>
                                    <div><dt>Aktualna wartość brutto po zapisie</dt><dd>{formatBudgetCents(preview.grossCents)}</dd></div>
                                    <div><dt>Wartość netto po zapisie</dt><dd>{formatBudgetCents(preview.netCents)}</dd></div>
                                    {DEDUCTION_FIELDS.map((item) => <div key={item.name}><dt>{item.label}</dt>
                                        <dd>{formatBudgetCents(preview.amounts[item.name])}</dd></div>)}
                                    <div><dt>Łączne potrącenia umowne</dt><dd>{formatBudgetCents(preview.totalCents)}</dd></div>
                                </dl>
                                <p>Kwoty wynikają z aktualnej wartości umowy{isEditing ? ', z uwzględnieniem dotychczasowych aneksów' : ''}.
                                    Faktyczne potrącenia przy płatnościach są rozliczane osobno.</p>
                            </section>}

                            <div className="main-form-footer">
                                <button type="submit" className="main-form-button main-form-button-primary" disabled={saving || checking}>
                                    {saving ? 'Zapisywanie...' : isEditing ? 'Zapisz zmiany' : 'Utwórz umowę podwykonawczą'}
                                </button>
                                <button type="button" className="main-form-button" disabled={saving} onClick={() => navigate(returnPath)}>Anuluj</button>
                                <span className="main-form-hint" role="status">{saving ? 'Trwa zapisywanie umowy.' : ''}</span>
                            </div>
                        </form>
                    )}
            </section>
        </>
    );
}

function SubcontractField({ name, label, value, onChange, error, required = false, type = 'text', inputMode,
    readOnly = false, multiline = false, wide = false, maxLength, options }) {
    const id = `subcontract-form-${name.replaceAll('.', '-')}`;
    const props = { id, name, value, required, readOnly, maxLength, inputMode,
        'aria-invalid': Boolean(error), 'aria-describedby': error ? `${id}-error` : undefined,
        onChange: (event) => onChange(event.target.value) };
    return <div className={`main-form-field${wide ? ' main-form-field-wide' : ''}`}>
        <label htmlFor={id}>{label}{required ? ' *' : ''}</label>
        {options ? <select {...props}><option value="">Wybierz</option>
            {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select>
            : multiline ? <textarea {...props} rows={3} /> : <input {...props} type={type} />}
        {error && <p id={`${id}-error`} className="main-form-field-error">{error}</p>}
    </div>;
}
