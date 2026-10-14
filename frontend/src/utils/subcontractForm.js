import { parseBudgetAmount, formatBudgetCents } from './budgetForm.js';
import { parseAmendmentAmount } from './contractAmendmentForm.js';
import {
    buildMainContractUpdatePayload, CONTRACTOR_FIELDS, normalizeTaxId,
    validateContractData, validateMainContractForm, validDate,
} from './mainContractForm.js';

export const DEDUCTION_FIELDS = [
    { name: 'performanceSecurityDeduction', label: 'Zabezpieczenie należytego wykonania umowy' },
    { name: 'otherDeduction', label: 'Inne potrącenia' },
];
export const VALUE_BASES = [
    { value: 'GROSS', label: 'Brutto' },
    { value: 'NET', label: 'Netto' },
];

export function emptyDeductions() {
    return Object.fromEntries(DEDUCTION_FIELDS.map((field) => [field.name,
        { enabled: false, percentage: '', basis: 'GROSS' }]));
}

export function deductionsToForm(data) {
    return Object.fromEntries(DEDUCTION_FIELDS.map((field) => {
        const rule = data[field.name];
        return [field.name, rule ? { enabled: true, percentage: String(rule.percentage ?? ''), basis: rule.basis ?? '' }
            : { enabled: false, percentage: '', basis: 'GROSS' }];
    }));
}

export function validateSubcontractForm(contract, deductions, contractor, nip, lookup, selected,
    contractTypes, isEditing) {
    const errors = isEditing ? validateContractData(contract, contractTypes)
        : validateMainContractForm(contract, contractor, nip, lookup, selected, contractTypes);
    for (const field of DEDUCTION_FIELDS) {
        const rule = deductions[field.name];
        if (!rule.enabled) continue;
        const percentage = parseBudgetAmount(rule.percentage);
        if (!percentage || percentage.cents > 10000n) {
            errors[`${field.name}.percentage`] = 'Podaj procent od 0 do 100, z maksymalnie dwoma miejscami po przecinku.';
        }
        if (!VALUE_BASES.some((option) => option.value === rule.basis)) {
            errors[`${field.name}.basis`] = 'Wybierz podstawę netto albo brutto.';
        }
    }
    return errors;
}

export function buildSubcontractRequest(mainContractId, subcontractId, contract, deductions, contractor, nip, selected) {
    const data = buildMainContractUpdatePayload(contract);
    for (const field of DEDUCTION_FIELDS) {
        const rule = deductions[field.name];
        data[field.name] = rule.enabled
            ? { percentage: parseBudgetAmount(rule.percentage).amount, basis: rule.basis } : null;
    }
    if (subcontractId != null) return { method: 'put', url: `/subcontracts/${subcontractId}`, data };

    const numericId = Number(mainContractId);
    data.mainContractId = Number.isSafeInteger(numericId) ? numericId : String(mainContractId);
    if (selected) data.subcontractorId = selected.id;
    else {
        data.newSubcontractor = Object.fromEntries(CONTRACTOR_FIELDS.map((field) => [
            field.name, contractor[field.name].trim() || null,
        ]));
        data.newSubcontractor.taxId = normalizeTaxId(nip);
    }
    return { method: 'post', url: '/subcontracts', data };
}

function roundHalfUp(numerator, denominator) {
    const negative = numerator < 0n;
    const absolute = negative ? -numerator : numerator;
    const rounded = (absolute + denominator / 2n) / denominator;
    return negative ? -rounded : rounded;
}

function proposedSubcontractCents(contract, savedSubcontract) {
    const initial = parseBudgetAmount(contract.initialValue);
    if (!initial) return null;
    let grossCents = initial.cents;
    if (savedSubcontract) {
        const oldInitial = parseBudgetAmount(savedSubcontract.initialValue);
        const oldCurrent = parseAmendmentAmount(savedSubcontract.currentValue);
        if (!oldInitial || !oldCurrent) return null;
        grossCents += oldCurrent.cents - oldInitial.cents;
    }
    return grossCents;
}

export function getSubcontractPreview(contract, deductions, savedSubcontract) {
    const grossCents = proposedSubcontractCents(contract, savedSubcontract);
    const vat = parseBudgetAmount(contract.vatRate);
    if (grossCents == null || !vat || vat.cents > 10000n) return null;
    // Backend najpierw zaokrągla netto do groszy, następnie kwotę potrącenia.
    const netCents = roundHalfUp(grossCents * 10000n, 10000n + vat.cents);
    const result = { grossCents, netCents, totalCents: 0n, amounts: {} };
    for (const field of DEDUCTION_FIELDS) {
        const rule = deductions[field.name];
        if (!rule.enabled) result.amounts[field.name] = 0n;
        else {
            const percentage = parseBudgetAmount(rule.percentage);
            if (!percentage || percentage.cents > 10000n
                || !VALUE_BASES.some((option) => option.value === rule.basis)) return null;
            const basis = rule.basis === 'NET' ? netCents : grossCents;
            result.amounts[field.name] = roundHalfUp(basis * percentage.cents, 10000n);
        }
        result.totalCents += result.amounts[field.name];
    }
    return result;
}

export function getSubcontractsSummary(mainContract, subcontracts) {
    const main = parseAmendmentAmount(mainContract?.currentValue);
    const amounts = subcontracts.map((subcontract) => parseAmendmentAmount(subcontract.currentValue));
    if (amounts.some((amount) => !amount)) return null;
    const totalCents = amounts.reduce((sum, amount) => sum + amount.cents, 0n);
    return { totalCents, remainingCents: main ? main.cents - totalCents : null };
}

export function getProposedSubcontractsSummary(mainContract, contract, savedSubcontract) {
    const main = parseAmendmentAmount(mainContract?.currentValue);
    const total = parseAmendmentAmount(mainContract?.subcontractsTotalValue);
    const proposed = proposedSubcontractCents(contract, savedSubcontract);
    const old = savedSubcontract ? parseAmendmentAmount(savedSubcontract.currentValue) : { cents: 0n };
    if (!main || !total || proposed == null || !old) return null;
    const totalCents = total.cents - old.cents + proposed;
    return { totalCents, remainingCents: main.cents - totalCents };
}

export function proposedSubcontractEndDate(contract, savedSubcontract) {
    return savedSubcontract?.endDateOverriddenByAmendment
        ? savedSubcontract.currentEndDate : contract.initialEndDate;
}

export function validateSubcontractDates(contract, mainContract, savedSubcontract) {
    const errors = {};
    if (validDate(contract.contractDate) && validDate(mainContract?.contractDate)
        && contract.contractDate < mainContract.contractDate) {
        errors.contractDate = 'Data zawarcia nie może poprzedzać daty zawarcia umowy głównej.';
    }
    const endDate = proposedSubcontractEndDate(contract, savedSubcontract);
    if (!validDate(endDate)) errors.initialEndDate = 'Podaj termin zakończenia umowy podwykonawczej.';
    else if (validDate(contract.contractDate) && endDate < contract.contractDate) {
        errors.initialEndDate = 'Aktualny termin zakończenia nie może poprzedzać daty zawarcia umowy podwykonawczej.';
    }
    return errors;
}

export function getSubcontractEndWarning(mainEndDate, subcontractEndDate) {
    if (!validDate(mainEndDate) || !validDate(subcontractEndDate) || subcontractEndDate <= mainEndDate) return '';
    const format = (value) => value.split('-').reverse().join('.');
    return `Uwaga: termin zakończenia umowy podwykonawczej (${format(subcontractEndDate)}) przekracza aktualny termin umowy głównej (${format(mainEndDate)}). Przekroczenie tego terminu nie blokuje zapisu.`;
}

export function readSubcontracts(data, mainContractId) {
    if (!Array.isArray(data) || data.some((entry) => !entry?.id || entry.mainContractId == null
        || !validDate(entry.contractDate))) {
        throw new Error('Nie można odczytać listy umów podwykonawczych. Spróbuj ponownie.');
    }
    return data.filter((entry) => String(entry.mainContractId) === String(mainContractId));
}

export function formatSubcontractAmount(value) {
    return formatBudgetCents(parseAmendmentAmount(value)?.cents ?? null);
}

export function formatDeductionRule(rule) {
    if (!rule) return 'Nie ustalono';
    const basis = VALUE_BASES.find((option) => option.value === rule.basis);
    const percentage = parseBudgetAmount(rule.percentage);
    return basis && percentage ? `${percentage.amount.replace(/\.?0+$/, '') || '0'}% ${basis.label.toLowerCase()}` : '—';
}
