import { useState } from 'react';

const currencyFormatter = new Intl.NumberFormat('pl-PL', {
  style: 'currency',
  currency: 'PLN',
  maximumFractionDigits: 2,
});

export default function BudgetCard({ budget }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <section className="card budget-card">
      <div className="budget-summary">
        <div>
          <span className="label">Budżet łącznie</span>
          <strong>{currencyFormatter.format(budget.totalAmount)}</strong>
        </div>
        <div>
          <span className="label">Budżet {budget.currentYear}</span>
          <strong>{currencyFormatter.format(budget.currentYearAmount)}</strong>
        </div>
      </div>

      <button
        className="expand-button"
        type="button"
        onClick={() => setExpanded((value) => !value)}
        aria-expanded={expanded}
      >
        {expanded ? 'Zwiń finansowanie' : 'Rozwiń finansowanie'}
      </button>

      {expanded && (
        <div className="budget-years">
          {budget.years.map((year) => (
            <div className="budget-year-row" key={year.year}>
              <span>{year.year}</span>
              <strong>{currencyFormatter.format(year.plannedAmount)}</strong>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
