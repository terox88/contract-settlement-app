import { useState } from 'react';

const currencyFormatter =
    new Intl.NumberFormat(
        'pl-PL',
        {
            style: 'currency',
            currency: 'PLN',
            maximumFractionDigits: 2,
        }
    );

export default function BudgetCard({
                                       budget,
                                   }) {
    const [expanded, setExpanded] =
        useState(false);

    const totalExceeded =
        Number(budget.remainingAmount ?? 0) < 0;

    const currentYearExceeded =
        Number(
            budget.currentYearRemainingAmount ?? 0
        ) < 0;

    return (
        <section className="card budget-card">

            <div className="budget-block">

                <div className="budget-block-header">
                    <div>
            <span className="label">
              Budżet zadania
            </span>

                        <strong className="budget-main-value">
                            {formatCurrency(
                                budget.totalAmount
                            )}
                        </strong>
                    </div>

                    <span className="budget-period-label">
            Łącznie
          </span>
                </div>

                <div className="budget-metrics">

                    <BudgetMetric
                        label="Zapłacono"
                        value={budget.paidAmount}
                    />

                    <BudgetMetric
                        label="Pozostało"
                        value={budget.remainingAmount}
                        warning={totalExceeded}
                    />

                </div>

                {totalExceeded && (
                    <div className="budget-warning">
                        Budżet zadania został przekroczony
                        o{' '}
                        {formatCurrency(
                            Math.abs(
                                Number(
                                    budget.remainingAmount
                                )
                            )
                        )}
                    </div>
                )}

            </div>

            <div className="budget-section-divider" />

            <div className="budget-block">

                <div className="budget-block-header">

                    <div>
            <span className="label">
              Budżet na rok
            </span>

                        <strong className="budget-main-value">
                            {formatCurrency(
                                budget.currentYearAmount
                            )}
                        </strong>
                    </div>

                    <span className="budget-period-label">
            {budget.currentYear}
          </span>

                </div>

                <div className="budget-metrics">

                    <BudgetMetric
                        label={`Zapłacono w ${budget.currentYear}`}
                        value={
                            budget.currentYearPaidAmount
                        }
                    />

                    <BudgetMetric
                        label={`Pozostało w ${budget.currentYear}`}
                        value={
                            budget.currentYearRemainingAmount
                        }
                        warning={
                            currentYearExceeded
                        }
                    />

                </div>

                {currentYearExceeded && (
                    <div className="budget-warning">
                        Budżet na {budget.currentYear}
                        {' '}został przekroczony o{' '}
                        {formatCurrency(
                            Math.abs(
                                Number(
                                    budget
                                        .currentYearRemainingAmount
                                )
                            )
                        )}
                    </div>
                )}

            </div>

            <button
                className="expand-button"
                type="button"
                onClick={() =>
                    setExpanded(
                        (value) => !value
                    )
                }
                aria-expanded={expanded}
            >
                {expanded
                    ? 'Zwiń finansowanie'
                    : 'Pokaż finansowanie w latach'}
            </button>

            {expanded && (
                <div className="budget-years">

                    {budget.years.map(
                        (year) => (
                            <div
                                className="budget-year-row"
                                key={year.year}
                            >
                <span>
                  {year.year}
                </span>

                                <strong>
                                    {formatCurrency(
                                        year.amount
                                    )}
                                </strong>
                            </div>
                        )
                    )}

                </div>
            )}

        </section>
    );
}

function BudgetMetric({
                          label,
                          value,
                          warning = false,
                      }) {
    return (
        <div className="budget-metric">

      <span className="label">
        {label}
      </span>

            <strong
                className={
                    warning
                        ? 'budget-metric-value budget-value-negative'
                        : 'budget-metric-value'
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