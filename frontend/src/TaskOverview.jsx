import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import useCalendarDate from './hooks/useCalendarDate';
import { formatBudgetCents } from './utils/budgetForm';
import { formatAdministrationDate } from './utils/contractAdministration';
import { summarizeTaskContracts, taskContractAttention } from './utils/taskOverview';
import './TaskOverview.css';

export default function TaskOverview({ contracts }) {
    const today = useCalendarDate();
    const [showAll, setShowAll] = useState(false);
    const totals = useMemo(() => summarizeTaskContracts(contracts), [contracts]);
    const notices = useMemo(() => taskContractAttention(contracts, today), [contracts, today]);
    const visible = showAll ? notices : notices.slice(0, 6);
    return <section className="card task-overview" aria-label="Podsumowanie zadania">
        <div className="task-overview-heading"><h2>Zadanie w liczbach</h2><span>Umowy główne: {contracts.length}</span></div>
        <div className="task-overview-metrics">
            {[
                ['Wartość umów', totals.currentValue], ['Zafakturowano', totals.invoicedAmount],
                ['Zapłacono wykonawcom głównym', totals.paidAmount], ['Z faktur pozostaje do zapłaty', totals.unpaidInvoices],
            ].map(([label, value]) => <div className="task-overview-metric" key={label}><span>{label}</span><strong>{formatBudgetCents(value)}</strong></div>)}
        </div>
        <p className="main-form-hint">Kwoty brutto z umów głównych. Niezapłacona część faktur nie obejmuje przyszłego, jeszcze niezafakturowanego wynagrodzenia.</p>
        <div className="task-overview-heading"><h3>Wymaga uwagi</h3><span>Pozycje: {notices.length}</span></div>
        {notices.length === 0 ? <p className="task-overview-clear">Brak zapisanych terminów wymagających uwagi.</p> : <ul className="task-attention-list">
            {visible.map(notice => <li key={notice.key} className={notice.expired ? 'task-attention-expired' : ''}>
                <Link to={`/main-contracts/${notice.contractId}`}><strong>{notice.contractNumber}</strong> — {notice.label}</Link>
                <span>{formatAdministrationDate(notice.date)} · {notice.text}</span>
            </li>)}
        </ul>}
        {notices.length > 6 && <button type="button" className="main-form-button" aria-expanded={showAll}
            onClick={() => setShowAll(value => !value)}>{showAll ? 'Pokaż pierwsze 6' : `Pokaż wszystkie (${notices.length})`}</button>}
        <p className="main-form-hint">Terminy realizacji, OC, CAR i niezwróconych zabezpieczeń: po terminie lub z ważnością krótszą niż 45 dni.</p>
    </section>;
}
