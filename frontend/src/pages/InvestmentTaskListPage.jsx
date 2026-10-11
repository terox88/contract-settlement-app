import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import http from '../api/http';

export default function InvestmentTaskListPage() {
    const [tasks, setTasks] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        loadTasks();
    }, []);

    async function loadTasks() {
        try {
            setLoading(true);
            setError(null);

            const response =
                await http.get('/investment-tasks');

            setTasks(response.data);

        } catch (error) {
            setError(error.message);

        } finally {
            setLoading(false);
        }
    }

    if (loading) {
        return (
            <p>Ładowanie zadań...</p>
        );
    }

    if (error) {
        return (
            <div className="error-message">
                {error}
            </div>
        );
    }

    return (
        <>
            <div className="page-header">
                <div>
                    <h1>Zadania inwestycyjne</h1>
                    <p>
                        Wybierz zadanie, aby przejść do umów
                        i rozliczeń.
                    </p>
                </div>
            </div>

            {tasks.length === 0 ? (
                <section className="card">
                    <p>Brak zadań inwestycyjnych.</p>
                </section>
            ) : (
                <div className="task-grid">
                    {tasks.map((task) => (
                        <Link
                            key={task.id}
                            to={`/tasks/${task.id}`}
                            className="task-card card"
                        >
              <span className="task-number">
                {task.taskNumber}
              </span>

                            <h2>{task.name}</h2>

                            {task.description && (
                                <p>{task.description}</p>
                            )}
                        </Link>
                    ))}
                </div>
            )}
        </>
    );
}