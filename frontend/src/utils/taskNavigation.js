const idPattern = /^[1-9]\d*$/;
const isId = (value) => idPattern.test(String(value ?? ''));
const sameId = (left, right) => String(left) === String(right);
export const TASK_HISTORY_KEY = 'settlements.task-navigation.v1';

export function safeNavigationUrl(value) {
    if (typeof value !== 'string' || value.length > 1200 || !value.startsWith('/') || value.startsWith('//') || /[\\\r\n\t]/.test(value)) return null;
    const [path, query = ''] = value.split('?');
    if (!/^\/(?:tasks|main-contracts|payments)(?:\/|$)/.test(path)) return null;
    const params = new URLSearchParams(query); params.sort();
    return path + (params.size ? `?${params.toString()}` : '');
}
export function isRememberedView(url) {
    const safe = safeNavigationUrl(url);
    if (!safe) return false;
    const path = safe.split('?')[0];
    return !/(?:^|\/)(?:new|edit)(?:\/|$)/.test(path) && path !== '/tasks' && path !== '/payments';
}
export function readTaskHistory(storage) {
    try {
        const value = JSON.parse(storage?.getItem(TASK_HISTORY_KEY) ?? '{}');
        if (!value || typeof value !== 'object' || Array.isArray(value)) return {};
        const history = {};
        for (const [taskId, entries] of Object.entries(value).slice(0, 20)) {
            if (!isId(taskId) || !Array.isArray(entries)) continue;
            history[taskId] = entries.filter((entry) => entry && isRememberedView(entry.url) && typeof entry.title === 'string')
                .slice(0, 8).map((entry) => ({ url: safeNavigationUrl(entry.url), title: entry.title.slice(0, 180) }));
        }
        return history;
    } catch { return {}; }
}
export function rememberTaskView(history, taskId, view) {
    const url = safeNavigationUrl(view.url);
    if (!isId(taskId) || !isRememberedView(url)) return history;
    const previous = history[String(taskId)] ?? [];
    const entries = [{ url, title: String(view.title).slice(0, 180) }, ...previous.filter((entry) => entry.url !== url)].slice(0, 8);
    return Object.fromEntries([[String(taskId), entries], ...Object.entries(history).filter(([id]) => id !== String(taskId))].slice(0, 20));
}

/** Buduje ścieżkę wyłącznie z rzeczywistych powiązań odczytanych z API. */
export async function resolveTaskNavigation(http, url, signal) {
    const safe = safeNavigationUrl(url);
    const parsed = new URL(safe ?? '/tasks', 'https://navigation.invalid');
    const parts = parsed.pathname.split('/').filter(Boolean);
    const query = parsed.searchParams;
    const crumbs = [{ title: 'Lista zadań', url: '/tasks' }];
    const cache = new Map();
    const get = (path) => {
        if (!cache.has(path)) cache.set(path, http.get(path, { signal }).then((response) => response.data));
        return cache.get(path);
    };
    const result = (task = null) => ({ task, crumbs, url: safe ?? '/tasks', title: crumbs.slice(-Math.min(2, crumbs.length)).map((crumb) => crumb.title).join(' · '),
        remember: Boolean(task) && isRememberedView(safe) });
    if (parts[0] === 'tasks' && parts[1] === 'new') {
        crumbs.push({ title: 'Nowe zadanie', url: parsed.pathname }); return result();
    }
    let task = null, main = null, contractId = null, rest = [], paymentMode = false;
    if (parts[0] === 'tasks' && isId(parts[1])) {
        task = await get(`/investment-tasks/${parts[1]}`);
        if (!sameId(task?.id, parts[1])) throw new Error('Nie można potwierdzić zadania.');
        crumbs.push({ title: task.taskNumber || `Zadanie ${task.id}`, url: `/tasks/${task.id}` });
        if (parts[2] === 'edit') crumbs.push({ title: 'Edycja zadania', url: parsed.pathname });
        if (parts[2] === 'main-contracts' && parts[3] === 'new') crumbs.push({ title: 'Nowa umowa główna', url: parsed.pathname });
        return result(task);
    }
    if (parts[0] === 'main-contracts' && isId(parts[1])) {
        contractId = parts[1]; rest = parts.slice(2);
    } else if (parts[0] === 'payments') {
        paymentMode = true;
        // Sam przelew może dotyczyć kilku zadań; przypisujemy kontekst tylko z konkretnej faktury.
        if (isId(query.get('contractId')) && isId(query.get('invoiceId')) && ['main', 'sub'].includes(query.get('type'))
            && (query.get('type') === 'main' || isId(query.get('subcontractId')))) {
            contractId = query.get('contractId');
            rest = query.get('type') === 'main' ? ['invoices', query.get('invoiceId')]
                : ['subcontracts', query.get('subcontractId'), 'invoices', query.get('invoiceId')];
        }
    }
    if (contractId) {
        main = await get(`/main-contracts/${contractId}`);
        if (!sameId(main?.id, contractId) || !isId(main.investmentTaskId)) throw new Error('Nie można potwierdzić umowy głównej.');
        task = await get(`/investment-tasks/${main.investmentTaskId}`);
        if (!sameId(task?.id, main.investmentTaskId)) throw new Error('Nie można potwierdzić zadania umowy.');
        const base = `/main-contracts/${contractId}`;
        crumbs.push({ title: task.taskNumber || `Zadanie ${task.id}`, url: `/tasks/${task.id}` },
            { title: `Umowa ${main.contractNumber || contractId}`, url: base });
        if (rest[0] === 'edit') crumbs.push({ title: 'Edycja umowy', url: parsed.pathname });
        if (rest[0] === 'amendments') crumbs.push({ title: rest[1] === 'new' ? 'Nowy aneks' : 'Edycja aneksu', url: parsed.pathname });
        if (rest[0] === 'invoices') {
            crumbs.push({ title: 'Faktury GW', url: `${base}/invoices` });
            if (rest[1] === 'new') crumbs.push({ title: 'Nowa faktura GW', url: parsed.pathname });
            else if (isId(rest[1])) {
                const invoice = await get(`/main-contractor-invoices/${rest[1]}`);
                if (!sameId(invoice?.id, rest[1]) || !sameId(invoice.mainContractId, contractId)) throw new Error('Faktura GW dotyczy innej umowy.');
                crumbs.push({ title: `Faktura GW ${invoice.invoiceNumber}`, url: `${base}/invoices/${invoice.id}` });
                if (rest[2] === 'edit') crumbs.push({ title: 'Edycja faktury GW', url: parsed.pathname });
                if (rest[2] === 'payments') crumbs.push({ title: 'Płatności faktury GW', url: parsed.pathname });
            }
        }
        if (rest[0] === 'subcontracts') {
            if (rest[1] === 'new') crumbs.push({ title: 'Nowa umowa podwykonawcza', url: parsed.pathname });
            else if (isId(rest[1])) {
                const sub = await get(`/subcontracts/${rest[1]}`);
                if (!sameId(sub?.id, rest[1]) || !sameId(sub.mainContractId, contractId)) throw new Error('Umowa podwykonawcza dotyczy innej umowy głównej.');
                const subBase = `${base}/subcontracts/${sub.id}/invoices`;
                crumbs.push({ title: `Faktury umowy PW ${sub.contractNumber || sub.id}`, url: subBase });
                if (rest[2] === 'edit') crumbs.push({ title: 'Edycja umowy PW', url: parsed.pathname });
                if (rest[2] === 'invoices' && rest[3] === 'new') crumbs.push({ title: 'Nowa faktura PW', url: parsed.pathname });
                else if (rest[2] === 'invoices' && isId(rest[3])) {
                    const invoice = await get(`/subcontractor-invoices/${rest[3]}`);
                    if (!sameId(invoice?.id, rest[3]) || !sameId(invoice.subcontractId, sub.id) || !sameId(invoice.mainContractId, contractId)) throw new Error('Faktura podwykonawcy dotyczy innej umowy.');
                    const paymentPath = `${subBase}/${invoice.id}/payments`;
                    // Faktura PW ma widok listy oraz rozliczeń; link prowadzi do istniejącego widoku.
                    crumbs.push({ title: `Faktura PW ${invoice.invoiceNumber}`, url: rest[4] === 'payments' || paymentMode ? paymentPath : subBase });
                    if (rest[4] === 'edit') crumbs.push({ title: 'Edycja faktury PW', url: parsed.pathname });
                    if (rest[4] === 'payments') crumbs.push({ title: 'Płatności i potrącenia', url: parsed.pathname });
                }
            }
        }
    }
    if (paymentMode) {
        crumbs.push({ title: 'Przelewy', url: '/payments' });
        if (parts[1] === 'new') crumbs.push({ title: 'Nowy przelew', url: safe });
        else if (isId(parts[1])) {
            const payment = await get(`/payments/${parts[1]}`);
            if (!sameId(payment?.id, parts[1])) throw new Error('Nie można potwierdzić przelewu.');
            const suffix = parsed.search;
            crumbs.push({ title: `Przelew ${payment.bankReference || `#${payment.id}`}`, url: `/payments/${payment.id}${suffix}` });
            if (parts[2] === 'edit') crumbs.push({ title: 'Edycja przelewu', url: safe });
        }
    }
    return result(task);
}
