import { useEffect, useRef, useState } from 'react';
import http from './api/http';
import BudgetCard from './BudgetCard';
import {
    buildBudgetRequest,
    formatBudgetCents,
    getBudgetPreview,
    isMissingBudget,
    parseBudgetYear,
    validateBudgetRows,
} from './utils/budgetForm';
import './BudgetSection.css';

export default function BudgetSection({ investmentTaskId }) {
    const [budget, setBudget] = useState(null);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [mode, setMode] = useState(null);
    const [success, setSuccess] = useState('');
    const [loadAttempt, setLoadAttempt] = useState(0);

    useEffect(() => {
        let active = true;
        const controller = new AbortController();

        async function loadBudget() {
            setLoading(true);
            setLoadError('');

            try {
                const response = await http.get(
                    `/budgets/investment-task/${investmentTaskId}`,
                    { signal: controller.signal }
                );
                if (active) setBudget(response.data);
            } catch (error) {
                if (!active) return;
                if (isMissingBudget(error, investmentTaskId)) setBudget(null);
                else setLoadError(error.message);
            } finally {
                if (active) setLoading(false);
            }
        }

        loadBudget();
        return () => {
            active = false;
            controller.abort();
        };
    }, [investmentTaskId, loadAttempt]);

    function openEditor(nextMode) {
        setSuccess('');
        setMode(nextMode);
    }

    function handleSaved(savedBudget) {
        // Serwis zwraca przeliczone podsumowanie, zaangażowanie i wolne środki.
        setBudget(savedBudget);
        setSuccess(mode === 'create' ? 'Budżet został utworzony.'
            : mode === 'add' ? 'Rok został dodany do budżetu.'
                : 'Kwota budżetu została zmieniona.');
        setMode(null);
    }

    return (
        <>
            <div className="section-heading budget-editor-section-heading">
                <div>
                    <h2>Budżet</h2>
                    <p>Plan finansowy i faktycznie zrealizowane płatności dla umów głównych.</p>
                </div>

                {!loading && !loadError && !mode && (
                    <div className="budget-editor-actions">
                        {budget ? (
                            <>
                                <button type="button" className="budget-editor-button"
                                    onClick={() => openEditor('add')}>
                                    Dodaj rok
                                </button>
                                <button type="button" className="budget-editor-button"
                                    disabled={!budget.years?.length}
                                    onClick={() => openEditor('edit')}>
                                    Zmień kwotę
                                </button>
                            </>
                        ) : (
                            <button type="button"
                                className="budget-editor-button budget-editor-button-primary"
                                onClick={() => openEditor('create')}>
                                Dodaj budżet
                            </button>
                        )}
                        <button type="button" className="budget-editor-button"
                            onClick={() => { setSuccess(''); setLoadAttempt((value) => value + 1); }}>
                            Odśwież
                        </button>
                    </div>
                )}
            </div>

            {success && (
                <div className="budget-editor-success" role="status">{success}</div>
            )}

            {loading ? (
                <div className="loading-message" role="status">Ładowanie budżetu...</div>
            ) : loadError ? (
                <div className="error-message" role="alert">
                    Nie udało się pobrać budżetu: {loadError}
                    <button type="button" className="retry-button"
                        onClick={() => setLoadAttempt((value) => value + 1)}>
                        Spróbuj ponownie
                    </button>
                </div>
            ) : (
                <>
                    {mode && (
                        <BudgetEditor
                            key={mode}
                            mode={mode}
                            budget={budget}
                            investmentTaskId={investmentTaskId}
                            onSaved={handleSaved}
                            onCancel={() => setMode(null)}
                        />
                    )}
                    {budget ? (
                        <BudgetCard budget={budget} />
                    ) : !mode ? (
                        <section className="card empty-state">
                            <h3>Brak budżetu</h3>
                            <p>Dla tego zadania nie określono jeszcze budżetu.</p>
                        </section>
                    ) : null}
                </>
            )}
        </>
    );
}

function BudgetEditor({ mode, budget, investmentTaskId, onSaved, onCancel }) {
    const years = [...(budget?.years ?? [])].sort((a, b) => Number(a.year) - Number(b.year));
    const existingYears = years.map((entry) => Number(entry.year));
    const [rows, setRows] = useState(() => {
        if (mode === 'edit') {
            const selected = years.find((entry) => Number(entry.year) === Number(budget.currentYear)) ?? years[0];
            return [{ id: 0, year: String(selected.year), amount: editableAmount(selected.amount) }];
        }
        let year = new Date().getFullYear();
        while (existingYears.includes(year)) year++;
        return [{ id: 0, year: String(year), amount: '' }];
    });
    const [fieldErrors, setFieldErrors] = useState({});
    const [saveError, setSaveError] = useState('');
    const [saving, setSaving] = useState(false);
    const rowIdRef = useRef(1);
    const formRef = useRef(null);
    const savingRef = useRef(false);
    const mountedRef = useRef(false);
    const preview = getBudgetPreview(mode, rows, budget);

    useEffect(() => {
        mountedRef.current = true;
        return () => { mountedRef.current = false; };
    }, []);

    function changeRow(id, field, value) {
        setRows((current) => current.map((row) => {
            if (row.id !== id) return row;
            if (mode === 'edit' && field === 'year') {
                const selected = years.find((entry) => String(entry.year) === value);
                return { ...row, year: value, amount: editableAmount(selected?.amount) };
            }
            return { ...row, [field]: value };
        }));
        setFieldErrors({});
        setSaveError('');
    }

    function addRow() {
        let year = (parseBudgetYear(rows.at(-1)?.year) ?? new Date().getFullYear()) + 1;
        const selected = new Set(rows.map((row) => parseBudgetYear(row.year)));
        while (selected.has(year)) year++;
        const id = rowIdRef.current++;
        setRows((current) => [...current, { id, year: String(year), amount: '' }]);
        setFieldErrors({});
        setSaveError('');
    }

    async function handleSubmit(event) {
        event.preventDefault();
        if (savingRef.current) return;

        const validation = validateBudgetRows(rows, mode === 'add' ? existingYears : []);
        setFieldErrors(validation.errors);
        setSaveError('');

        if (!validation.valid) {
            const invalid = rows.find((row) => validation.errors[row.id]);
            if (invalid) {
                const field = validation.errors[invalid.id].year ? 'year' : 'amount';
                formRef.current?.elements.namedItem(`${field}-${invalid.id}`)?.focus();
            } else setSaveError('Dodaj co najmniej jeden rok budżetu.');
            return;
        }

        if (mode === 'edit' && !existingYears.includes(validation.years[0].year)) {
            setSaveError('Wybrany rok nie jest zapisany w budżecie.');
            return;
        }

        savingRef.current = true;
        setSaving(true);

        try {
            const response = await http.request(
                buildBudgetRequest(mode, investmentTaskId, validation.years)
            );
            if (mountedRef.current) onSaved(response.data);
        } catch (error) {
            if (mountedRef.current) setSaveError(error.message);
        } finally {
            savingRef.current = false;
            if (mountedRef.current) setSaving(false);
        }
    }

    return (
        <form ref={formRef} className="card budget-editor-form"
            onSubmit={handleSubmit} noValidate>
            <div className="budget-editor-form-heading">
                <h3>{mode === 'create' ? 'Dodaj budżet'
                    : mode === 'add' ? 'Dodaj rok do budżetu' : 'Zmień kwotę roku'}</h3>
                <p>Kwoty brutto w PLN. Możesz użyć przecinka lub kropki. Pola * są wymagane.</p>
            </div>

            {saveError && (
                <div className="error-message budget-editor-alert" role="alert">{saveError}</div>
            )}

            <fieldset className="budget-editor-fields" disabled={saving}>
                <legend className="budget-editor-sr-only">Finansowanie roczne</legend>
                <div className="budget-editor-rows">
                    {rows.map((row, index) => (
                        <BudgetRow
                            key={row.id}
                            row={row}
                            index={index}
                            errors={fieldErrors[row.id] ?? {}}
                            yearOptions={mode === 'edit' ? years : null}
                            removable={mode === 'create' && rows.length > 1}
                            onChange={changeRow}
                            onRemove={() => {
                                setRows((current) => current.filter((entry) => entry.id !== row.id));
                                setFieldErrors({});
                                setSaveError('');
                            }}
                        />
                    ))}
                </div>
                {mode === 'create' && (
                    <button type="button" className="budget-editor-button budget-editor-add-row"
                        onClick={addRow}>
                        Dodaj kolejny rok
                    </button>
                )}
            </fieldset>

            <div className="budget-editor-preview" aria-live="polite">
                <PreviewMetric label={mode === 'create' ? 'Budżet całkowity'
                    : 'Budżet całkowity po zmianie'} value={preview.totalCents} />
                {preview.committedCents !== null && (
                    <>
                        <PreviewMetric label={`Zaangażowanie w ${rows[0].year}`}
                            value={preview.committedCents} />
                        <PreviewMetric label="Wolne środki po zmianie"
                            value={preview.availableCents} />
                    </>
                )}
                {preview.paidCents !== null && (
                    <PreviewMetric label={`Zapłacono w ${rows[0].year}`}
                        value={preview.paidCents} />
                )}
            </div>

            {preview.availableCents !== null && preview.availableCents < 0n && (
                <p className="budget-editor-warning" role="status">
                    Wprowadzona kwota jest niższa od zaangażowania w tym roku.
                    Wolne środki po zmianie będą ujemne.
                </p>
            )}
            {preview.paymentExceeded && (
                <p className="budget-editor-warning" role="status">
                    Wprowadzona kwota jest niższa od płatności dokonanych w tym roku.
                </p>
            )}

            <div className="budget-editor-footer">
                <button type="submit" className="budget-editor-button budget-editor-button-primary"
                    disabled={saving}>
                    {saving ? 'Zapisywanie...' : mode === 'create'
                        ? 'Utwórz budżet' : mode === 'add' ? 'Zapisz rok' : 'Zapisz zmianę'}
                </button>
                <button type="button" className="budget-editor-button"
                    onClick={onCancel} disabled={saving}>
                    Anuluj
                </button>
                <span className="budget-editor-hint" role="status" aria-live="polite">
                    {saving ? 'Trwa zapisywanie budżetu.' : ''}
                </span>
            </div>
        </form>
    );
}

function BudgetRow({ row, index, errors, yearOptions, removable, onChange, onRemove }) {
    const yearId = `budget-year-${row.id}`;
    const amountId = `budget-amount-${row.id}`;

    return (
        <div className={`budget-editor-row${removable ? ' budget-editor-row-removable' : ''}`}>
            <div className="budget-editor-field">
                <label htmlFor={yearId}>Rok *</label>
                {yearOptions ? (
                    <select id={yearId} name={`year-${row.id}`} value={row.year}
                        onChange={(event) => onChange(row.id, 'year', event.target.value)}
                        required aria-invalid={Boolean(errors.year)}
                        aria-describedby={errors.year ? `${yearId}-error` : undefined}>
                        {yearOptions.map((entry) => (
                            <option key={entry.year} value={entry.year}>{entry.year}</option>
                        ))}
                    </select>
                ) : (
                    <input id={yearId} name={`year-${row.id}`} type="text" inputMode="numeric"
                        value={row.year} required autoComplete="off"
                        onChange={(event) => onChange(row.id, 'year', event.target.value)}
                        aria-invalid={Boolean(errors.year)}
                        aria-describedby={errors.year ? `${yearId}-error` : undefined} />
                )}
                {errors.year && (
                    <p id={`${yearId}-error`} className="budget-editor-field-error">{errors.year}</p>
                )}
            </div>
            <div className="budget-editor-field">
                <label htmlFor={amountId}>Kwota brutto (PLN) *</label>
                <input id={amountId} name={`amount-${row.id}`} type="text" inputMode="decimal"
                    value={row.amount} required autoComplete="off" placeholder="0,00"
                    onChange={(event) => onChange(row.id, 'amount', event.target.value)}
                    aria-invalid={Boolean(errors.amount)}
                    aria-describedby={errors.amount ? `${amountId}-error` : undefined} />
                {errors.amount && (
                    <p id={`${amountId}-error`} className="budget-editor-field-error">{errors.amount}</p>
                )}
            </div>
            {removable && (
                <div className="budget-editor-row-action">
                    <button type="button" className="budget-editor-button"
                        onClick={onRemove} aria-label={`Usuń rok z wiersza ${index + 1}`}>
                        Usuń
                    </button>
                </div>
            )}
        </div>
    );
}

function PreviewMetric({ label, value }) {
    return (
        <div>
            <span>{label}</span>
            <strong className={value !== null && value < 0n ? 'budget-editor-negative' : ''}>
                {formatBudgetCents(value)}
            </strong>
        </div>
    );
}

function editableAmount(value) {
    return value === null || value === undefined ? '' : String(value).replace('.', ',');
}
