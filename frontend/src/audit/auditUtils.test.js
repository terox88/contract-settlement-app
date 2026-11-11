import test from 'node:test';
import assert from 'node:assert/strict';
import {
    applyHistoryFilters, auditValue, formatAuditTime, historyHeading,
    historyRequestParams, historyReturnLink, historyUrl, readAuditPage,
} from './auditUtils.js';
import { resolveTaskNavigation } from '../utils/taskNavigation.js';

test('request parameters exclude detail selection and client permission overrides', () => {
    assert.deepEqual(historyRequestParams(new URLSearchParams('actor=Anna&eventId=9&includeAccounts=true&size=50')),
        { actor: 'Anna', size: '50', page: '0' });
});

test('applying filters keeps contract scope and resets pagination and detail', () => {
    const current = new URLSearchParams('mainContractId=7&subcontractId=8&page=4&eventId=9&size=50');
    const next = applyHistoryFilters(current, { actor: ' Anna ', text: '', operation: 'ATTACHMENT_DELETED' });
    assert.equal(next.get('mainContractId'), '7'); assert.equal(next.get('subcontractId'), '8');
    assert.equal(next.get('size'), '50'); assert.equal(next.get('actor'), 'Anna');
    assert.equal(next.has('page'), false); assert.equal(next.has('eventId'), false);
});

test('changing entity type clears the previous target identifier', () => {
    const current = new URLSearchParams('entityType=MAIN_CONTRACT&entityId=7');
    assert.equal(applyHistoryFilters(current, { entityType: 'SUBCONTRACT' }).has('entityId'), false);
    assert.equal(applyHistoryFilters(current, { entityType: 'MAIN_CONTRACT' }).get('entityId'), '7');
});

test('history links encode text without turning it into a route', () => {
    const value = '/x?<script>&100%';
    const url = new URL(historyUrl({ text: value, taskId: 7 }), 'https://app.invalid');
    assert.equal(url.pathname, '/history'); assert.equal(url.searchParams.get('text'), value);
    assert.equal(url.searchParams.get('taskId'), '7');
});

test('return links accept only identifiers and use the immediate subcontract', () => {
    assert.equal(historyReturnLink(new URLSearchParams('mainContractId=7&subcontractId=8')).url, '/main-contracts/7/subcontracts/8');
    assert.equal(historyReturnLink(new URLSearchParams('taskId=//external.invalid')).url, '/tasks');
    assert.equal(historyHeading(new URLSearchParams('taskId=7')), 'Historia zadania');
});

test('empty, missing, zero and text values remain distinguishable', () => {
    assert.equal(auditValue(null), 'Brak wartości'); assert.equal(auditValue(''), 'Pusty tekst');
    assert.equal(auditValue(0), '0'); assert.equal(auditValue('<img src=x>'), '<img src=x>');
});

test('history time uses Poland independently of the computer timezone', () => {
    assert.ok(formatAuditTime('2026-11-10T12:00:00Z').endsWith('13:00:00'));
    assert.ok(formatAuditTime('2026-07-10T12:00:00Z').endsWith('14:00:00'));
    assert.equal(formatAuditTime('not-a-date'), 'Nieznana data');
});

test('malformed pagination is rejected without silently showing an empty history', () => {
    const valid = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
    assert.equal(readAuditPage(valid), valid);
    assert.throws(() => readAuditPage({ ...valid, content: {} }));
    assert.throws(() => readAuditPage({ ...valid, size: 101 }));
    assert.throws(() => readAuditPage({ ...valid, totalElements: -1 }));
});

test('history breadcrumb requires no live task or contract to exist', async () => {
    const http = { get() { throw new Error('History must not require its target to exist'); } };
    const result = await resolveTaskNavigation(http, '/history?mainContractId=7');
    assert.equal(result.crumbs.at(-1).title, 'Historia zmian');
    assert.equal(result.crumbs.at(-1).url, '/history?mainContractId=7');
    assert.equal(result.remember, false);
});
