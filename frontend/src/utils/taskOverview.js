import { parseBudgetAmount } from './budgetForm.js';
import { expiryNotice, securityStages } from './contractAdministration.js';

const normalize = value => String(value ?? '').toLocaleLowerCase('pl-PL').normalize('NFD')
    .replace(/\p{M}/gu, '').replace(/ł/g, 'l');

export function filterTasks(tasks, query) {
    const words = normalize(query).trim().split(/\s+/).filter(Boolean);
    if (!words.length) return tasks;
    return tasks.filter(task => {
        const text = normalize([task.taskNumber, task.name, task.implementer, task.description].join(' '));
        return words.every(word => text.includes(word));
    });
}

export function taskStatusCounts(tasks) {
    const counts = { total: tasks.length, IN_PROGRESS: 0, COMPLETED: 0, ON_HOLD: 0, CANCELLED: 0 };
    for (const task of tasks) if (Object.hasOwn(counts, task.status) && task.status !== 'total') counts[task.status]++;
    return counts;
}

export function summarizeTaskContracts(contracts) {
    const totals = { currentValue: 0n, invoicedAmount: 0n, paidAmount: 0n, unpaidInvoices: 0n };
    for (const contract of contracts) {
        for (const field of ['currentValue', 'invoicedAmount', 'paidAmount']) {
            const amount = parseBudgetAmount(contract[field]);
            totals[field] = totals[field] === null || !amount ? null : totals[field] + amount.cents;
        }
        const invoiced = parseBudgetAmount(contract.invoicedAmount), paid = parseBudgetAmount(contract.paidAmount);
        totals.unpaidInvoices = totals.unpaidInvoices === null || !invoiced || !paid ? null
            : totals.unpaidInvoices + (invoiced.cents > paid.cents ? invoiced.cents - paid.cents : 0n);
    }
    return totals;
}

export function taskContractAttention(contracts, today) {
    const notices = [];
    for (const contract of contracts) {
        const data = contract.administration ?? {};
        const add = (kind, label, date) => {
            const notice = expiryNotice(date, today);
            if (notice) {
                if (kind === 'end') notice.text = notice.days < 0
                    ? `Termin realizacji minął ${-notice.days} ${notice.days === -1 ? 'dzień' : 'dni'} temu.`
                    : notice.days === 0 ? 'Termin realizacji przypada dzisiaj.'
                        : `Do terminu realizacji pozostało ${notice.days} ${notice.days === 1 ? 'dzień' : 'dni'}.`;
                notices.push({ key: `${contract.id}-${kind}`, contractId: contract.id,
                    contractNumber: contract.contractNumber, label, date, ...notice });
            }
        };
        if (!data.actualCompletionDate) add('end', 'Termin realizacji — brak faktycznego zakończenia', contract.currentEndDate);
        add('oc', 'Ubezpieczenie OC', data.ocInsuranceValidUntil);
        add('car', 'Ubezpieczenie CAR', data.carInsuranceValidUntil);
        for (const stage of securityStages(data)) {
            if (!stage.returned && data.securityType !== 'CASH') add(stage.stage, `Zabezpieczenie: ${stage.label.toLocaleLowerCase('pl-PL')}`, stage.date);
        }
    }
    return notices.sort((a, b) => a.days - b.days || String(a.contractNumber).localeCompare(String(b.contractNumber), 'pl'));
}
