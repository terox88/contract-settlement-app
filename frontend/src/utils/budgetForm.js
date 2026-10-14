const integerFormatter = new Intl.NumberFormat('pl-PL');

export function parseBudgetYear(value) {
    const text = String(value ?? '').trim();
    if (!/^\d+$/.test(text)) return null;

    const year = Number(text);
    // Odpowiada @Min(1900) i zakresowi int w DTO backendu.
    return Number.isInteger(year) && year >= 1900 && year <= 2147483647
        ? year : null;
}

export function parseBudgetAmount(value) {
    const text = String(value ?? '').trim();
    // Przyjmujemy np. 1234,56, 1234.56 oraz 1 234,56.
    // Większej liczby miejsc dziesiętnych nie zaokrąglamy po cichu.
    if (!/^(?:\d+|\d{1,3}(?:[ \u00a0\u202f]+\d{3})+)(?:[,.]\d{1,2})?$/.test(text)) {
        return null;
    }

    const normalized = text.replace(/[ \u00a0\u202f]/g, '').replace(',', '.');
    const [whole, fraction = ''] = normalized.split('.');
    const cents = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));

    return {
        cents,
        // BigDecimal w żądaniu dostaje ciąg dziesiętny bez utraty groszy.
        amount: `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`,
    };
}

export function formatBudgetCents(cents) {
    if (cents === null || cents === undefined) return '—';

    const negative = cents < 0n;
    const absolute = negative ? -cents : cents;
    const whole = integerFormatter.format(absolute / 100n);
    const fraction = String(absolute % 100n).padStart(2, '0');

    return `${negative ? '−' : ''}${whole},${fraction} zł`;
}

export function validateBudgetRows(rows, existingYears = []) {
    const parsedYears = rows.map((row) => parseBudgetYear(row.year));
    const counts = new Map();
    const existing = new Set(existingYears.map(Number));

    for (const year of parsedYears) {
        if (year !== null) counts.set(year, (counts.get(year) ?? 0) + 1);
    }

    const errors = {};
    const years = [];

    rows.forEach((row, index) => {
        const rowErrors = {};
        const year = parsedYears[index];
        const amount = parseBudgetAmount(row.amount);

        if (year === null) {
            rowErrors.year = 'Podaj pełny rok, nie mniejszy niż 1900.';
        } else if (counts.get(year) > 1) {
            rowErrors.year = 'Ten rok występuje w formularzu więcej niż raz.';
        } else if (existing.has(year)) {
            rowErrors.year = 'Ten rok jest już zapisany w budżecie.';
        }

        if (amount === null) {
            rowErrors.amount = 'Podaj kwotę nieujemną, z maksymalnie dwoma miejscami po przecinku.';
        }

        if (Object.keys(rowErrors).length) errors[row.id] = rowErrors;
        else years.push({ year, amount: amount.amount });
    });

    return {
        valid: rows.length > 0 && Object.keys(errors).length === 0,
        errors,
        years,
    };
}

export function buildBudgetRequest(mode, investmentTaskId, years) {
    if (!years.length || (mode !== 'create' && years.length !== 1)) {
        throw new Error('Nieprawidłowa liczba lat do zapisu.');
    }

    const base = `/budgets/investment-task/${investmentTaskId}/years`;

    if (mode === 'create') {
        const numericId = Number(investmentTaskId);
        return {
            method: 'post',
            url: '/budgets',
            data: {
                investmentTaskId: Number.isSafeInteger(numericId)
                    ? numericId : String(investmentTaskId),
                years,
            },
        };
    }

    if (mode === 'add') {
        return { method: 'post', url: base, data: years[0] };
    }

    if (mode === 'edit') {
        return {
            method: 'put',
            url: `${base}/${years[0].year}`,
            data: { amount: years[0].amount },
        };
    }

    throw new Error('Nieprawidłowy tryb formularza budżetu.');
}

export function getBudgetPreview(mode, rows, budget) {
    const result = {
        totalCents: null,
        committedCents: null,
        availableCents: null,
        paidCents: null,
        paymentExceeded: false,
    };

    const amounts = rows.map((row) => parseBudgetAmount(row.amount));
    if (!rows.length || amounts.some((amount) => amount === null)) return result;

    const enteredTotal = amounts.reduce((sum, amount) => sum + amount.cents, 0n);
    if (mode === 'create') {
        result.totalCents = enteredTotal;
        return result;
    }

    const oldTotal = parseBudgetAmount(budget?.totalAmount);
    const year = parseBudgetYear(rows[0].year);
    const savedYear = budget?.years?.find((entry) => Number(entry.year) === year);

    if (mode === 'add') {
        result.totalCents = oldTotal ? oldTotal.cents + enteredTotal : null;
    } else {
        const previousAmount = parseBudgetAmount(savedYear?.amount);
        result.totalCents = oldTotal && previousAmount
            ? oldTotal.cents - previousAmount.cents + enteredTotal : null;
    }

    if (year === null) return result;

    const isCurrentYear = year === Number(budget?.currentYear);
    const commitment = parseBudgetAmount(
        savedYear?.committedAmount
        ?? (isCurrentYear ? budget?.currentYearCommittedAmount : null)
    );

    if (commitment) {
        result.committedCents = commitment.cents;
        result.availableCents = enteredTotal - commitment.cents;
    }

    if (isCurrentYear) {
        const paid = parseBudgetAmount(budget?.currentYearPaidAmount);
        if (paid) {
            result.paidCents = paid.cents;
            result.paymentExceeded = enteredTotal < paid.cents;
        }
    }

    return result;
}

export function isMissingBudget(error, investmentTaskId) {
    if (error.response?.status === 404) return true;

    // Obecny serwis zgłasza brak budżetu jako IllegalArgumentException (400).
    return error.response?.status === 400 && error.message ===
        `Budget for investment task with id ${investmentTaskId} does not exist`;
}
