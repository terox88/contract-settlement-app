import { useEffect, useState } from 'react';
import {
    Link,
    useParams,
} from 'react-router-dom';
import http from '../api/http';
import BudgetCard from '../BudgetCard';

export default function InvestmentTaskDetailsPage() {
    const { taskId } = useParams();

    const [task, setTask] =
        useState(null);

    const [budget, setBudget] =
        useState(null);

    const [loading, setLoading] =
        useState(true);

    const [error, setError] =
        useState(null);

    useEffect(() => {
        loadData();
    }, [taskId]);

    async function loadData() {
        try {
            setLoading(true);
            setError(null);

            const taskResponse =
                await http.get(
                    `/investment-tasks/${taskId}`
                );

            setTask(taskResponse.data);

            try {

                const budgetResponse =
                    await http.get(
                        `/budgets/investment-task/${taskId}`
                    );

                setBudget(
                    normalizeBudget(
                        budgetResponse.data
                    )
                );

            } catch (budgetError) {

                /*
                 * Brak budżetu nie powinien blokować
                 * wyświetlenia całego zadania.
                 */
                setBudget(null);
            }

        } catch (error) {
            setError(error.message);

        } finally {
            setLoading(false);
        }
    }

    if (loading) {
        return (
            <p>Ładowanie zadania...</p>
        );
    }

    if (error) {
        return (
            <div className="error-message">
                {error}
            </div>
        );
    }

    if (!task) {
        return null;
    }

    return (
        <>
            <Link
                to="/tasks"
                className="back-link"
            >
                ← Zadania inwestycyjne
            </Link>

            <section className="card task-details">
        <span className="task-number">
          {task.taskNumber}
        </span>

                <h1>{task.name}</h1>

                {task.description && (
                    <p>{task.description}</p>
                )}
            </section>

            <h2 className="section-title">
                Budżet
            </h2>

            {budget ? (
                <BudgetCard budget={budget} />
            ) : (
                <section className="card">
                    <p>
                        Dla tego zadania nie określono jeszcze
                        budżetu.
                    </p>
                </section>
            )}

            <h2 className="section-title">
                Umowy główne
            </h2>

            <section className="card">
                <p>
                    W następnym kroku podłączymy tutaj
                    umowy główne zadania.
                </p>
            </section>
        </>
    );
}

function normalizeBudget(data) {
    const currentYear =
        new Date().getFullYear();

    const years =
        (data.years ?? []).map((year) => ({
            ...year,

            /*
             * Obsługa zarówno obecnego amount,
             * jak i starego plannedAmount ze startera.
             */
            amount:
                year.amount
                ?? year.plannedAmount
                ?? 0,
        }));

    const currentYearAmount =
        data.currentYearAmount
        ?? years.find(
            (year) => year.year === currentYear
        )?.amount
        ?? 0;

    return {
        ...data,
        years,
        currentYear:
            data.currentYear
            ?? currentYear,
        currentYearAmount,
    };
}