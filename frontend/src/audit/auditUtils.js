export const FILTER_FIELDS = ['dateFrom', 'dateTo', 'actor', 'text', 'entityType', 'operation'];
const REQUEST_FIELDS = [...FILTER_FIELDS, 'entityId', 'taskId', 'mainContractId', 'subcontractId', 'page', 'size'];
const positiveId = (value) => /^[1-9]\d{0,18}$/.test(String(value ?? ''));

export function historyRequestParams(search) {
    const result = {};
    for (const key of REQUEST_FIELDS) {
        const value = search.get(key);
        if (value != null && value !== '') result[key] = value;
    }
    result.page ??= '0'; result.size ??= '20';
    return result;
}

export function historyUrl(filters = {}) {
    const search = new URLSearchParams();
    for (const key of REQUEST_FIELDS) {
        if (filters[key] != null && String(filters[key]) !== '') search.set(key, String(filters[key]));
    }
    return '/history' + (search.size ? `?${search}` : '');
}

export function applyHistoryFilters(current, filters) {
    const next = new URLSearchParams();
    for (const key of ['taskId', 'mainContractId', 'subcontractId', 'size']) {
        if (current.get(key)) next.set(key, current.get(key));
    }
    if (current.get('entityId') && filters.entityType === current.get('entityType')) next.set('entityId', current.get('entityId'));
    for (const key of FILTER_FIELDS) {
        const value = String(filters[key] ?? '').trim();
        if (value) next.set(key, value);
    }
    return next;
}

export function historyHeading(search) {
    if (search.get('entityId')) return 'Historia wybranego rekordu';
    if (search.get('subcontractId')) return 'Historia umowy podwykonawczej';
    if (search.get('mainContractId')) return 'Historia umowy głównej';
    if (search.get('taskId')) return 'Historia zadania';
    return 'Historia zmian';
}

export function historyReturnLink(search) {
    const main = search.get('mainContractId'), sub = search.get('subcontractId'), task = search.get('taskId');
    if (positiveId(main) && positiveId(sub)) return { url: `/main-contracts/${main}/subcontracts/${sub}`, label: 'Wróć do umowy' };
    if (positiveId(main)) return { url: `/main-contracts/${main}`, label: 'Wróć do umowy głównej' };
    if (positiveId(task)) return { url: `/tasks/${task}`, label: 'Wróć do zadania' };
    return { url: '/tasks', label: 'Zadania inwestycyjne' };
}

export function formatAuditTime(value) {
    const date = new Date(value);
    if (!value || !Number.isFinite(date.getTime())) return 'Nieznana data';
    return new Intl.DateTimeFormat('pl-PL', {
        dateStyle: 'medium', timeStyle: 'medium', timeZone: 'Europe/Warsaw',
    }).format(date);
}

export function auditValue(value) {
    if (value == null) return 'Brak wartości';
    return value === '' ? 'Pusty tekst' : String(value);
}

export function readAuditPage(data) {
    if (!data || !Array.isArray(data.content) || !Number.isInteger(data.page) || data.page < 0
        || !Number.isInteger(data.totalPages) || data.totalPages < 0 || !Number.isInteger(data.totalElements)
        || data.totalElements < 0 || !Number.isInteger(data.size) || data.size < 1 || data.size > 100) {
        throw new Error('Serwer zwrócił nieprawidłową listę historii.');
    }
    return data;
}
