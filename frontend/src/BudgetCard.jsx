
import { useId, useState } from 'react';
import './BudgetCard.css';

const currencyFormatter = new Intl.NumberFormat('pl-PL', {
    style: 'currency',
    currency: 'PLN',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
});

const percentFormatter = new Intl.NumberFormat('pl-PL', {
    maximumFractionDigits: 1,
});

function toNumber(value) {
    if (value === null || value === undefined || value === '') {
        return null;
    }

    const number = Number(value);
    return Number.isFinite(number) ? number : null;
}

function formatCurrency(value) {
    const number = toNumber(value);
    return number === null ? '—' : currencyFormatter.format(number);
}

function isNegative(value) {
    const number = toNumber(value);
    return number !== null && number < 0;
}

function getProgress(amount, total) {
    const used = toNumber(amount);
    const limit = toNumber(total);

    if (used === null || limit === null) {
        return { width: 0, percentage: null };
    }

    if (limit <= 0) {
        return {
            width: used > 0 ? 100 : 0,
            percentage: used > 0 ? null : 0,
        };
    }

    const percentage = Math.max(0, (used / limit) * 100);

    return {
        width: Math.min(percentage, 100),
        percentage,
    };
}

function FinancialTile({
                           label,
                           value,
                           description,
                           variant = 'default',
                           warning = false,
                       }) {
    return (
        <div
            className={[
                'budget-ui-tile',
                `budget-ui-tile--${variant}`,
                warning ? 'budget-ui-tile--warning' : '',
            ].filter(Boolean).join(' ')}
        >
      <span className="budget-ui-tile__label">
        {label}
      </span>

            <strong className="budget-ui-tile__value">
                {formatCurrency(value)}
            </strong>

            {description && (
                <span className="budget-ui-tile__description">
          {description}
        </span>
            )}

            {warning && (
                <span className="budget-ui-tile__warning">
          Przekroczenie
        </span>
            )}
        </div>
    );
}

function CommitmentProgress({ amount, total, year }) {
    const progress = getProgress(amount, total);

    const percentageLabel =
        progress.percentage === null
            ? '—'
            : `${percentFormatter.format(progress.percentage)}%`;

    return (
        <div className="budget-ui-progress-section">
            <div className="budget-ui-progress-info">
        <span>
          Zaangażowanie budżetu {year}
        </span>

                <strong>{percentageLabel}</strong>
            </div>

            <div
                className="budget-ui-progress-track"
                role="progressbar"
                aria-label={`Zaangażowanie budżetu ${year}`}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={Math.round(progress.width)}
            >
                <div
                    className="budget-ui-progress-fill"
                    style={{ width: `${progress.width}%` }}
                />
            </div>
        </div>
    );
}

function BudgetYearCard({ year, currentYear }) {
    const warning = isNegative(year.availableAmount);
    const isCurrentYear = Number(year.year) === Number(currentYear);

    return (
        <article className="budget-ui-year-card">
            <div className="budget-ui-year-card__header">
                <div className="budget-ui-year-card__heading">
          <span className="budget-ui-year-card__year">
            {year.year}
          </span>

                    {isCurrentYear && (
                        <span className="budget-ui-year-card__current">
              Bieżący rok
            </span>
                    )}
                </div>

                <div className="budget-ui-year-card__budget">
                    <span>Budżet</span>
                    <strong>{formatCurrency(year.amount)}</strong>
                </div>
            </div>

            <div className="budget-ui-year-card__metrics">
                <div className="budget-ui-year-card__metric">
                    <span>Zaangażowano</span>
                    <strong>
                        {formatCurrency(year.committedAmount)}
                    </strong>
                </div>

                <div
                    className={[
                        'budget-ui-year-card__metric',
                        'budget-ui-year-card__metric--available',
                        warning ? 'budget-ui-year-card__metric--warning' : '',
                    ].filter(Boolean).join(' ')}
                >
                    <span>Wolne środki</span>
                    <strong>
                        {formatCurrency(year.availableAmount)}
                    </strong>
                </div>
            </div>

            <CommitmentProgress
                amount={year.committedAmount}
                total={year.amount}
                year={year.year}
            />
        </article>
    );
}

export default function BudgetCard({ budget }) {
    const [expanded, setExpanded] = useState(false);
    const yearsId = useId();

    if (!budget) {
        return null;
    }

    const currentYear =
        budget.currentYear ?? new Date().getFullYear();

    const years = Array.isArray(budget.years)
        ? [...budget.years].sort(
            (a, b) => Number(a.year) - Number(b.year)
        )
        : [];

    const totalExceeded = isNegative(budget.remainingAmount);
    const annualExceeded = isNegative(
        budget.currentYearRemainingAmount
    );
    const commitmentExceeded = isNegative(
        budget.currentYearAvailableAmount
    );

    return (
        <section
            className="budget-ui"
            aria-label="Podsumowanie budżetu zadania"
        >
            {/* NAGŁÓWEK */}
            <header className="budget-ui-header">
                <div>
          <span className="budget-ui-eyebrow">
            FINANSE ZADANIA
          </span>

                    <h3 className="budget-ui-title">
                        Budżet i wykorzystanie środków
                    </h3>

                    <p className="budget-ui-subtitle">
                        Wydatki oraz zaangażowanie finansowe umów głównych.
                    </p>
                </div>

                <span className="budget-ui-currency">
          PLN · brutto
        </span>
            </header>

            {/* PODSUMOWANIE CAŁEGO ZADANIA */}
            <div className="budget-ui-section-heading">
                <h4>Podsumowanie całkowite</h4>
                <span>Wszystkie lata</span>
            </div>

            <div className="budget-ui-total-grid">
                <FinancialTile
                    label="Budżet całkowity"
                    value={budget.totalAmount}
                    description="Łączny plan finansowy zadania"
                    variant="primary"
                />

                <FinancialTile
                    label="Zapłacono od początku"
                    value={budget.paidAmount}
                    description="Faktycznie zrealizowane płatności"
                />

                <FinancialTile
                    label="Pozostało po płatnościach"
                    value={budget.remainingAmount}
                    description="Budżet pomniejszony o wydatki"
                    warning={totalExceeded}
                />
            </div>

            {/* BUDŻET BIEŻĄCEGO ROKU */}
            <div className="budget-ui-annual-section">
                <div className="budget-ui-annual-header">
                    <div>
            <span className="budget-ui-eyebrow">
              ROK BUDŻETOWY
            </span>

                        <h4>
                            Finansowanie w {currentYear}
                        </h4>
                    </div>

                    <div className="budget-ui-annual-budget">
                        <span>Budżet roczny</span>
                        <strong>
                            {formatCurrency(budget.currentYearAmount)}
                        </strong>
                    </div>
                </div>

                <div className="budget-ui-annual-grid">
                    <FinancialTile
                        label="Zapłacono"
                        value={budget.currentYearPaidAmount}
                        description="Wydatki w bieżącym roku"
                    />

                    <FinancialTile
                        label="Pozostało wg płatności"
                        value={budget.currentYearRemainingAmount}
                        description="Budżet minus dokonane płatności"
                        warning={annualExceeded}
                    />

                    <FinancialTile
                        label="Zaangażowanie"
                        value={budget.currentYearCommittedAmount}
                        description="Środki przypisane do umów"
                    />

                    <FinancialTile
                        label="Wolne środki"
                        value={budget.currentYearAvailableAmount}
                        description="Dostępne na nowe zobowiązania"
                        variant="highlight"
                        warning={commitmentExceeded}
                    />
                </div>

                <CommitmentProgress
                    amount={budget.currentYearCommittedAmount}
                    total={budget.currentYearAmount}
                    year={currentYear}
                />

                {commitmentExceeded && (
                    <p className="budget-ui-alert">
                        Zaangażowanie przekracza dostępny budżet roczny.
                        Należy zweryfikować limity finansowe i plan budżetu.
                    </p>
                )}
            </div>

            {/* ROZWINIĘCIE FINANSOWANIA */}
            <div className="budget-ui-details">
                <button
                    type="button"
                    className="budget-ui-toggle"
                    onClick={() => setExpanded((value) => !value)}
                    aria-expanded={expanded}
                    aria-controls={expanded ? yearsId : undefined}
                >
          <span>
            {expanded
                ? 'Ukryj finansowanie w latach'
                : 'Pokaż finansowanie w latach'}
          </span>

                    <span
                        className={[
                            'budget-ui-chevron',
                            expanded ? 'budget-ui-chevron--open' : '',
                        ].filter(Boolean).join(' ')}
                        aria-hidden="true"
                    >
            ⌄
          </span>
                </button>

                {expanded && (
                    <div
                        id={yearsId}
                        className="budget-ui-years"
                    >
                        <div className="budget-ui-years-heading">
                            <h4>Finansowanie w poszczególnych latach</h4>

                            <p>
                                Budżet, zaangażowanie i dostępne środki.
                            </p>
                        </div>

                        {years.length === 0 ? (
                            <p className="budget-ui-empty">
                                Nie określono jeszcze finansowania rocznego.
                            </p>
                        ) : (
                            <div className="budget-ui-years-list">
                                {years.map((year) => (
                                    <BudgetYearCard
                                        key={year.year}
                                        year={year}
                                        currentYear={currentYear}
                                    />
                                ))}
                            </div>
                        )}
                    </div>
                )}
            </div>
        </section>
    );
}
