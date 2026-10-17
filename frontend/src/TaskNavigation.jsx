import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import http from './api/http';
import { readTaskHistory, rememberTaskView, resolveTaskNavigation, safeNavigationUrl, TASK_HISTORY_KEY } from './utils/taskNavigation';
import './TaskNavigation.css';

function storage() { try { return window.sessionStorage; } catch { return null; } }

export default function TaskNavigation() {
    const location = useLocation();
    const url = location.pathname + location.search;
    const [history, setHistory] = useState(() => readTaskHistory(storage()));
    const remember = useCallback((context) => {
        if (context.remember) setHistory((previous) => rememberTaskView(previous, context.task.id, context));
    }, []);
    useEffect(() => {
        try { storage()?.setItem(TASK_HISTORY_KEY, JSON.stringify(history)); } catch { /* Lista nadal działa w pamięci karty. */ }
    }, [history]);
    return <NavigationTrail key={url} url={url} history={history} onReady={remember} />;
}

function NavigationTrail({ url, history, onReady }) {
    const [context, setContext] = useState(null);
    const [error, setError] = useState('');
    const [attempt, setAttempt] = useState(0);
    useEffect(() => {
        let active = true;
        const controller = new AbortController();
        setError('');
        resolveTaskNavigation(http, url, controller.signal).then((data) => {
            if (active) { setContext(data); onReady(data); }
        }).catch((failure) => { if (active) setError(failure.message); });
        return () => { active = false; controller.abort(); };
    }, [url, attempt, onReady]);
    const crumbs = context?.crumbs ?? [{ title: 'Lista zadań', url: '/tasks' }];
    const recent = context?.task ? (history[String(context.task.id)] ?? []).filter((entry) => entry.url !== safeNavigationUrl(url)) : [];
    return <section className="task-navigation" aria-label="Nawigacja aplikacji">
        <nav aria-label="Ścieżka bieżącego widoku"><ol className="task-navigation-path">{crumbs.map((crumb, index) => <li key={`${crumb.url}-${index}`}>
            {index === crumbs.length - 1 && crumb.url === url ? <span aria-current="page">{crumb.title}</span> : <Link to={crumb.url}>{crumb.title}</Link>}
        </li>)}</ol></nav>
        {!context && !error && <span className="task-navigation-status" role="status">Ładowanie nawigacji zadania...</span>}
        {error && <div className="task-navigation-status" role="status">Nie udało się odczytać ścieżki zadania.
            <button type="button" onClick={() => setAttempt((value) => value + 1)}>Ponów</button></div>}
        {context?.task && recent.length > 0 && <nav className="task-navigation-recent" aria-label={`Ostatnie widoki zadania ${context.task.taskNumber || context.task.id}`}>
            <span>Ostatnio w tym zadaniu:</span><ul>{recent.slice(0, 6).map((entry) => <li key={entry.url}><Link to={entry.url}>{entry.title}</Link></li>)}</ul>
        </nav>}
    </section>;
}
