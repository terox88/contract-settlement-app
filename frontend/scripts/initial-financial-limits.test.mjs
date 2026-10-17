import test from 'node:test';
import assert from 'node:assert/strict';
import { validateInitialFinancialLimits } from '../src/utils/financialLimitForm.js';

const contract = { contractDate: '2026-10-17', initialValue: '100' };
const budget = { years: [
    { year: 2026, amount: '100', availableAmount: '40' },
    { year: 2027, amount: '200', availableAmount: '60' },
] };
const rows = (first = '40', second = '60') => [{ year: '2026', amount: first }, { year: '2027', amount: second }];

test('Nowa umowa wysyła pełny plan lat z dokładnymi kwotami', () => {
    const result = validateInitialFinancialLimits(contract, budget, rows());
    assert.equal(result.error, null);
    assert.deepEqual(result.years, [{ year: 2026, amount: '40.00' }, { year: 2027, amount: '60.00' }]);
});
test('Nowa umowa nie dolicza swojej wartości do wolnego budżetu roku zawarcia', () => {
    assert.match(validateInitialFinancialLimits(contract, budget, rows('100', '0')).error, /Za mało wolnych środków na 2026/);
    assert.match(validateInitialFinancialLimits(contract, budget, rows('40.01', '59.99')).error, /Za mało wolnych środków na 2026/);
});
test('Nowa umowa nie może pogłębiać istniejącego przekroczenia budżetu', () => {
    const annual = { years: [{ year: 2026, amount: '100', availableAmount: '-20' }, budget.years[1]] };
    assert.match(validateInitialFinancialLimits(contract, annual, rows()).error, /Za mało wolnych środków/);
});
test('Brak informacji o wolnych środkach kieruje walidację do backendu', () => {
    const annual = { years: budget.years.map(({ year, amount }) => ({ year, amount })) };
    const result = validateInitialFinancialLimits(contract, annual, rows());
    assert.equal(result.error, null);
    assert.deepEqual(result.uncheckedYears, [2026, 2027]);
});
test('Nie można utworzyć umowy bez budżetu, ze złą sumą lub powtórzonym rokiem', () => {
    assert.match(validateInitialFinancialLimits(contract, null, rows()).error, /budżetu/);
    assert.match(validateInitialFinancialLimits(contract, budget, rows('40', '59')).error, /Suma limitów/);
    assert.match(validateInitialFinancialLimits(contract, budget, [rows()[0], rows()[0]]).error, /więcej niż raz/);
});
test('Plan działa także dla kwot z przecinkiem, zerowej umowy i późniejszego roku finansowania', () => {
    const small = validateInitialFinancialLimits({ ...contract, initialValue: '0,30' }, budget, rows('0,10', '0,20'));
    assert.equal(small.error, null);
    assert.deepEqual(small.years.map((row) => row.amount), ['0.10', '0.20']);
    assert.equal(validateInitialFinancialLimits({ ...contract, initialValue: '0' }, budget, rows('0', '0')).error, null);
    assert.equal(validateInitialFinancialLimits(contract, { years: [{ year: 2028, amount: '100', availableAmount: '100' }] }, [{ year: '2028', amount: '100' }]).error, null);
});
