import test from 'node:test';
import assert from 'node:assert/strict';
import { filterTasks, taskStatusCounts, summarizeTaskContracts, taskContractAttention } from '../src/utils/taskOverview.js';
import { administrationPayload, emptyAdministration, securityStages, validateAdministration } from '../src/utils/contractAdministration.js';

test('Wyszukiwanie zadań łączy słowa, ignoruje wielkość liter i polskie znaki', () => {
    const tasks = [{ id: 1, taskNumber: 'Z/12', name: 'Żłobek', implementer: 'Dział Inwestycji' },
        { id: 2, name: 'Szkoła', description: 'Instalacja' }];
    assert.equal(filterTasks(tasks, ' zlobek DZIAL ')[0].id, 1);
    assert.equal(filterTasks(tasks, 'z/12 inwestycji').length, 1);
    assert.equal(filterTasks(tasks, 'szkola instalacja')[0].id, 2);
    assert.equal(filterTasks(tasks, 'zlobek szkola').length, 0);
    assert.equal(filterTasks(tasks, ''), tasks);
    assert.deepEqual(taskStatusCounts([{ status: 'IN_PROGRESS' }, { status: 'COMPLETED' }, { status: 'NEW' }]),
        { total: 3, IN_PROGRESS: 1, COMPLETED: 1, ON_HOLD: 0, CANCELLED: 0 });
});

test('Podsumowanie zachowuje grosze i pokazuje niezapłacone faktury zamiast całej pozostałej umowy', () => {
    const contracts = [
        { currentValue: '1000000.01', invoicedAmount: '100.03', paidAmount: '50.01', remainingToPay: '999950.00' },
        { currentValue: '0.99', invoicedAmount: '20', paidAmount: '30' },
    ];
    assert.deepEqual(summarizeTaskContracts(contracts), { currentValue: 100000100n, invoicedAmount: 12003n,
        paidAmount: 8001n, unpaidInvoices: 5002n });
    assert.equal(summarizeTaskContracts([{ currentValue: null }]).currentValue, null);
    assert.equal(summarizeTaskContracts([]).unpaidInvoices, 0n);
});

test('Panel uwagi respektuje 45 dni, faktyczne zakończenie i zwrot każdego etapu osobno', () => {
    const contract = { id: 7, contractNumber: 'U/1', currentEndDate: '2026-10-20', administration: {
        actualCompletionDate: '2026-10-20', ocInsuranceValidUntil: '2026-12-04', carInsuranceValidUntil: '2026-12-03',
        securityType: 'BANK_GUARANTEE', securityValidityMode: 'TWO_STAGE', securityValidUntil: '2026-10-19',
        retentionSecurityValidUntil: '2026-10-21', securityReturned: true, retentionSecurityReturned: false,
    } };
    const notices = taskContractAttention([contract], '2026-10-20');
    assert.deepEqual(notices.map(item => item.key), ['7-RETENTION', '7-car']);
    assert.equal(notices[0].days, 1);
    assert.equal(taskContractAttention([{ ...contract, administration: { ...contract.administration,
        retentionSecurityReturned: true, carInsuranceValidUntil: null } }], '2026-10-20').length, 0);
    const noCompletion = { ...contract, administration: { actualCompletionDate: null } };
    assert.match(taskContractAttention([noCompletion], '2026-10-20')[0].label, /brak faktycznego/);
    assert.equal(taskContractAttention([{ ...noCompletion, currentEndDate: '2026-10-19' }], '2026-10-20')[0].expired, true);
});

test('Dwa zwroty pieniężne mają poprawne kwoty bez sumowania pełnego i zredukowanego zabezpieczenia', () => {
    const stages = securityStages({ securityType: 'CASH', securityValidityMode: 'TWO_STAGE', securityAmount: '1000.01',
        retentionSecurityAmount: '300.01', securityReturned: true });
    assert.equal(stages[0].amountCents, 70000n);
    assert.equal(stages[1].amountCents, 30001n);
    assert.equal(stages[0].returned, true); assert.equal(stages[1].returned, false);
    assert.equal(securityStages({ securityType: 'NOT_ESTABLISHED' }).length, 0);
    assert.equal(securityStages({ securityType: 'CASH', securityAmount: '1000' })[0].amountCents, 100000n);
    assert.equal(securityStages({ securityType: 'CASH', securityValidityMode: 'TWO_STAGE', securityAmount: '1000' })[0].amountCents, null);
});

test('Dwuetapowe zabezpieczenie pieniężne nie wymaga dat; kwoty i ukryte daty są walidowane', () => {
    const value = { ...emptyAdministration(), securityType: 'CASH', securityValidityMode: 'TWO_STAGE',
        securityAmount: '1000', retentionSecurityAmount: '300' };
    assert.deepEqual(validateAdministration(value), {});
    assert.equal(administrationPayload(value).retentionSecurityAmount, '300.00');
    assert.ok(validateAdministration({ ...value, securityValidUntil: '2026-10-20' })['administration.securityType']);
    assert.ok(validateAdministration({ ...value, retentionSecurityAmount: '1000.01' })['administration.retentionSecurityAmount']);
    assert.ok(validateAdministration({ ...value, securityValidityMode: 'SINGLE' })['administration.securityValidityMode']);
});
