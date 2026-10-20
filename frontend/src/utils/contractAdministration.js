import { parseBudgetAmount } from './budgetForm.js';

const FIELDS = ['actualCompletionDate', 'warrantyMonths', 'ocInsuranceValidUntil', 'carInsuranceValidUntil',
    'securityType', 'securityAmount', 'securityValidityMode', 'securityValidUntil', 'retentionSecurityAmount', 'retentionSecurityValidUntil'];
export const isDocumentSecurity = type => Boolean(type && !['NOT_ESTABLISHED', 'CASH'].includes(type));
export const emptyAdministration = () => Object.fromEntries(FIELDS.map(name => [name, '']));
export const administrationToForm = value => Object.fromEntries(FIELDS.map(name => [name, String(value?.[name] ?? '')]));

export function readAdministrationOptions(data) {
    if (!Array.isArray(data?.securityTypes) || !data.securityTypes.length || !Array.isArray(data?.validityModes)
        || data.securityTypes.some(item => !item?.value || typeof item.label !== 'string' || typeof item.document !== 'boolean')
        || data.validityModes.some(item => !item?.value || typeof item.label !== 'string')
        || !['SINGLE', 'TWO_STAGE'].every(mode => data.validityModes.some(item => item.value === mode))) {
        throw new Error('Nie można odczytać wariantów zabezpieczenia umowy.');
    }
    return data;
}
function parseDay(value) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value ?? '') || value.startsWith('0000')) return null;
    const date = new Date(`${value}T00:00:00Z`);
    return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value ? date : null;
}
export function warrantyEndDate(completed, months) {
    const start = parseDay(completed);
    if (!start || !/^\d+$/.test(String(months ?? '')) || Number(months) > 1200) return null;
    const end = new Date(start); end.setUTCDate(1); end.setUTCMonth(end.getUTCMonth() + Number(months));
    const last = new Date(end); last.setUTCMonth(last.getUTCMonth() + 1); last.setUTCDate(0);
    end.setUTCDate(Math.min(start.getUTCDate(), last.getUTCDate()));
    return end.getUTCFullYear() > 9999 ? null : end.toISOString().slice(0, 10);
}
export function localToday(date = new Date()) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
export function formatAdministrationDate(value) {
    const date = parseDay(value);
    return date ? new Intl.DateTimeFormat('pl-PL', { timeZone: 'UTC' }).format(date) : 'Nie uzupełniono';
}
export function expiryNotice(validUntil, today = localToday()) {
    const end = parseDay(validUntil), now = parseDay(today);
    if (!end || !now) return null;
    const days = Math.round((end.getTime() - now.getTime()) / 86400000);
    if (days >= 45) return null;
    if (days < 0) return { days, expired: true, text: `Ważność upłynęła ${-days} ${days === -1 ? 'dzień' : 'dni'} temu.` };
    return { days, expired: false, text: days === 0 ? 'Ważność upływa dzisiaj.' : days === 1
        ? 'Do końca ważności pozostał 1 dzień.' : `Do końca ważności pozostało ${days} dni.` };
}
export function validateAdministration(value, contractDate = null) {
    const form = { ...emptyAdministration(), ...value }, errors = {};
    const error = (name, message) => { errors[`administration.${name}`] = message; };
    for (const name of ['actualCompletionDate', 'ocInsuranceValidUntil', 'carInsuranceValidUntil', 'securityValidUntil', 'retentionSecurityValidUntil']) {
        if (form[name] && !parseDay(form[name])) error(name, 'Podaj prawidłową datę.');
    }
    if (parseDay(contractDate) && parseDay(form.actualCompletionDate) && form.actualCompletionDate < contractDate) {
        error('actualCompletionDate', 'Faktyczna data zakończenia umowy nie może być wcześniejsza niż data jej zawarcia.');
    }
    if (form.warrantyMonths !== '' && (!/^\d+$/.test(String(form.warrantyMonths)) || Number(form.warrantyMonths) > 1200)) {
        error('warrantyMonths', 'Podaj całkowitą liczbę miesięcy od 0 do 1200.');
    }
    if (form.actualCompletionDate && form.warrantyMonths !== '' && !errors['administration.warrantyMonths']
        && !errors['administration.actualCompletionDate'] && !warrantyEndDate(form.actualCompletionDate, form.warrantyMonths)) {
        error('warrantyMonths', 'Data końca gwarancji wykracza poza obsługiwany zakres.');
    }
    for (const name of ['securityAmount', 'retentionSecurityAmount']) {
        if (form[name] !== '') {
            const amount = parseBudgetAmount(form[name]);
            if (!amount || amount.cents <= 0n || amount.cents > 999999999999999n) error(name, 'Podaj dodatnią kwotę z dokładnością do grosza.');
        }
    }
    const document = isDocumentSecurity(form.securityType);
    if (document) {
        if (!['SINGLE', 'TWO_STAGE'].includes(form.securityValidityMode)) error('securityValidityMode', 'Wybierz wariant ważności zabezpieczenia.');
        if (!form.securityValidUntil) error('securityValidUntil', 'Podaj termin ważności dokumentu.');
        if (form.securityValidityMode === 'TWO_STAGE') {
            if (!form.retentionSecurityValidUntil) error('retentionSecurityValidUntil', 'Podaj termin ważności zredukowanego zabezpieczenia.');
            else if (form.securityValidUntil && form.retentionSecurityValidUntil < form.securityValidUntil) error('retentionSecurityValidUntil', 'Drugi termin nie może być wcześniejszy od pierwszego.');
            const full = parseBudgetAmount(form.securityAmount), reduced = parseBudgetAmount(form.retentionSecurityAmount);
            if (full && reduced && reduced.cents > full.cents) error('retentionSecurityAmount', 'Zredukowana kwota nie może przekraczać pełnej kwoty.');
        }
    }
    // Hidden fields must never survive a switch to cash, no security or one date.
    if (!document && (form.securityValidityMode || form.securityValidUntil || form.retentionSecurityAmount || form.retentionSecurityValidUntil)) error('securityType', 'Usuń terminy dokumentu po zmianie formy zabezpieczenia.');
    if ((!form.securityType || form.securityType === 'NOT_ESTABLISHED') && form.securityAmount) error('securityType', 'Kwota wymaga wskazania ustanowionego zabezpieczenia.');
    if (document && form.securityValidityMode === 'SINGLE' && (form.retentionSecurityAmount || form.retentionSecurityValidUntil)) error('securityValidityMode', 'Drugi etap wymaga wariantu dwóch terminów.');
    return errors;
}
export function administrationPayload(value, contractDate = null) {
    const form = { ...emptyAdministration(), ...value };
    if (Object.keys(validateAdministration(form, contractDate)).length) throw new Error('Popraw dodatkowe dane umowy.');
    return Object.fromEntries(FIELDS.map(name => [name, form[name] === '' ? null : name === 'warrantyMonths'
        ? Number(form[name]) : ['securityAmount', 'retentionSecurityAmount'].includes(name) ? parseBudgetAmount(form[name]).amount : form[name]]));
}
export function changeSecurityType(value, type) {
    return { ...value, securityType: type,
        securityAmount: !type || type === 'NOT_ESTABLISHED' ? '' : value.securityAmount,
        securityValidityMode: isDocumentSecurity(type) ? value.securityValidityMode || 'SINGLE' : '',
        securityValidUntil: isDocumentSecurity(type) ? value.securityValidUntil : '',
        retentionSecurityAmount: isDocumentSecurity(type) ? value.retentionSecurityAmount : '',
        retentionSecurityValidUntil: isDocumentSecurity(type) ? value.retentionSecurityValidUntil : '' };
}
