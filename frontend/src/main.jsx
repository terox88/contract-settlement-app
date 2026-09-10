import React from 'react';
import ReactDOM from 'react-dom/client';
import BudgetCard from './BudgetCard';
import './styles.css';

const exampleBudget = {
  totalAmount: 8000000,
  currentYear: 2026,
  currentYearAmount: 1500000,
  years: [
    { year: 2026, plannedAmount: 1500000 },
    { year: 2027, plannedAmount: 4000000 },
    { year: 2028, plannedAmount: 2500000 },
  ],
};

function App() {
  return (
    <main className="container">
      <h1>Rozliczenia umów</h1>
      <p>Starter aplikacji działa.</p>

      <section className="card">
        <h2>Przykładowe zadanie inwestycyjne</h2>
        <p>Budowa i wyposażenie obiektu</p>
      </section>

      <BudgetCard budget={exampleBudget} />
    </main>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode><App /></React.StrictMode>
);
