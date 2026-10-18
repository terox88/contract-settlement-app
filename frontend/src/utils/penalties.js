import { parseBudgetAmount } from './budgetForm.js';
import { validDate } from './mainContractForm.js';
export function validatePenalty(form, contractDate) {
    const errors = {}, amount = parseBudgetAmount(form.amount);
    if (!validDate(form.imposedDate) || form.imposedDate < contractDate) errors.imposedDate = 'Podaj datę nie wcześniejszą niż data zawarcia umowy.';
    if (!amount || amount.cents <= 0n || amount.cents > 999999999999999n) errors.amount = 'Podaj dodatnią kwotę do 13 cyfr przed przecinkiem i 2 po przecinku.';
    if (form.description.trim().length > 1000) errors.description = 'Opis może mieć do 1000 znaków.';
    return errors;
}
export function penaltyRequest(contract, id, form) {
    const errors = validatePenalty(form, contract.contractDate);
    if (Object.keys(errors).length) throw new Error(Object.values(errors)[0]);
    const data = { imposedDate: form.imposedDate, amount: parseBudgetAmount(form.amount).amount, description: form.description.trim() || null };
    if (id) return { method: 'put', url: `/contract-penalties/${id}`, data };
    return { method: 'post', url: '/contract-penalties', data: { ...data, contractId: contract.id } };
}
export function readPenalties(data, contractId) {
    const ids = new Set();
    if (!Array.isArray(data)) throw new Error('Nie można odczytać kar umownych.');
    for (const row of data) {
        if (!/^[1-9]\d*$/.test(String(row.id)) || ids.has(String(row.id)) || String(row.contractId) !== String(contractId)
            || !validDate(row.imposedDate) || !parseBudgetAmount(row.amount) || parseBudgetAmount(row.amount).cents <= 0n) throw new Error('Nieprawidłowe dane kar umownych.');
        ids.add(String(row.id));
    }
    return data;
}
