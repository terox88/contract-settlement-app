import { formatBudgetCents, parseBudgetAmount } from './budgetForm.js';
import { validDate } from './mainContractForm.js';

const MAX_CHANGE_CENTS = 999999999999999n;
const dateFormatter = new Intl.DateTimeFormat('pl-PL');

export function emptyAmendment() {
    const date = new Date();
    const today = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
    return {
        amendmentNumber: '', amendmentDate: today, effectiveDate: today,
        valueChange: '', newEndDate: '', description: '',
    };
}

export function amendmentToForm(data) {
    return Object.fromEntries(Object.keys(emptyAmendment()).map((field) => [
        field, String(data[field] ?? ''),
    ]));
}

export function parseAmendmentAmount(value) {
    const text = String(value ?? '').trim().replace(/^−/, '-');
    const negative = text.startsWith('-');
    const signed = negative || text.startsWith('+');
    const amount = parseBudgetAmount(signed ? text.slice(1).trim() : text);
    if (!amount) return null;
    return {
        cents: negative ? -amount.cents : amount.cents,
        amount: negative && amount.cents !== 0n ? `-${amount.amount}` : amount.amount,
    };
}

export function readAmendmentList(data, contractId) {
    if (!Array.isArray(data) || data.some((item) => !item?.id
        || String(item.contractId) !== String(contractId)
        || !validDate(item.effectiveDate)
        || (item.valueChange != null && !parseAmendmentAmount(item.valueChange)))) {
        throw new Error('Nie można odczytać listy aneksów tej umowy. Spróbuj ponownie.');
    }
    return [...data].sort((first, second) => first.effectiveDate.localeCompare(second.effectiveDate));
}

export function getAmendmentPreview(form, contract, amendments, amendmentId) {
    const initial = parseBudgetAmount(contract?.initialValue);
    const change = form.valueChange.trim() ? parseAmendmentAmount(form.valueChange) : { cents: 0n };
    if (!initial || !change || !validDate(form.effectiveDate)) return null;

    const history = [];
    for (const amendment of amendments) {
        if (amendmentId != null && String(amendment.id) === String(amendmentId)) continue;
        const amount = amendment.valueChange == null ? { cents: 0n } : parseAmendmentAmount(amendment.valueChange);
        if (!amount || !validDate(amendment.effectiveDate)) return null;
        history.push({ effectiveDate: amendment.effectiveDate, cents: amount.cents,
            newEndDate: amendment.newEndDate });
    }
    history.push({ effectiveDate: form.effectiveDate, cents: change.cents, newEndDate: form.newEndDate || null });
    history.sort((first, second) => first.effectiveDate.localeCompare(second.effectiveDate));

    let total = initial.cents;
    let endDate = contract.initialEndDate || null;
    let negativeAtDate = null;
    for (const entry of history) {
        total += entry.cents;
        if (total < 0n && negativeAtDate === null) negativeAtDate = entry.effectiveDate;
        if (entry.newEndDate) endDate = entry.newEndDate;
    }
    return {
        currentValueCents: parseBudgetAmount(contract.currentValue)?.cents ?? null,
        proposedValueCents: total, currentEndDate: contract.currentEndDate || null,
        proposedEndDate: endDate, negativeAtDate,
    };
}

export function validateAmendmentForm(form, contract, amendments, amendmentId) {
    const errors = {};
    if (!form.amendmentNumber.trim()) errors.amendmentNumber = 'Podaj numer aneksu.';
    else if (form.amendmentNumber.trim().length > 255) errors.amendmentNumber = 'Numer może mieć maksymalnie 255 znaków.';

    for (const [field, label] of [['amendmentDate', 'Data aneksu'], ['effectiveDate', 'Data obowiązywania']]) {
        if (!validDate(form[field])) errors[field] = 'Podaj prawidłową datę.';
        else if (form[field] < contract.contractDate) errors[field] = `${label} nie może poprzedzać daty umowy.`;
    }
    if (!errors.effectiveDate && amendments.some((entry) => entry.effectiveDate === form.effectiveDate
        && (amendmentId == null || String(entry.id) !== String(amendmentId)))) {
        errors.effectiveDate = 'Umowa ma już aneks z tą datą obowiązywania.';
    }

    const change = form.valueChange.trim() ? parseAmendmentAmount(form.valueChange) : null;
    if (form.valueChange.trim() && !change) {
        errors.valueChange = 'Podaj różnicę kwoty z maksymalnie dwoma miejscami po przecinku, np. 5000 lub -2000.';
    } else if (change && (change.cents > MAX_CHANGE_CENTS || change.cents < -MAX_CHANGE_CENTS)) {
        errors.valueChange = 'Zmiana wartości przekracza zakres kwoty aneksu.';
    }

    if (form.newEndDate) {
        if (!validDate(form.newEndDate)) errors.newEndDate = 'Podaj prawidłową datę.';
        else if (contract.initialStartDate && form.newEndDate < contract.initialStartDate) {
            errors.newEndDate = 'Nowy termin nie może poprzedzać rozpoczęcia realizacji umowy.';
        }
    }
    if (form.description.trim().length > 2000) errors.description = 'Opis może mieć maksymalnie 2000 znaków.';

    if (!errors.effectiveDate && !errors.valueChange) {
        const preview = getAmendmentPreview(form, contract, amendments, amendmentId);
        if (!preview) errors.valueChange = 'Nie można zweryfikować historii wartości umowy. Odśwież formularz.';
        else if (preview.negativeAtDate) {
            errors.valueChange = `Po tej zmianie wartość umowy byłaby ujemna od ${formatAmendmentDate(preview.negativeAtDate)}. Sprawdź także późniejsze aneksy.`;
        }
    }
    return errors;
}

export function buildAmendmentRequest(contractId, amendmentId, form) {
    const data = {
        amendmentNumber: form.amendmentNumber.trim(), amendmentDate: form.amendmentDate,
        effectiveDate: form.effectiveDate,
        valueChange: form.valueChange.trim() ? parseAmendmentAmount(form.valueChange).amount : null,
        newEndDate: form.newEndDate || null, description: form.description.trim() || null,
    };
    if (amendmentId != null) return { method: 'put', url: `/contract-amendments/${amendmentId}`, data };
    const numericId = Number(contractId);
    data.contractId = Number.isSafeInteger(numericId) ? numericId : String(contractId);
    return { method: 'post', url: '/contract-amendments', data };
}

export function formatAmendmentDate(value) {
    return validDate(value) ? dateFormatter.format(new Date(`${value}T00:00:00`)) : '—';
}

export function formatAmendmentChange(value) {
    if (value == null) return 'Bez zmiany wartości';
    const parsed = parseAmendmentAmount(value);
    return parsed ? `${parsed.cents > 0n ? '+' : ''}${formatBudgetCents(parsed.cents)}` : '—';
}
