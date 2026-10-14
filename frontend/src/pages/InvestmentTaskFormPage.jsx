import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import http from '../api/http';

const STATUS_OPTIONS = [
    { value: 'IN_PROGRESS', label: 'W realizacji' },
    { value: 'COMPLETED', label: 'Zakończone' },
    { value: 'ON_HOLD', label: 'Wstrzymane' },
    { value: 'CANCELLED', label: 'Rezygnacja' },
];

const EMPTY_FORM = {
    taskNumber: '',
    name: '',
    description: '',
    status: 'IN_PROGRESS',
};

export default function InvestmentTaskFormPage() {
    const { taskId } = useParams();

    // Zmiana zadania lub trybu otwiera formularz z nowym stanem.
    return <InvestmentTaskForm key={taskId ?? 'new'} taskId={taskId} />;
}

function InvestmentTaskForm({ taskId }) {
    const navigate = useNavigate();
    const isEditing = Boolean(taskId);
    const returnPath = isEditing ? `/tasks/${taskId}` : '/tasks';

    const [form, setForm] = useState({ ...EMPTY_FORM });
    const [fieldErrors, setFieldErrors] = useState({});
    const [loading, setLoading] = useState(isEditing);
    const [loadError, setLoadError] = useState('');
    const [saveError, setSaveError] = useState('');
    const [saving, setSaving] = useState(false);
    const [loadAttempt, setLoadAttempt] = useState(0);

    const formRef = useRef(null);
    const savingRef = useRef(false);
    const mountedRef = useRef(false);

    useEffect(() => {
        mountedRef.current = true;
        return () => {
            mountedRef.current = false;
        };
    }, []);

    useEffect(() => {
        if (!isEditing) return;

        let active = true;
        const controller = new AbortController();

        async function loadTask() {
            setLoading(true);
            setLoadError('');

            try {
                const response = await http.get(
                    `/investment-tasks/${taskId}`,
                    { signal: controller.signal }
                );

                if (!active) return;

                const task = response.data;
                setForm({
                    taskNumber: task.taskNumber ?? '',
                    name: task.name ?? '',
                    description: task.description ?? '',
                    status: task.status ?? '',
                });
            } catch (error) {
                if (!active) return;

                setLoadError(
                    error.response?.status === 404
                        ? 'Nie znaleziono zadania inwestycyjnego.'
                        : error.message
                );
            } finally {
                if (active) setLoading(false);
            }
        }

        loadTask();

        return () => {
            active = false;
            controller.abort();
        };
    }, [taskId, isEditing, loadAttempt]);

    function handleChange(event) {
        const { name, value } = event.target;

        setForm((current) => ({ ...current, [name]: value }));
        setFieldErrors((current) => ({ ...current, [name]: '' }));
        setSaveError('');
    }

    async function handleSubmit(event) {
        event.preventDefault();
        if (savingRef.current || loading || loadError) return;

        const errors = {};
        if (!form.taskNumber.trim()) {
            errors.taskNumber = 'Podaj numer zadania.';
        }
        if (!form.name.trim()) {
            errors.name = 'Podaj nazwę zadania.';
        }
        if (!form.status) {
            errors.status = 'Wybierz status zadania.';
        }

        setFieldErrors(errors);
        setSaveError('');

        const firstInvalidField = Object.keys(errors)[0];
        if (firstInvalidField) {
            formRef.current?.elements.namedItem(firstInvalidField)?.focus();
            return;
        }

        const payload = {
            taskNumber: form.taskNumber.trim(),
            name: form.name.trim(),
            description: form.description.trim() || null,
            status: form.status,
        };

        savingRef.current = true;
        setSaving(true);

        try {
            const response = isEditing
                ? await http.put(`/investment-tasks/${taskId}`, payload)
                : await http.post('/investment-tasks', payload);

            if (mountedRef.current) {
                navigate(`/tasks/${response.data.id}`, { replace: true });
            }
        } catch (error) {
            if (mountedRef.current) setSaveError(error.message);
        } finally {
            savingRef.current = false;
            if (mountedRef.current) setSaving(false);
        }
    }

    return (
        <>
            {saving ? (
                <span className="back-link" aria-disabled="true">
                    {isEditing ? '← Szczegóły zadania' : '← Zadania inwestycyjne'}
                </span>
            ) : (
                <Link to={returnPath} className="back-link">
                    {isEditing ? '← Szczegóły zadania' : '← Zadania inwestycyjne'}
                </Link>
            )}

            <section className="card task-form-card">
                <div className="task-form-heading">
                    <h1>{isEditing ? 'Edytuj zadanie' : 'Dodaj zadanie'}</h1>
                    <p>Pola oznaczone * są wymagane.</p>
                </div>

                {loading ? (
                    <div className="loading-message" role="status">
                        Ładowanie danych zadania...
                    </div>
                ) : loadError ? (
                    <div className="error-message" role="alert">
                        {loadError}
                        <button
                            type="button"
                            className="retry-button"
                            onClick={() => setLoadAttempt((current) => current + 1)}
                        >
                            Spróbuj ponownie
                        </button>
                    </div>
                ) : (
                    <form ref={formRef} onSubmit={handleSubmit} noValidate>
                        {saveError && (
                            <div className="error-message task-form-alert" role="alert">
                                {saveError}
                            </div>
                        )}

                        <fieldset className="task-form-fields" disabled={saving}>
                            <legend className="task-form-sr-only">Dane zadania</legend>

                            <div className="task-form-grid">
                                <div className="task-form-field">
                                    <label htmlFor="taskNumber">Numer zadania *</label>
                                    <input
                                        id="taskNumber"
                                        name="taskNumber"
                                        type="text"
                                        value={form.taskNumber}
                                        onChange={handleChange}
                                        required
                                        autoComplete="off"
                                        aria-invalid={Boolean(fieldErrors.taskNumber)}
                                        aria-describedby={fieldErrors.taskNumber
                                            ? 'taskNumber-error' : undefined}
                                    />
                                    {fieldErrors.taskNumber && (
                                        <p id="taskNumber-error" className="task-form-field-error">
                                            {fieldErrors.taskNumber}
                                        </p>
                                    )}
                                </div>

                                <div className="task-form-field">
                                    <label htmlFor="status">Status *</label>
                                    <select
                                        id="status"
                                        name="status"
                                        value={form.status}
                                        onChange={handleChange}
                                        required
                                        aria-invalid={Boolean(fieldErrors.status)}
                                        aria-describedby={fieldErrors.status
                                            ? 'status-error' : undefined}
                                    >
                                        <option value="" disabled>Wybierz status</option>
                                        {form.status && !STATUS_OPTIONS.some(
                                            (option) => option.value === form.status
                                        ) && (
                                            <option value={form.status}>{form.status}</option>
                                        )}
                                        {STATUS_OPTIONS.map((option) => (
                                            <option key={option.value} value={option.value}>
                                                {option.label}
                                            </option>
                                        ))}
                                    </select>
                                    {fieldErrors.status && (
                                        <p id="status-error" className="task-form-field-error">
                                            {fieldErrors.status}
                                        </p>
                                    )}
                                </div>

                                <div className="task-form-field task-form-field-wide">
                                    <label htmlFor="name">Nazwa zadania *</label>
                                    <input
                                        id="name"
                                        name="name"
                                        type="text"
                                        value={form.name}
                                        onChange={handleChange}
                                        required
                                        aria-invalid={Boolean(fieldErrors.name)}
                                        aria-describedby={fieldErrors.name
                                            ? 'name-error' : undefined}
                                    />
                                    {fieldErrors.name && (
                                        <p id="name-error" className="task-form-field-error">
                                            {fieldErrors.name}
                                        </p>
                                    )}
                                </div>

                                <div className="task-form-field task-form-field-wide">
                                    <label htmlFor="description">Opis</label>
                                    <textarea
                                        id="description"
                                        name="description"
                                        rows={5}
                                        value={form.description}
                                        onChange={handleChange}
                                        aria-describedby="description-hint"
                                    />
                                    <p id="description-hint" className="task-form-hint">
                                        Opcjonalne informacje o zakresie zadania.
                                    </p>
                                </div>
                            </div>
                        </fieldset>

                        <div className="task-form-footer">
                            <button
                                type="submit"
                                className="task-form-button task-form-button-primary"
                                disabled={saving}
                            >
                                {saving ? 'Zapisywanie...' : isEditing
                                    ? 'Zapisz zmiany' : 'Utwórz zadanie'}
                            </button>

                            <button
                                type="button"
                                className="task-form-button task-form-button-secondary"
                                onClick={() => navigate(returnPath)}
                                disabled={saving}
                            >
                                Anuluj
                            </button>

                            <span className="task-form-hint" role="status" aria-live="polite">
                                {saving ? 'Trwa zapisywanie zadania.' : ''}
                            </span>
                        </div>
                    </form>
                )}
            </section>
        </>
    );
}
