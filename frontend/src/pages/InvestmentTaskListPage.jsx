import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import http from '../api/http';
import './InvestmentTaskFilters.css';

const FILTERS = [
    {
        value: 'ALL',
        label: 'Wszystkie',
    },
    {
        value: 'IN_PROGRESS',
        label: 'W realizacji',
    },
    {
        value: 'COMPLETED',
        label: 'Zakończone',
    },
    {
        value: 'ON_HOLD',
        label: 'Wstrzymane',
    },
    {
        value: 'CANCELLED',
        label: 'Rezygnacja',
    },
];

const STATUS_LABELS = {
    IN_PROGRESS: 'W realizacji',
    COMPLETED: 'Zakończone',
    ON_HOLD: 'Wstrzymane',
    CANCELLED: 'Rezygnacja',
};

export default function InvestmentTaskListPage() {
    const [tasks, setTasks] =
        useState([]);

    const [selectedStatus, setSelectedStatus] =
        useState('ALL');

    const [loading, setLoading] =
        useState(true);

    const [error, setError] =
        useState(null);

    const [implementerDraft, setImplementerDraft] = useState('');
    const [implementerFilter, setImplementerFilter] = useState('');
    const [attempt, setAttempt] = useState(0);

    useEffect(() => {
        let active = true;
        const controller = new AbortController();
        setLoading(true); setError(null);
        const params = { ...(selectedStatus === 'ALL' ? {} : { status: selectedStatus }),
            ...(implementerFilter ? { implementer: implementerFilter } : {}) };
        http.get('/investment-tasks', { params, signal: controller.signal })
            .then(response => {
                if (!Array.isArray(response.data)) throw new Error('Nie można odczytać listy zadań.');
                if (active) setTasks(response.data);
            })
            .catch(failure => { if (active) setError(failure.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [selectedStatus, implementerFilter, attempt]);

    return (
        <>
            <div className="page-header task-form-list-header">
                <div>
                    <h1>Zadania inwestycyjne</h1>
                    <Link to="/payments" className="main-form-button">Przelewy i rozliczenia</Link>

                    <p>
                        Wybierz zadanie, aby przejść
                        do umów i rozliczeń.
                    </p>
                </div>

                <Link
                    to="/tasks/new"
                    className="task-form-button task-form-button-primary"
                >
                    Dodaj zadanie
                </Link>
            </div>

            <form className="task-implementer-filter" onSubmit={event => {
                event.preventDefault(); setImplementerFilter(implementerDraft.trim());
            }}>
                <div className="task-form-field"><label htmlFor="task-implementer-filter">Realizator</label>
                    <input id="task-implementer-filter" value={implementerDraft} maxLength={255}
                        placeholder="Nazwa lub fragment nazwy działu" onChange={event => setImplementerDraft(event.target.value)} />
                </div>
                <button className="task-form-button" type="submit">Filtruj</button>
                <button className="task-form-button" type="button" onClick={() => { setImplementerDraft(''); setImplementerFilter(''); }}>Wyczyść realizatora</button>
                {implementerFilter && <p role="status">Realizator zawiera: <strong>{implementerFilter}</strong></p>}
            </form>

            <div className="task-filters">
                {FILTERS.map((filter) => (
                    <button
                        key={filter.value}
                        type="button"
                        className={
                            selectedStatus === filter.value
                                ? 'task-filter-button active'
                                : 'task-filter-button'
                        }
                        onClick={() =>
                            setSelectedStatus(
                                filter.value
                            )
                        }
                    >
                        {filter.label}
                    </button>
                ))}
            </div>

            {error && (
                <div className="error-message">
                    {error}

                    <button
                        type="button"
                        className="retry-button"
                        onClick={() => setAttempt(value => value + 1)}
                    >
                        Spróbuj ponownie
                    </button>
                </div>
            )}

            {loading ? (
                <div className="loading-message">
                    Ładowanie zadań...
                </div>
            ) : !error && tasks.length === 0 ? (
                <section className="card empty-state">
                    <h2>Brak zadań</h2>

                    <p>
                        Nie znaleziono zadań
                        odpowiadających wybranemu
                        filtrowi.
                    </p>
                </section>
            ) : !error ? (
                <div className="task-grid">

                    {tasks.map((task) => (
                        <Link
                            key={task.id}
                            to={`/tasks/${task.id}`}
                            className="task-card card"
                        >
                            <div className="task-card-header">

                <span className="task-number">
                  {task.taskNumber}
                </span>

                                <TaskStatusBadge
                                    status={task.status}
                                    label={
                                        task.statusLabel
                                    }
                                />

                            </div>

                            <h2>
                                {task.name}
                            </h2>

                            <p className="task-description"><strong>Realizator:</strong> {task.implementer || 'Nie uzupełniono'}</p>
                            {task.description && (
                                <p className="task-description">
                                    {task.description}
                                </p>
                            )}

                            <div className="task-card-footer">
                <span>
                  Otwórz zadanie
                </span>

                                <span aria-hidden="true">
                  →
                </span>
                            </div>
                        </Link>
                    ))}

                </div>
            ) : null}
        </>
    );
}

function TaskStatusBadge({
                             status,
                             label,
                         }) {

    const statusClass =
        getStatusClass(status);

    const displayLabel =
        label
        ?? STATUS_LABELS[status]
        ?? status
        ?? 'Brak statusu';

    return (
        <span
            className={
                `status-badge ${statusClass}`
            }
        >
      {displayLabel}
    </span>
    );
}

function getStatusClass(status) {
    switch (status) {

        case 'IN_PROGRESS':
            return 'status-in-progress';

        case 'COMPLETED':
            return 'status-completed';

        case 'ON_HOLD':
            return 'status-on-hold';

        case 'CANCELLED':
            return 'status-cancelled';

        default:
            return 'status-unknown';
    }
}