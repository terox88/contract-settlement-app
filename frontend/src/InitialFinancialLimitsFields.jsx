import { formatBudgetCents, parseBudgetAmount } from './utils/budgetForm';
import { validateInitialFinancialLimits } from './utils/financialLimitForm';

export default function InitialFinancialLimitsFields({ contract, budget, rows, onChange, onReset, error, disabled, onRefresh }) {
    const validation = validateInitialFinancialLimits(contract, budget, rows);
    const total = rows.reduce((sum, row) => sum + (parseBudgetAmount(row.amount)?.cents ?? 0n), 0n);
    function change(index, field, value) {
        onChange(rows.map((row, position) => position === index ? { ...row, [field]: value } : row));
    }
    function addYear() {
        const used = new Set(rows.map((row) => Number(row.year)).filter(Number.isInteger));
        const available = (budget?.years ?? []).map((row) => Number(row.year)).filter((year) => year >= 2000 && year <= 2200)
            .sort((a, b) => a - b).find((year) => !used.has(year));
        let year = available ?? Math.max(Number(contract.contractDate?.slice(0, 4)) || new Date().getFullYear(), ...used) + 1;
        while (used.has(year)) year++;
        if (year > 2200) year = Array.from({ length: 201 }, (_, index) => index + 2000).find((candidate) => !used.has(candidate));
        if (year === undefined) return;
        onChange([...rows, { year: String(year), amount: '0.00' }]);
    }
    return <fieldset className="main-form-section" disabled={disabled}>
        <legend>Limity finansowe na lata *</legend>
        <p className="main-form-hint">Podaj podział wartości brutto umowy na lata. Umowa, wykonawca i limity zostaną zapisane razem.</p>
        {!budget && <p className="error-message">Najpierw zdefiniuj budżet zadania i odpowiednie lata. Wpisane dane możesz zachować w tym formularzu i odświeżyć budżet.</p>}
        {(error || (contract.initialValue.trim() && validation.error)) && <p className="main-form-field-error" role="alert" id="initial-limits-error">{error || validation.error}</p>}
        <div className="initial-limits-rows">{rows.map((row, index) => <div className="initial-limits-row" key={index}>
            <div className="main-form-field"><label htmlFor={`initial-limit-year-${index}`}>Rok *</label>
                <input id={`initial-limit-year-${index}`} name={index === 0 ? 'financialLimitYears' : `limit-year-${index}`} type="number" min="2000" max="2200" required
                    value={row.year} onChange={(event) => change(index, 'year', event.target.value)} aria-describedby="initial-limits-error" /></div>
            <div className="main-form-field"><label htmlFor={`initial-limit-amount-${index}`}>Limit brutto (PLN) *</label>
                <input id={`initial-limit-amount-${index}`} name={`limit-amount-${index}`} inputMode="decimal" required
                    value={row.amount} onChange={(event) => change(index, 'amount', event.target.value)} /></div>
            <button className="main-form-button" type="button" disabled={rows.length <= 1} onClick={() => onChange(rows.filter((_, position) => position !== index))}>Usuń rok</button>
        </div>)}</div>
        <p className="initial-limits-total">Suma limitów: <strong>{formatBudgetCents(total)}</strong></p>
        {!validation.error && validation.uncheckedYears.length > 0 && <p className="main-form-hint">Dostępność środków zostanie dodatkowo sprawdzona przy zapisie.</p>}
        <div className="initial-limits-actions">
            <button className="main-form-button" type="button" onClick={addYear} disabled={rows.length >= 201}>Dodaj rok</button>
            <button className="main-form-button" type="button" onClick={onReset}>Cała wartość w roku zawarcia</button>
            <button className="main-form-button" type="button" onClick={onRefresh}>Odśwież budżet</button>
        </div>
    </fieldset>;
}
