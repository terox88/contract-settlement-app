import { useEffect, useMemo, useState } from 'react';
import http from './api/http';
import './FinancialLimitsCard.css';


const formatter = new Intl.NumberFormat('pl-PL', {
  style: 'currency',
  currency: 'PLN',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

// Work in integer grosze to avoid floating-point errors when comparing amounts.
function cents(value) {
  if (value === null || value === undefined) return null;
  const normalized = String(value)
    .replace(/[\s\u00a0\u202f]/g, '')
    .replace(',', '.');
  const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(normalized);
  if (!match) return null;
  const amount = Number(match[1]) * 100 + Number((match[2] || '').padEnd(2, '0'));
  return Number.isSafeInteger(amount) ? amount : null;
}

function money(value) {
  const amount = cents(value);
  return amount === null ? '—' : formatter.format(amount / 100);
}

function fromCents(amount) {
  return (amount / 100).toFixed(2);
}

function defaultRow(contract) {
  return {
    key: 'initial',
    year: String(contract.contractDate?.slice(0, 4) || new Date().getFullYear()),
    amount: fromCents(cents(contract.currentValue) ?? 0),
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
  return 'Wystąpił błąd komunikacji z serwerem. Spróbuj ponownie.';
}

let nextRowKey = 0;
function createRow() {
  nextRowKey += 1;
  return {
    key: `new-${nextRowKey}`,
    year: String(new Date().getFullYear() + 1),
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

  const contractId = contract?.id;
  const investmentTaskId = contract?.investmentTaskId;
  const contractYear = Number(contract?.contractDate?.slice(0, 4));
  const currentValueCents = cents(contract?.currentValue);

  useEffect(() => {
    if (!contractId || !investmentTaskId) {
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    setError('');

    async function load() {
      try {
        const [limitResult, budgetResult] = await Promise.all([
          http.get(`/financial-limits/main-contract/${contractId}`)
            .then((response) => response.data)
            .catch((err) => {
              if (isMissingLimit(err)) return null;
              throw err;
            }),
          http.get(`/budgets/investment-task/${investmentTaskId}`)
            .then((response) => response.data)
            .catch((err) => {
              if (err?.response?.status === 404) return null;
              throw err;
            }),
        ]);
        if (!active) return;
        setLimit(limitResult);
        setBudget(budgetResult);
      } catch (err) {
        if (active) setError(`Nie można pobrać limitów: ${errorMessage(err)}`);
      } finally {
        if (active) setLoading(false);
      }
    }

    load();
    return () => { active = false; };
  }, [contractId, investmentTaskId, reload]);

  const displayedYears = useMemo(() => {
    if (limit) return [...(limit.years ?? [])].sort((a, b) => a.year - b.year);
    if (!Number.isInteger(contractYear)) return [];
    return [{ year: contractYear, amount: contract.currentValue }];
  }, [limit, contractYear, contract?.currentValue]);

  const totalLimitCents = limit
    ? (limit.years ?? []).reduce((total, year) => total + (cents(year.amount) ?? 0), 0)
    : currentValueCents;

  function startEditing() {
    setError('');
    setNotice('');
    const nextRows = limit
      ? (limit.years ?? []).map((year) => ({
          key: `existing-${year.year}`,
          year: String(year.year),
          amount: fromCents(cents(year.amount) ?? 0),
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

  function validate() {
    if (!budget) return 'Zadanie nie ma zdefiniowanego budżetu rocznego. Najpierw uzupełnij budżet.';
    if (currentValueCents === null) return 'Nie można odczytać aktualnej wartości umowy.';
    if (rows.length === 0) return 'Dodaj co najmniej jeden rok limitu.';

    const proposed = new Map();
    let totalCents = 0;
    for (const row of rows) {
      const year = Number(row.year);
      const amountCents = cents(row.amount);
      if (!Number.isInteger(year) || year < 2000 || year > 2200) {
        return 'Podaj poprawny rok (2000–2200).';
      }
      if (proposed.has(year)) return `Rok ${year} został podany więcej niż raz.`;
      if (amountCents === null) return `Podaj poprawną, nieujemną kwotę dla roku ${year} (maksymalnie 2 miejsca po przecinku).`;
      proposed.set(year, amountCents);
      totalCents += amountCents;
    }
    if (totalCents !== currentValueCents) {
      return `Suma limitów musi być równa aktualnej wartości umowy (${money(contract.currentValue)}).`;
    }

    const original = new Map();
    if (limit) {
      for (const year of limit.years ?? []) original.set(Number(year.year), cents(year.amount) ?? 0);
    } else if (Number.isInteger(contractYear)) {
      original.set(contractYear, currentValueCents);
    }

    const budgetYears = new Map((budget.years ?? []).map((year) => [Number(year.year), year]));
    for (const [year, newAmount] of proposed) {
      const budgetYear = budgetYears.get(year);
      if (!budgetYear) return `Brak budżetu zadania na rok ${year}. Najpierw dodaj rok do budżetu.`;
      const available = cents(budgetYear.availableAmount);
      if (available === null) return `Brak informacji o wolnych środkach w roku ${year}.`;
      // Current free budget already includes this contract's OLD commitment.
      const capacity = available + (original.get(year) ?? 0);
      if (newAmount > capacity) {
        return `Za mało wolnych środków na ${year}. Maksymalny limit tej umowy przy obecnym budżecie: ${formatter.format(Math.max(0, capacity) / 100)}.`;
      }
    }
    return null;
  }

  async function handleSave(event) {
    event.preventDefault();
    if (saving) return;
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setSaving(true);
    setError('');
    setNotice('');
    const prepared = rows.map((row) => ({
      year: Number(row.year),
      amount: cents(row.amount) / 100,
    }));

    try {
      if (!limit) {
        await http.post('/financial-limits', {
          mainContractId: contractId,
          years: prepared,
        });
      } else {
        // The current API saves years separately (no atomic replace endpoint).
        const before = new Map((limit.years ?? []).map((year) => [Number(year.year), cents(year.amount)]));
        const updated = prepared.filter((year) =>
          before.has(year.year) && cents(year.amount) !== before.get(year.year)
        );
        const added = prepared.filter((year) => !before.has(year.year));

        // Reduce existing commitments before increasing others.
        const decreasing = updated.filter((year) => cents(year.amount) < before.get(year.year));
        const increasing = updated.filter((year) => cents(year.amount) > before.get(year.year));
        for (const year of decreasing) {
          await http.put(`/financial-limits/main-contract/${contractId}/years/${year.year}`, { amount: year.amount });
        }
        for (const year of increasing) {
          await http.put(`/financial-limits/main-contract/${contractId}/years/${year.year}`, { amount: year.amount });
        }
        for (const year of added) {
          await http.post(`/financial-limits/main-contract/${contractId}/years`, year);
        }
      }
      setEditing(false);
      setNotice('Limity finansowe zostały zapisane.');
      setReload((value) => value + 1);
      onChanged?.();
    } catch (err) {
      setEditing(false);
      setError(`Nie udało się zapisać wszystkich zmian: ${errorMessage(err)}. Część lat mogła zostać zmieniona — sprawdź aktualne limity.`);
      setReload((value) => value + 1);
      onChanged?.();
    } finally {
      setSaving(false);
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
        {!editing && !loading && !error && (
          <button type="button" className="finlim__button finlim__button--primary" onClick={startEditing}>
            {limit ? 'Edytuj limity' : 'Ustal limity'}
          </button>
        )}
      </div>

      {loading ? (
        <p className="finlim__muted">Wczytywanie limitów finansowych…</p>
      ) : (
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
                  <span>{limit ? 'Suma limitów' : 'Zaangażowanie'}</span>
                  <strong>{money((totalLimitCents ?? 0) / 100)}</strong>
                </div>
              </div>
              {!limit && (
                <p className="finlim__hint">
                  Umowa roczna
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
              {limit && totalLimitCents !== currentValueCents && (
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
              <div className="finlim__form-rows">
                {rows.map((row) => (
                  <div className="finlim__form-row" key={row.key}>
                    <label>
                      <span>Rok</span>
                      <input
                        type="number"
                        min="2000"
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
                <button type="button" className="finlim__button" onClick={() => setRows((prev) => [...prev, createRow()])} disabled={saving}>
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
