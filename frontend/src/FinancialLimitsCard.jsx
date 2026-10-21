import { useEffect, useMemo, useRef, useState } from 'react';
import http from './api/http';
import { parseBudgetAmount, formatBudgetCents, isMissingBudget } from './utils/budgetForm';
import { buildFinancialLimitRequest, financialLimitSigningYear, hasFinancialLimitPlan, nextFinancialLimitYear, validateFinancialLimitPlan } from './utils/financialLimitForm';
import './FinancialLimitsCard.css';

function cents(value) { return parseBudgetAmount(value)?.cents ?? null; }
function money(value) { return formatBudgetCents(cents(value)); }
function fromCents(amount) { return `${amount / 100n}.${String(amount % 100n).padStart(2, '0')}`; }

function defaultRow(contract) {
  return {
    key: 'initial',
    year: String(contract.contractDate?.slice(0, 4) || new Date().getFullYear()),
    amount: fromCents(cents(contract.currentValue) ?? 0n),
    original: false,
  };
}

function isMissingLimit(error) {
  if (error?.response?.status === 404) return true;
  const data = error?.response?.data;
  const message = [data?.message, data?.detail, typeof data === 'string' ? data : '']
    .filter(Boolean).join(' ');
  return error?.response?.status === 400 &&
    /financial limit.*does not exist/i.test(message);
}

function errorMessage(error) {
  const data = error?.response?.data;
  if (typeof data === 'string' && data.trim()) return data;
  if (data?.message) return data.message;
  if (data?.detail) return data.detail;
  if (error?.message) return error.message;
  return 'Wystąpił błąd komunikacji z serwerem. Spróbuj ponownie.';
}

let nextRowKey = 0;
function createRow(year) {
  nextRowKey += 1;
  return {
    key: `new-${nextRowKey}`,
    year: String(year),
    amount: '0.00',
    original: false,
  };
}

export default function FinancialLimitsCard({ contract, onChanged }) {
  const [limit, setLimit] = useState(null);
  const [budget, setBudget] = useState(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [rows, setRows] = useState([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reload, setReload] = useState(0);
  const [loadError, setLoadError] = useState('');
  const savingRef = useRef(false);
  const mountedRef = useRef(false);
  useEffect(() => { mountedRef.current = true; return () => { mountedRef.current = false; }; }, []);
  const hasPlan = hasFinancialLimitPlan(limit);

  const contractId = contract?.id;
  const investmentTaskId = contract?.investmentTaskId;
  const contractYear = Number(contract?.contractDate?.slice(0, 4));
  const minimumYear = Math.max(2000, financialLimitSigningYear(contract) ?? 2000);
  const nextYear = nextFinancialLimitYear(contract, rows, budget);
  const currentValueCents = cents(contract?.currentValue);

  useEffect(() => {
    if (!contractId || !investmentTaskId) {
      setLoading(false);
      return;
    }
    let active = true;
    const controller = new AbortController();
    setLoading(true);
    setLoadError('');

    async function load() {
      try {
        const [limitResult, budgetResult] = await Promise.all([
          http.get(`/financial-limits/main-contract/${contractId}`, { signal: controller.signal })
            .then((response) => response.data)
            .catch((err) => {
              if (isMissingLimit(err)) return null;
              throw err;
            }),
          http.get(`/budgets/investment-task/${investmentTaskId}`, { signal: controller.signal })
            .then((response) => response.data)
            .catch((err) => {
              if (isMissingBudget(err, investmentTaskId)) return null;
              throw err;
            }),
        ]);
        if (!active) return;
        if (limitResult && (String(limitResult.mainContractId) !== String(contractId) || !Array.isArray(limitResult.years))) {
          throw new Error('Nieprawidłowa odpowiedź z limitami tej umowy.');
        }
        if (budgetResult && (String(budgetResult.investmentTaskId) !== String(investmentTaskId) || !Array.isArray(budgetResult.years))) {
          throw new Error('Nieprawidłowa odpowiedź z budżetem zadania.');
        }
        setLimit(limitResult);
        setBudget(budgetResult);
      } catch (err) {
        if (active) setLoadError(`Nie można pobrać limitów: ${errorMessage(err)}`);
      } finally {
        if (active) setLoading(false);
      }
    }

    load();
    return () => { active = false; controller.abort(); };
  }, [contractId, investmentTaskId, reload]);

  const displayedYears = useMemo(() => {
    if (hasPlan) return [...limit.years].sort((a, b) => a.year - b.year);
    if (!Number.isInteger(contractYear)) return [];
    return [{ year: contractYear, amount: contract.currentValue }];
  }, [limit, hasPlan, contractYear, contract?.currentValue]);

  const totalLimitCents = hasPlan
    ? limit.years.reduce((total, year) => total + (cents(year.amount) ?? 0n), 0n)
    : currentValueCents;

  function startEditing() {
    setError('');
    setNotice('');
    const nextRows = hasPlan
      ? limit.years.map((year) => ({
          key: `existing-${year.year}`,
          year: String(year.year),
          amount: cents(year.amount) === null ? String(year.amount ?? '') : fromCents(cents(year.amount)),
          original: true,
        }))
      : [defaultRow(contract)];
    setRows(nextRows);
    setEditing(true);
  }

  function updateRow(key, field, value) {
    setRows((prev) => prev.map((row) => row.key === key ? { ...row, [field]: value } : row));
    setError('');
  }

  const validation = validateFinancialLimitPlan(contract, limit, budget, rows);

  async function handleSave(event) {
    event.preventDefault();
    if (savingRef.current || loading || loadError) return;
    const check = validateFinancialLimitPlan(contract, limit, budget, rows);
    if (check.error) { setError(check.error); return; }
    savingRef.current = true;
    setSaving(true); setError(''); setNotice('');
    let saved = false;
    try {
      const request = buildFinancialLimitRequest(contract, limit, budget, rows);
      await http[request.method](request.url, request.data);
      saved = true;
    } catch (err) {
      if (mountedRef.current) setError(`Nie udało się zapisać planu finansowego: ${errorMessage(err)}`);
    } finally {
      savingRef.current = false;
      if (mountedRef.current) setSaving(false);
    }
    if (saved && mountedRef.current) {
      setEditing(false);
      setNotice('Limity finansowe zostały zapisane.');
      setReload((value) => value + 1);
      onChanged?.();
    }
  }

  return (
    <section className="finlim" aria-label="Limity finansowe umowy">
      <div className="finlim__header">
        <div>
          <span className="finlim__eyebrow">PLAN FINANSOWY UMOWY</span>
          <h3>Limity finansowe</h3>
          <p>Podział zaangażowania umowy na lata budżetowe.</p>
        </div>
        {!loading && !loadError && <div className="finlim__actions">
          {!editing && <button type="button" className="finlim__button finlim__button--primary" onClick={startEditing}>
            {hasPlan ? 'Edytuj limity' : 'Ustal limity'}
          </button>}
          <button type="button" className="finlim__button" disabled={saving} onClick={() => { setError(''); setReload((value) => value + 1); }}>Odśwież</button>
        </div>}
      </div>

      {loading ? (
        <p className="finlim__muted">Wczytywanie limitów finansowych…</p>
      ) : loadError ? <div className="finlim__alert" role="alert">{loadError}
        <button type="button" className="finlim__button" onClick={() => setReload((value) => value + 1)}>Spróbuj ponownie</button>
      </div> : (
        <>
          {error && <div className="finlim__alert" role="alert">{error}</div>}
          {notice && <div className="finlim__notice" role="status">{notice}</div>}
          {!editing && (
            <>
              <div className="finlim__summary">
                <div>
                  <span>Aktualna wartość umowy</span>
                  <strong>{money(contract.currentValue)}</strong>
                </div>
                <div>
                  <span>{hasPlan ? 'Suma limitów' : 'Zaangażowanie'}</span>
                  <strong>{formatBudgetCents(totalLimitCents)}</strong>
                </div>
              </div>
              {!hasPlan && (
                <p className="finlim__hint">
                  Bez podziału na lata cała wartość umowy obciąża rok jej zawarcia. Ustal limity, aby rozłożyć zaangażowanie na lata budżetowe.
                </p>
              )}
              <div className="finlim__year-list">
                {displayedYears.map((year) => (
                  <div className="finlim__year" key={year.year}>
                    <span>{year.year}</span>
                    <strong>{money(year.amount)}</strong>
                  </div>
                ))}
              </div>
              {hasPlan && totalLimitCents !== currentValueCents && (
                <div className="finlim__alert" role="alert">
                  Suma limitów różni się od aktualnej wartości umowy. Sprawdź podział finansowania po aneksach.
                </div>
              )}
            </>
          )}

          {editing && (
            <form className="finlim__form" onSubmit={handleSave}>
              <p className="finlim__hint">
                Suma limitów powinna odpowiadać aktualnej wartości umowy. Aby wycofać istniejący rok, ustaw jego kwotę na 0 zł.
              </p>
              <p className="finlim__hint">Limity można ustalić od roku zawarcia umowy ({contractYear}). Jeżeli wcześniej zapisano limit na starszy rok, wyzeruj go i przenieś kwotę na właściwe lata.</p>
              {validation.uncheckedYears.length > 0 && !validation.error && <p className="finlim__hint">
                Dostępność środków w latach {validation.uncheckedYears.join(', ')} zostanie sprawdzona przy zapisie.
              </p>}
              <div className="finlim__form-rows">
                {rows.map((row) => (
                  <div className="finlim__form-row" key={row.key}>
                    <label>
                      <span>Rok</span>
                      <input
                        type="number"
                        min={minimumYear}
                        max="2200"
                        step="1"
                        value={row.year}
                        disabled={saving || row.original}
                        onChange={(event) => updateRow(row.key, 'year', event.target.value)}
                        required
                      />
                    </label>
                    <label>
                      <span>Limit brutto (PLN)</span>
                      <input
                        type="text"
                        inputMode="decimal"
                        value={row.amount}
                        disabled={saving}
                        onChange={(event) => updateRow(row.key, 'amount', event.target.value)}
                        placeholder="0,00"
                        required
                      />
                    </label>
                    {!row.original && (
                      <button
                        type="button"
                        className="finlim__button finlim__button--remove"
                        onClick={() => setRows((prev) => prev.filter((item) => item.key !== row.key))}
                        disabled={saving}
                        aria-label={`Usuń rok ${row.year}`}
                      >
                        Usuń
                      </button>
                    )}
                  </div>
                ))}
              </div>
              <div className="finlim__form-footer">
                <button type="button" className="finlim__button" onClick={() => { setEditing(false); setError(''); }} disabled={saving}>
                  Anuluj
                </button>
                <button type="button" className="finlim__button" onClick={() => { if (nextYear !== null) setRows((prev) => [...prev, createRow(nextYear)]); }} disabled={saving || nextYear === null}>
                  + Dodaj rok
                </button>
                <button type="submit" className="finlim__button finlim__button--primary" disabled={saving || !budget}>
                  {saving ? 'Zapisywanie…' : 'Zapisz limity'}
                </button>
              </div>
              {!budget && <p className="finlim__hint">Aby edytować limity, najpierw utwórz budżet zadania.</p>}
            </form>
          )}
        </>
      )}
    </section>
  );
}
