import { parseBudgetAmount, formatBudgetCents } from './budgetForm.js';
import { parseAmendmentAmount } from './contractAmendmentForm.js';

export function hasFinancialLimitPlan(limit) {
    return Array.isArray(limit?.years) && limit.years.length > 0;
}

export function originalCommitments(contract, limit) {
    const original = new Map();
    if (hasFinancialLimitPlan(limit)) {
        for (const row of limit.years) {
            const amount = parseBudgetAmount(row.amount);
            if (!amount) return null;
            original.set(Number(row.year), amount.cents);
        }
    } else {
        const year = Number(contract.contractDate?.slice(0, 4));
        const amount = parseBudgetAmount(contract.currentValue);
        if (!Number.isInteger(year) || !amount) return null;
        original.set(year, amount.cents);
    }
    return original;
}

/** Brak podsumowania rocznego oznacza odroczenie kontroli do serwera, a nie zero wolnych środków. */
export function annualAvailableCents(budget, row) {
    const direct = parseAmendmentAmount(row.availableAmount);
    if (direct) return direct.cents;
    const amount = parseBudgetAmount(row.amount);
    const committed = parseBudgetAmount(row.committedAmount);
    if (amount && committed) return amount.cents - committed.cents;
    if (Number(row.year) === Number(budget.currentYear)) {
        const currentAvailable = parseAmendmentAmount(budget.currentYearAvailableAmount);
        if (currentAvailable) return currentAvailable.cents;
        const currentCommitted = parseBudgetAmount(budget.currentYearCommittedAmount);
        if (amount && currentCommitted) return amount.cents - currentCommitted.cents;
    }
    return null;
}

export function validateFinancialLimitPlan(contract, limit, budget, rows, { newContract = false } = {}) {
    const result = { error: null, years: [], uncheckedYears: [] };
    const fail = (message) => ({ ...result, error: message });
    if (!budget) return fail('Zadanie nie ma zdefiniowanego budżetu rocznego. Najpierw uzupełnij budżet.');
    if (!Array.isArray(budget.years)) return fail('Nie można odczytać lat budżetu. Odśwież dane.');
    const current = parseBudgetAmount(contract.currentValue);
    if (!current) return fail('Nie można odczytać aktualnej wartości umowy.');
    if (!rows.length) return fail('Dodaj co najmniej jeden rok limitu.');
    const proposed = new Map();
    let total = 0n;
    for (const row of rows) {
        const year = Number(row.year);
        if (!/^\d{4}$/.test(String(row.year)) || !Number.isInteger(year) || year < 2000 || year > 2200) {
            return fail('Podaj poprawny rok (2000–2200).');
        }
        if (proposed.has(year)) return fail(`Rok ${year} został podany więcej niż raz.`);
        const amount = parseBudgetAmount(row.amount);
        if (!amount) return fail(`Podaj poprawną, nieujemną kwotę dla roku ${year} (maksymalnie 2 miejsca po przecinku).`);
        if (amount.cents > 999999999999999n) return fail(`Kwota dla roku ${year} przekracza dopuszczalny zakres.`);
        proposed.set(year, amount.cents); total += amount.cents;
        result.years.push({ year, amount: amount.amount });
    }
    if (total !== current.cents) return fail(`Suma limitów musi być równa aktualnej wartości umowy (${formatBudgetCents(current.cents)}).`);
    const before = newContract ? new Map() : originalCommitments(contract, limit);
    if (!before) return fail('Nie można odczytać dotychczasowego zaangażowania umowy. Odśwież dane.');
    const budgetYears = new Map(budget.years.map((row) => [Number(row.year), row]));
    for (const [year, amount] of proposed) {
        const budgetYear = budgetYears.get(year);
        // Zerowy limit może zwalniać rok, dla którego nie ma już finansowania.
        if (!budgetYear && amount > 0n) return fail(`Brak budżetu zadania na rok ${year}. Najpierw dodaj rok do budżetu.`);
        const previous = before.get(year) ?? 0n;
        // Istniejące przeciążenie wolno zmniejszyć albo pozostawić bez pogorszenia.
        if (amount <= previous) continue;
        const available = budgetYear ? annualAvailableCents(budget, budgetYear) : null;
        if (available === null) { result.uncheckedYears.push(year); continue; }
        const capacity = available + previous;
        if (amount > capacity) {
            return fail(`Za mało wolnych środków na ${year}. Maksymalny limit tej umowy przy obecnym budżecie: ${formatBudgetCents(capacity > 0n ? capacity : 0n)}.`);
        }
    }
    return result;
}

export function validateInitialFinancialLimits(contract, budget, rows) {
    return validateFinancialLimitPlan({ ...contract, currentValue: contract.initialValue }, null, budget, rows, { newContract: true });
}

export function buildFinancialLimitRequest(contract, limit, budget, rows) {
    const validation = validateFinancialLimitPlan(contract, limit, budget, rows);
    if (validation.error) throw new Error(validation.error);
    return { method: 'put', url: `/financial-limits/main-contract/${contract.id}/plan`, data: { years: validation.years } };
}
