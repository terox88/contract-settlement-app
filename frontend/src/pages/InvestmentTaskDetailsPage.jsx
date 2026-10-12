import {
    useEffect,
    useState,
} from 'react';

import {
    Link,
    useParams,
} from 'react-router-dom';

import http from '../api/http';
import BudgetCard from '../BudgetCard';

const currencyFormatter =
    new Intl.NumberFormat(
        'pl-PL',
        {
            style: 'currency',
            currency: 'PLN',
            maximumFractionDigits: 2,
        }
    );

const dateFormatter =
    new Intl.DateTimeFormat(
        'pl-PL'
    );

const CONTRACT_TYPE_LABELS = {
    SERVICE: 'Usługa',
    DESIGN_AND_BUILD:
        'Zaprojektuj i wybuduj',
    CONSTRUCTION_WORKS:
        'Roboty budowlane',
    CONSTRUCTION:
        'Roboty budowlane',
};

const REMUNERATION_TYPE_LABELS = {
    LUMP_SUM: 'Ryczałt',
    COST_ESTIMATE: 'Kosztorysowe',
    MEASURED: 'Obmiarowe',
};

export default function InvestmentTaskDetailsPage() {
    const { taskId } = useParams();

    const [task, setTask] =
        useState(null);

    const [budget, setBudget] =
        useState(null);

    const [contracts, setContracts] =
        useState([]);

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

            /*
             * Zadanie oraz umowy pobieramy równolegle.
             */
            const [
                taskResponse,
                contractsResponse,
            ] = await Promise.all([
                http.get(
                    `/investment-tasks/${taskId}`
                ),

                http.get(
                    '/main-contracts',
                    {
                        params: {
                            investmentTaskId:
                            taskId,
                        },
                    }
                ),
            ]);

            setTask(
                taskResponse.data
            );

            /*
             * Dodatkowo filtrujemy po stronie
             * frontendu.
             *
             * Dzięki temu nawet gdyby backend
             * zignorował parametr filtra,
             * nie pokażemy umów z innego zadania.
             */
            const taskContracts =
                contractsResponse.data.filter(
                    (contract) =>
                        String(
                            contract.investmentTaskId
                        ) === String(taskId)
                );

            setContracts(
                taskContracts
            );

            /*
             * Brak budżetu nie blokuje
             * całej strony zadania.
             */
            try {

                const budgetResponse =
                    await http.get(
                        `/budgets/investment-task/${taskId}`
                    );

                setBudget(
                    budgetResponse.data
                );

            } catch {
                setBudget(null);
            }

        } catch (error) {

            setError(
                error.message
            );

        } finally {

            setLoading(false);
        }
    }

    if (loading) {
        return (
            <div className="loading-message">
                Ładowanie zadania...
            </div>
        );
    }

    if (error) {
        return (
            <div className="error-message">
                {error}

                <button
                    type="button"
                    className="retry-button"
                    onClick={loadData}
                >
                    Spróbuj ponownie
                </button>
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

                <div className="task-details-header">

                    <div>

            <span className="task-number">
              {task.taskNumber}
            </span>

                        <h1>
                            {task.name}
                        </h1>

                    </div>

                    {task.statusLabel && (
                        <span className="task-details-status">
              {task.statusLabel}
            </span>
                    )}

                </div>

                {task.description && (
                    <p className="task-details-description">
                        {task.description}
                    </p>
                )}

            </section>

            <div className="section-heading">
                <div>
                    <h2>Budżet</h2>

                    <p>
                        Plan finansowy i faktycznie
                        zrealizowane płatności
                        dla umów głównych.
                    </p>
                </div>
            </div>

            {budget ? (
                <BudgetCard
                    budget={budget}
                />
            ) : (
                <section className="card empty-state">
                    <h3>
                        Brak budżetu
                    </h3>

                    <p>
                        Dla tego zadania nie
                        określono jeszcze budżetu.
                    </p>
                </section>
            )}

            <div className="section-heading contracts-heading">

                <div>
                    <h2>Umowy główne</h2>

                    <p>
                        Umowy zawarte w ramach
                        tego zadania inwestycyjnego.
                    </p>
                </div>

                <span className="contracts-count">
          {contracts.length}
        </span>

            </div>

            {contracts.length === 0 ? (
                <section className="card empty-state">

                    <h3>
                        Brak umów głównych
                    </h3>

                    <p>
                        Do tego zadania nie
                        przypisano jeszcze żadnej
                        umowy głównej.
                    </p>

                </section>
            ) : (
                <div className="contracts-list">

                    {contracts.map(
                        (contract) => (
                            <MainContractCard
                                key={contract.id}
                                contract={contract}
                            />
                        )
                    )}

                </div>
            )}
        </>
    );
}

function MainContractCard({
                              contract,
                          }) {

    const penaltyAmount =
        Number(
            contract.totalPenaltyAmount ?? 0
        );

    return (
        <section className="card main-contract-card">

            <div className="contract-card-header">

                <div>

          <span className="contract-number">
            {contract.contractNumber}
          </span>

                    <h3>
                        {contract.mainContractorName}
                    </h3>

                    {contract.shortDescription && (
                        <p className="contract-description">
                            {contract.shortDescription}
                        </p>
                    )}

                </div>

                <div className="contract-type">
                    {formatContractType(
                        contract.contractType
                    )}
                </div>

            </div>

            <div className="contract-meta">

                <div>
          <span className="label">
            Data umowy
          </span>

                    <strong>
                        {formatDate(
                            contract.contractDate
                        )}
                    </strong>
                </div>

                <div>
          <span className="label">
            Termin realizacji
          </span>

                    <strong>
                        {formatDate(
                            contract.currentEndDate
                        )}
                    </strong>
                </div>

                <div>
          <span className="label">
            Wynagrodzenie
          </span>

                    <strong>
                        {formatRemunerationType(
                            contract.remunerationType
                        )}
                    </strong>
                </div>

            </div>

            <div className="contract-financials">

                <ContractFinancialMetric
                    label="Wartość umowy"
                    value={
                        contract.currentValue
                    }
                />

                <ContractFinancialMetric
                    label="Zafakturowano"
                    value={
                        contract.invoicedAmount
                    }
                />

                <ContractFinancialMetric
                    label="Zapłacono"
                    value={
                        contract.paidAmount
                    }
                />

                <ContractFinancialMetric
                    label="Pozostało do zapłaty"
                    value={
                        contract.remainingToPay
                    }
                    warning={
                        Number(
                            contract.remainingToPay ?? 0
                        ) < 0
                    }
                />

            </div>

            {penaltyAmount > 0 && (
                <div className="contract-penalty">
                    Kary umowne:{' '}
                    <strong>
                        {formatCurrency(
                            penaltyAmount
                        )}
                    </strong>
                </div>
            )}

        </section>
    );
}

function ContractFinancialMetric({
                                     label,
                                     value,
                                     warning = false,
                                 }) {
    return (
        <div className="contract-financial-metric">

      <span className="label">
        {label}
      </span>

            <strong
                className={
                    warning
                        ? 'contract-financial-value value-warning'
                        : 'contract-financial-value'
                }
            >
                {formatCurrency(value)}
            </strong>

        </div>
    );
}

function formatCurrency(value) {
    return currencyFormatter.format(
        Number(value ?? 0)
    );
}

function formatDate(value) {
    if (!value) {
        return '—';
    }

    return dateFormatter.format(
        new Date(
            `${value}T00:00:00`
        )
    );
}

function formatContractType(type) {
    return (
        CONTRACT_TYPE_LABELS[type]
        ?? type
        ?? '—'
    );
}

function formatRemunerationType(type) {
    return (
        REMUNERATION_TYPE_LABELS[type]
        ?? type
        ?? '—'
    );
}