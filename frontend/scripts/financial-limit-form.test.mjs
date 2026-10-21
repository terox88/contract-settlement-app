import test from 'node:test';
import assert from 'node:assert/strict';
import { annualAvailableCents, buildFinancialLimitRequest, originalCommitments, validateFinancialLimitPlan } from '../src/utils/financialLimitForm.js';

const contract = { id: 7, contractDate: '2026-06-01', currentValue: '100' };
const budget = { investmentTaskId: 1, currentYear: 2026, years: [
    { year: 2026, amount: '100', committedAmount: '120', availableAmount: '-20' },
    { year: 2027, amount: '100', committedAmount: '80', availableAmount: '20' },
] };
const plan = (amount2026 = '80', amount2027 = '20') => [
    { year: '2026', amount: amount2026 }, { year: '2027', amount: amount2027 },
];

test('Domyślne zaangażowanie z roku zawarcia zostaje zastąpione podziałem na lata', () => {
    assert.deepEqual([...originalCommitments(contract, null)], [[2026, 10000n]]);
    const check = validateFinancialLimitPlan(contract, null, budget, plan());
    assert.equal(check.error, null);
    assert.deepEqual(check.years, [{ year: 2026, amount: '80.00' }, { year: 2027, amount: '20.00' }]);
});
test('Ujemne wolne środki nie są traktowane jak brak danych', () => {
    assert.equal(annualAvailableCents(budget, budget.years[0]), -2000n);
    assert.equal(validateFinancialLimitPlan(contract, null, budget, plan()).error, null);
});
test('Starsza odpowiedź z samymi kwotami lat pozwala wysłać plan do walidacji serwera', () => {
    const oldBudget = { years: [{ year: 2026, amount: '100' }, { year: 2027, amount: '100' }] };
    const check = validateFinancialLimitPlan(contract, null, oldBudget, plan());
    assert.equal(check.error, null);
    assert.deepEqual(check.uncheckedYears, [2027]);
    assert.equal(buildFinancialLimitRequest(contract, null, oldBudget, plan()).url, '/financial-limits/main-contract/7/plan');
});
test('Gdy dostępne jest zaangażowanie, wolne środki są liczone jako budżet minus zaangażowanie', () => {
    assert.equal(annualAvailableCents({}, { year: 2026, amount: '100', committedAmount: '120' }), -2000n);
    assert.equal(annualAvailableCents({}, { year: 2026, amount: '100' }), null);
});
test('Podsumowanie bieżącego roku nie jest przypisywane pozostałym latom', () => {
    const data = { currentYear: 2026, currentYearAvailableAmount: '-20' };
    assert.equal(annualAvailableCents(data, { year: 2026, amount: '100' }), -2000n);
    assert.equal(annualAvailableCents(data, { year: 2027, amount: '100' }), null);
});
test('Zmniejszenie przeciążenia jest dozwolone nawet gdy nie usuwa go całkowicie', () => {
    const data = { ...contract, currentValue: '120' };
    const original = { years: [{ year: 2026, amount: '100' }, { year: 2027, amount: '20' }] };
    const annual = { years: [{ year: 2026, amount: '100', availableAmount: '-20' }, { year: 2027, amount: '100', availableAmount: '10' }] };
    assert.equal(validateFinancialLimitPlan(data, original, annual, plan('90', '30')).error, null);
    assert.match(validateFinancialLimitPlan(data, original, annual, plan('110', '10')).error, /Za mało wolnych środków na 2026/);
});
test('Własne poprzednie limity nie są liczone drugi raz, także przy zerowych wolnych środkach', () => {
    const original = { years: [{ year: 2026, amount: '80' }, { year: 2027, amount: '20' }] };
    const annual = { years: [{ year: 2026, amount: '100', availableAmount: '0' }, { year: 2027, amount: '100', availableAmount: '0' }] };
    assert.equal(validateFinancialLimitPlan(contract, original, annual, plan()).error, null);
    assert.match(validateFinancialLimitPlan(contract, original, annual, plan('70', '30')).error, /Za mało wolnych środków na 2027/);
});
test('Brak wolnych środków przy zwiększaniu innego roku nadal blokuje formularz', () => {
    assert.match(validateFinancialLimitPlan(contract, null, budget, plan('79.99', '20.01')).error, /Za mało wolnych środków na 2027/);
});
test('Pusty zapisany plan korzysta z takiego samego zaangażowania domyślnego jak backend', () => {
    assert.deepEqual([...originalCommitments(contract, { years: [] })], [[2026, 10000n]]);
    assert.equal(validateFinancialLimitPlan(contract, { years: [] }, budget, plan()).error, null);
});
test('Zerowy limit może zwolnić istniejący rok bez budżetu; dodatni wymaga budżetu', () => {
    const only2027 = { years: [{ year: 2027, amount: '100', availableAmount: '100' }] };
    assert.equal(validateFinancialLimitPlan(contract, null, only2027, plan('0', '100')).error, null);
    assert.match(validateFinancialLimitPlan(contract, null, only2027, plan()).error, /Brak budżetu zadania na rok 2026/);
});
test('Suma, duplikaty, zakres lat i precyzja kwot nadal są walidowane', () => {
    assert.match(validateFinancialLimitPlan(contract, null, budget, plan('80', '19.99')).error, /Suma limitów/);
    assert.match(validateFinancialLimitPlan(contract, null, budget, [plan()[0], plan()[0]]).error, /więcej niż raz/);
    assert.match(validateFinancialLimitPlan(contract, null, budget, [{ year: '2201', amount: '100' }]).error, /poprawny rok/);
    assert.match(validateFinancialLimitPlan(contract, null, budget, plan('-1', '101')).error, /nieujemną kwotę/);
    assert.match(validateFinancialLimitPlan(contract, null, budget, plan('80.001', '19.999')).error, /miejsca po przecinku/);
});
test('Plan jest wysyłany jednym PUT z dokładnymi kwotami dziesiętnymi', () => {
    const data = { ...contract, currentValue: '0.30' };
    const annual = { years: [{ year: 2026, amount: '1' }, { year: 2027, amount: '1' }] };
    const request = buildFinancialLimitRequest(data, null, annual, plan('0,10', '0,20'));
    assert.deepEqual(request, { method: 'put', url: '/financial-limits/main-contract/7/plan', data: {
        years: [{ year: 2026, amount: '0.10' }, { year: 2027, amount: '0.20' }],
    } });
});

test('Edycja limitów również blokuje finansowanie sprzed roku zawarcia umowy', () => {
    const history = { years: [{ year: 2025, amount: '1000', availableAmount: '1000' }, ...budget.years] };
    const rows = [{ year: '2025', amount: '100' }];
    assert.match(validateFinancialLimitPlan(contract, null, history, rows).error, /2025.*rok zawarcia.*2026/);
    assert.throws(() => buildFinancialLimitRequest(contract, null, history, rows), /2025.*2026/);
    assert.match(validateFinancialLimitPlan(contract, { years: [{ year: 2025, amount: '100' }] }, history, rows).error, /2025.*2026/);
});

test('Błędny historyczny limit można wyzerować lub usunąć; nie można dodawać nowego historycznego roku', () => {
    const original = { years: [{ year: 2025, amount: '100' }] };
    const annual = { years: [{ year: 2026, amount: '100', availableAmount: '100' }] };
    const corrected = [{ year: '2025', amount: '0' }, { year: '2026', amount: '100' }];
    assert.equal(validateFinancialLimitPlan(contract, original, annual, corrected).error, null);
    assert.equal(validateFinancialLimitPlan(contract, original, annual, corrected.slice(1)).error, null);
    assert.match(validateFinancialLimitPlan(contract, null, annual, corrected).error, /2025.*2026/);
});
