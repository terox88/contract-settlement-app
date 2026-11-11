import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import http from '../api/http';
import {
    FILTER_FIELDS, applyHistoryFilters, auditValue, formatAuditTime,
    historyHeading, historyRequestParams, historyReturnLink, historyUrl, readAuditPage,
} from '../audit/auditUtils';
import './AuditHistoryPage.css';

export default function AuditHistoryPage() {
    const [search, setSearch] = useSearchParams();
    const request = historyRequestParams(search);
    const listKey = new URLSearchParams(request).toString();
    const selected = search.get('eventId');
    const [options, setOptions] = useState(null);
    const [optionsError, setOptionsError] = useState('');
    const [page, setPage] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [reload, setReload] = useState(0);
    const back = historyReturnLink(search);

    useEffect(() => {
        const controller = new AbortController(); let active = true;
        setOptionsError('');
        http.get('/audit/options', { signal: controller.signal }).then(({ data }) => {
            if (!Array.isArray(data?.entityTypes) || !Array.isArray(data?.operations)) throw new Error('Nieprawidłowa lista filtrów historii.');
            if (active) setOptions(data);
        }).catch((failure) => { if (active && failure.code !== 'ERR_CANCELED') setOptionsError(failure.message); });
        return () => { active = false; controller.abort(); };
    }, [reload]);

    useEffect(() => {
        const controller = new AbortController(); let active = true;
        setLoading(true); setError(''); setPage(null);
        http.get('/audit/events', { params: Object.fromEntries(new URLSearchParams(listKey)), signal: controller.signal })
            .then(({ data }) => { const result = readAuditPage(data); if (active) setPage(result); })
            .catch((failure) => { if (active && failure.code !== 'ERR_CANCELED') setError(failure.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [listKey, reload]);

    function updateQuery(key, value) {
        const next = new URLSearchParams(search);
        next.delete('eventId');
        if (key !== 'page') next.delete('page');
        next.set(key, String(value)); setSearch(next);
    }
    function showEvent(id) { const next = new URLSearchParams(search); next.set('eventId', String(id)); setSearch(next); }
    function closeEvent() { const next = new URLSearchParams(search); next.delete('eventId'); setSearch(next, { replace: true }); }

    return <div className="audit-page">
        <Link className="back-link" to={back.url}>← {back.label}</Link>
        <header className="audit-heading">
            <div><p className="audit-eyebrow">Rejestr operacji</p><h1>{historyHeading(search)}</h1>
                <p>Autor, czas i szczegóły zapisanych zmian. Daty i godziny w czasie polskim.</p></div>
            <button type="button" className="audit-button" disabled={loading} onClick={() => setReload((value) => value + 1)}>Odśwież</button>
        </header>
        <section className="card audit-filters" aria-label="Filtry historii">
            <HistoryFilters key={listKey} search={search} options={options}
                onApply={(filters) => setSearch(applyHistoryFilters(search, filters))} onReset={() => setSearch({})} />
            {optionsError && <p className="audit-error" role="alert">Nie udało się wczytać filtrów: {optionsError}
                <button type="button" className="audit-text-button" onClick={() => setReload((value) => value + 1)}>Ponów</button></p>}
            {(search.get('taskId') || search.get('mainContractId') || search.get('subcontractId') || search.get('entityId')) &&
                <div className="audit-scope"><span>Aktywne powiązanie: {historyHeading(search).replace('Historia ', '')}.</span>
                    <Link to="/history">Pokaż całą historię</Link></div>}
        </section>

        <section className="card audit-results" aria-label="Wpisy historii" aria-busy={loading}>
            <div className="audit-results-heading"><div><h2>Zapisane operacje</h2>
                <p className="audit-muted">{page ? `Liczba wpisów: ${page.totalElements}` : 'Od najnowszych do najstarszych'}</p></div>
                <label className="audit-page-size">Na stronie <select aria-label="Liczba wpisów na stronie" value={request.size} onChange={(event) => updateQuery('size', event.target.value)}>
                    <option value="20">20</option><option value="50">50</option><option value="100">100</option>
                </select></label></div>
            {loading && <p className="audit-state" role="status">Ładowanie historii…</p>}
            {error && <div className="audit-state audit-error" role="alert"><p>{error}</p>
                <button type="button" className="audit-button" onClick={() => setReload((value) => value + 1)}>Spróbuj ponownie</button></div>}
            {page && page.content.length === 0 && <div className="audit-state"><h3>Brak wpisów</h3>
                <p>Nie znaleziono operacji dla wybranych filtrów. Rejestr obejmuje zmiany zapisane po uruchomieniu historii.</p>
                {page.page > 0 && <button className="audit-button" onClick={() => updateQuery('page', 0)}>Przejdź do pierwszej strony</button>}</div>}
            {page && page.content.length > 0 && <>
                <div className="audit-table-scroll"><table className="audit-table"><thead><tr>
                    <th scope="col">Kiedy</th><th scope="col">Kto</th><th scope="col">Operacja i rekord</th><th scope="col">Powiązania</th><th scope="col"><span className="audit-sr-only">Szczegóły</span></th>
                </tr></thead><tbody>{page.content.map((event) => <tr key={event.id}>
                    <td><time dateTime={event.occurredAt}>{formatAuditTime(event.occurredAt)}</time></td>
                    <td><Actor event={event} /></td>
                    <td><strong>{event.operationLabel}</strong><span className="audit-record-type">{event.entityTypeLabel}</span>
                        <span className="audit-record-label">{event.entityLabel}</span></td>
                    <td><ContextLinks event={event} /></td>
                    <td><button type="button" className="audit-button audit-details-button" onClick={() => showEvent(event.id)} aria-label={`Szczegóły: ${event.entityLabel}`}>
                        Szczegóły <span aria-hidden="true">→</span></button></td>
                </tr>)}</tbody></table></div>
                <nav className="audit-pagination" aria-label="Strony historii">
                    <button className="audit-button" disabled={page.page === 0} onClick={() => updateQuery('page', page.page - 1)}>← Poprzednia</button>
                    <span>Strona {page.page + 1} z {page.totalPages}</span>
                    <button className="audit-button" disabled={page.page + 1 >= page.totalPages} onClick={() => updateQuery('page', page.page + 1)}>Następna →</button>
                </nav>
            </>}
        </section>
        <p className="audit-footnote">{options?.canReadAccountHistory
            ? 'Widok obejmuje historię danych i operacje dotyczące kont użytkowników.'
            : 'Widok obejmuje historię danych. Rejestr kont użytkowników jest dostępny dla administratora.'}</p>
        {selected && <EventDialog key={`${selected}-${reload}`} id={selected} onClose={closeEvent} />}
    </div>;
}

function HistoryFilters({ search, options, onApply, onReset }) {
    const [draft, setDraft] = useState(() => Object.fromEntries(FILTER_FIELDS.map((key) => [key, search.get(key) ?? ''])));
    const [error, setError] = useState('');
    const change = (event) => setDraft((previous) => ({ ...previous, [event.target.name]: event.target.value }));
    function submit(event) {
        event.preventDefault();
        if (draft.dateFrom && draft.dateTo && draft.dateFrom > draft.dateTo) {
            setError('Data początkowa nie może być późniejsza od końcowej.'); return;
        }
        setError(''); onApply(draft);
    }
    return <form onSubmit={submit}>
        <div className="audit-filter-grid">
            <label>Od dnia<input type="date" name="dateFrom" value={draft.dateFrom} onChange={change} /></label>
            <label>Do dnia włącznie<input type="date" name="dateTo" value={draft.dateTo} onChange={change} /></label>
            <label>Autor<input name="actor" value={draft.actor} maxLength={150} onChange={change} placeholder="Imię i nazwisko lub login" /></label>
            <label>Rekord<input name="text" value={draft.text} maxLength={200} onChange={change} placeholder="Numer umowy, faktury lub nazwa" /></label>
            <FilterSelect label="Rodzaj rekordu" name="entityType" value={draft.entityType} items={options?.entityTypes} onChange={change} />
            <FilterSelect label="Operacja" name="operation" value={draft.operation} items={options?.operations} onChange={change} />
        </div>
        {error && <p className="audit-error" role="alert">{error}</p>}
        <div className="audit-filter-actions"><button className="audit-button audit-button--primary" type="submit">Zastosuj filtry</button>
            <button className="audit-button" type="button" onClick={onReset}>Wyczyść filtry</button></div>
    </form>;
}

function FilterSelect({ label, name, value, items, onChange }) {
    return <label>{label}<select name={name} value={value} onChange={onChange} disabled={!items}>
        <option value="">Wszystkie</option>
        {value && !items?.some((item) => item.value === value) && <option value={value} disabled>Nieznana wartość</option>}
        {(items ?? []).map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
    </select></label>;
}

function Actor({ event }) {
    return <div className="audit-actor"><span>{event.actorDisplayName || (event.systemActor ? 'System' : event.actorUsername)}</span>
        {event.actorUsername && <small>{event.actorUsername}</small>}
        {event.systemActor && <small>Operacja systemowa</small>}</div>;
}

function ContextLinks({ event }) {
    return <div className="audit-context-links">
        {event.taskId != null && <Link to={historyUrl({ taskId: event.taskId })}>Historia zadania</Link>}
        {event.mainContractId != null && <Link to={historyUrl({ mainContractId: event.mainContractId })}>Historia umowy głównej</Link>}
        {event.subcontractId != null && <Link to={historyUrl({ mainContractId: event.mainContractId, subcontractId: event.subcontractId })}>Historia umowy PW</Link>}
        {event.taskId == null && event.mainContractId == null && <span className="audit-muted">Rejestr ogólny</span>}
    </div>;
}

function EventDialog({ id, onClose }) {
    const dialog = useRef(null);
    const [detail, setDetail] = useState(null);
    const [error, setError] = useState('');
    const [attempt, setAttempt] = useState(0);
    useEffect(() => { const element = dialog.current; element.showModal(); return () => element.close(); }, []);
    useEffect(() => {
        const controller = new AbortController(); let active = true;
        setDetail(null); setError('');
        http.get(`/audit/events/${encodeURIComponent(id)}`, { signal: controller.signal }).then(({ data }) => {
            if (!data?.event || String(data.event.id) !== String(id) || !Array.isArray(data.changes)) throw new Error('Nieprawidłowe szczegóły wpisu historii.');
            if (active) setDetail(data);
        }).catch((failure) => { if (active && failure.code !== 'ERR_CANCELED') setError(failure.message); });
        return () => { active = false; controller.abort(); };
    }, [id, attempt]);
    return <dialog className="audit-dialog" ref={dialog} aria-labelledby="audit-detail-title" onCancel={(event) => { event.preventDefault(); onClose(); }}>
        <div className="audit-dialog-heading"><div><p className="audit-eyebrow">Szczegóły operacji</p>
            <h2 id="audit-detail-title">{detail?.event.operationLabel || 'Wpis historii'}</h2></div>
            <button className="audit-button" type="button" onClick={onClose} autoFocus>Zamknij</button></div>
        {!detail && !error && <p role="status">Ładowanie szczegółów…</p>}
        {error && <div className="audit-error" role="alert"><p>{error}</p><button className="audit-button" onClick={() => setAttempt((value) => value + 1)}>Spróbuj ponownie</button></div>}
        {detail && <>
            <dl className="audit-detail-meta"><div><dt>Rekord</dt><dd>{detail.event.entityLabel}<small>{detail.event.entityTypeLabel}</small></dd></div>
                <div><dt>Autor</dt><dd><Actor event={detail.event} /></dd></div>
                <div><dt>Czas operacji</dt><dd>{formatAuditTime(detail.event.occurredAt)}</dd></div></dl>
            {detail.reason && <section className="audit-reason"><h3>Uzasadnienie</h3><p>{detail.reason}</p></section>}
            <h3>Zmiany pól</h3>
            {detail.changes.length ? <div className="audit-table-scroll"><table className="audit-table audit-changes"><thead><tr>
                <th scope="col">Pole</th><th scope="col">Przed operacją</th><th scope="col">Po operacji</th></tr></thead>
                <tbody>{detail.changes.map((change, index) => <tr key={`${change.fieldName}-${index}`}>
                    <th scope="row">{change.fieldLabel || change.fieldName}</th>
                    <td className={change.beforeValue == null ? 'audit-null' : ''}>{auditValue(change.beforeValue)}</td>
                    <td className={change.afterValue == null ? 'audit-null' : ''}>{auditValue(change.afterValue)}</td>
                </tr>)}</tbody></table></div> : <p className="audit-muted">Brak szczegółowych zmian pól.</p>}
            <footer className="audit-dialog-footer"><ContextLinks event={detail.event} />
                <Link to={historyUrl({ entityType: detail.event.entityType, entityId: detail.event.entityId })}>Historia tego rekordu</Link></footer>
        </>}
    </dialog>;
}
