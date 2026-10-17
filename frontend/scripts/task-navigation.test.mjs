import test from 'node:test';
import assert from 'node:assert/strict';
import { safeNavigationUrl, isRememberedView, readTaskHistory, rememberTaskView, resolveTaskNavigation } from '../src/utils/taskNavigation.js';

const records = {
    '/investment-tasks/1': { id: 1, taskNumber: 'Z/1' },
    '/main-contracts/2': { id: 2, investmentTaskId: 1, contractNumber: 'UM/2' },
    '/subcontracts/3': { id: 3, mainContractId: 2, contractNumber: 'PW/3' },
    '/main-contractor-invoices/4': { id: 4, mainContractId: 2, invoiceNumber: 'GW/4' },
    '/subcontractor-invoices/5': { id: 5, mainContractId: 2, subcontractId: 3, invoiceNumber: 'PW/5' },
    '/payments/6': { id: 6, bankReference: 'BANK/6' },
};
function client(overrides = {}) {
    const calls = [];
    return { calls, async get(url, options) {
        calls.push({ url, options });
        const data = { ...records, ...overrides }[url];
        if (!data) throw new Error(`Brak ${url}`);
        return { data };
    } };
}
test('Adresy nawigacji są lokalne, a parametry uporządkowane', () => {
    assert.equal(safeNavigationUrl('/tasks/1?z=2&a=1'), '/tasks/1?a=1&z=2');
    for (const url of ['https://example.com', '//example.com/tasks', '/tasks\\evil', '/other', null]) assert.equal(safeNavigationUrl(url), null);
});
test('Historia obejmuje widoki, pomija formularze i globalne listy', () => {
    for (const url of ['/tasks', '/payments', '/tasks/new', '/tasks/1/edit', '/main-contracts/2/subcontracts/3/invoices/new']) assert.equal(isRememberedView(url), false);
    assert.equal(isRememberedView('/main-contracts/2/invoices/4'), true);
});
test('Uszkodzona lub zablokowana pamięć nie blokuje nawigacji', () => {
    assert.deepEqual(readTaskHistory({ getItem() { throw new Error('blocked'); } }), {});
    assert.deepEqual(readTaskHistory({ getItem: () => 'bad json' }), {});
    const storage = { getItem: () => JSON.stringify({ 1: [{ url: '//evil', title: 'bad' }, { url: '/tasks/1', title: 'Z/1' }], invalid: [] }) };
    assert.deepEqual(readTaskHistory(storage), { 1: [{ url: '/tasks/1', title: 'Z/1' }] });
});
test('Historia jest osobna dla zadania, bez duplikatów i ograniczona do ośmiu widoków', () => {
    let history = {};
    for (let id = 1; id <= 10; id++) history = rememberTaskView(history, 1, { url: `/main-contracts/${id}`, title: `UM/${id}` });
    history = rememberTaskView(history, 2, { url: '/tasks/2', title: 'Z/2' });
    history = rememberTaskView(history, 1, { url: '/main-contracts/9', title: 'UM/9' });
    assert.equal(history[1].length, 8);
    assert.equal(history[1][0].url, '/main-contracts/9');
    assert.equal(history[2][0].url, '/tasks/2');
    assert.equal(history[1].filter((entry) => entry.url === '/main-contracts/9').length, 1);
    assert.equal(rememberTaskView(history, 1, { url: '/tasks/1/edit', title: 'Edycja' }), history);
});
test('Lista zadań i formularz nowego zadania nie wymagają odczytów API', async () => {
    const http = client();
    assert.equal((await resolveTaskNavigation(http, '/tasks')).task, null);
    assert.equal((await resolveTaskNavigation(http, '/tasks/new')).crumbs.at(-1).title, 'Nowe zadanie');
    assert.equal(http.calls.length, 0);
});
test('Nowa umowa ma bezpośrednią drogę do zadania i listy zadań', async () => {
    const nav = await resolveTaskNavigation(client(), '/tasks/1/main-contracts/new');
    assert.equal(nav.task.id, 1);
    assert.deepEqual(nav.crumbs.map((crumb) => crumb.url), ['/tasks', '/tasks/1', '/tasks/1/main-contracts/new']);
    assert.equal(nav.remember, false);
});
test('Szczegóły faktury GW odtwarzają prawdziwy kontekst zadania i umowy', async () => {
    const http = client();
    const controller = new AbortController();
    const nav = await resolveTaskNavigation(http, '/main-contracts/2/invoices/4', controller.signal);
    assert.deepEqual(nav.crumbs.map((crumb) => crumb.url), ['/tasks', '/tasks/1', '/main-contracts/2', '/main-contracts/2/invoices', '/main-contracts/2/invoices/4']);
    assert.equal(nav.remember, true);
    assert.equal(nav.title, 'Faktury GW · Faktura GW GW/4');
    assert.ok(http.calls.every((call) => call.options.signal === controller.signal));
});
test('Edycja faktury PW nie wymaga zainstalowania modułu płatności', async () => {
    const nav = await resolveTaskNavigation(client(), '/main-contracts/2/subcontracts/3/invoices/5/edit');
    assert.equal(nav.remember, false);
    assert.ok(nav.crumbs.some((crumb) => crumb.url === '/main-contracts/2/subcontracts/3/invoices'));
    assert.ok(nav.crumbs.every((crumb) => !crumb.url.includes('/payments')));
});
test('Widok płatności faktury PW zachowuje kontekst zadania', async () => {
    const url = '/main-contracts/2/subcontracts/3/invoices/5/payments';
    const nav = await resolveTaskNavigation(client(), url);
    assert.equal(nav.task.id, 1);
    assert.equal(nav.crumbs.at(-1).url, url);
    assert.equal(nav.remember, true);
});
test('Przelew bez kontekstu faktury nie jest przypisywany do przypadkowego zadania', async () => {
    const http = client();
    const nav = await resolveTaskNavigation(http, '/payments/6');
    assert.equal(nav.task, null);
    assert.equal(nav.remember, false);
    assert.deepEqual(http.calls.map((call) => call.url), ['/payments/6']);
});
test('Nowy przelew otwarty z faktury ma zweryfikowaną ścieżkę do umowy i zadania', async () => {
    const nav = await resolveTaskNavigation(client(), '/payments/new?type=sub&contractId=2&subcontractId=3&invoiceId=5');
    assert.equal(nav.task.id, 1);
    assert.equal(nav.crumbs.at(-1).title, 'Nowy przelew');
    assert.equal(nav.remember, false);
});
test('Niezgodne powiązania faktur i umów nie tworzą błędnej ścieżki', async () => {
    await assert.rejects(resolveTaskNavigation(client({ '/main-contractor-invoices/4': { ...records['/main-contractor-invoices/4'], mainContractId: 99 } }), '/main-contracts/2/invoices/4'), /innej umowy/);
    await assert.rejects(resolveTaskNavigation(client({ '/subcontracts/3': { ...records['/subcontracts/3'], mainContractId: 99 } }), '/main-contracts/2/subcontracts/3/invoices'), /innej umowy/);
    await assert.rejects(resolveTaskNavigation(client({ '/subcontractor-invoices/5': { ...records['/subcontractor-invoices/5'], subcontractId: 99 } }), '/main-contracts/2/subcontracts/3/invoices/5/edit'), /innej umowy/);
});
