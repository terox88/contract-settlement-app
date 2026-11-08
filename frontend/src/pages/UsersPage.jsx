import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import http from '../api/http';
import { useAuth } from '../auth/AuthContext';
import {
    makeUserEditor,
    mergeUser,
    requiresUserReload,
    userEditorBody,
    validateUserEditor,
} from '../users/usersUtils';
import './UsersPage.css';

export default function UsersPage() {
    const {
        user,
        isAdmin,
        busy: authBusy,
        finishSession,
    } = useAuth();

    const navigate = useNavigate();

    const [users, setUsers] = useState([]);
    const [roles, setRoles] = useState([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [reload, setReload] = useState(0);
    const [search, setSearch] = useState('');
    const [activity, setActivity] = useState('all');
    const [editor, setEditor] = useState(null);
    const [formError, setFormError] = useState('');
    const [stale, setStale] = useState(false);
    const [saving, setSaving] = useState(false);
    const [notice, setNotice] = useState('');

    const savingRef = useRef(false);
    const mountedRef = useRef(false);

    useEffect(() => {
        mountedRef.current = true;
        return () => {
            mountedRef.current = false;
        };
    }, []);

    useEffect(() => {
        if (!isAdmin) return undefined;

        let active = true;
        const controller = new AbortController();

        setLoading(true);
        setLoadError('');

        Promise.all([
            http.get('/users', { signal: controller.signal }),
            http.get('/users/roles', { signal: controller.signal }),
        ]).then(([accounts, options]) => {
            if (!active) return;

            if (!Array.isArray(accounts.data)
                || !Array.isArray(options.data)
                || !options.data.length) {
                throw new Error(
                    'Serwer zwrócił nieprawidłową listę użytkowników lub ról.'
                );
            }

            setUsers(accounts.data);
            setRoles(options.data);
        }).catch((failure) => {
            if (active && failure.code !== 'ERR_CANCELED') {
                setLoadError(failure.message);
            }
        }).finally(() => {
            if (active) setLoading(false);
        });

        return () => {
            active = false;
            controller.abort();
        };
    }, [isAdmin, reload]);

    const locked = loading || saving || authBusy;

    const activeAdmins = users.filter(
        (account) => account.enabled && account.role === 'ADMIN'
    ).length;

    const ownAccount = editor?.account?.id === user?.id;

    const lastAdmin = editor?.mode === 'edit'
        && editor.account.enabled
        && editor.account.role === 'ADMIN'
        && activeAdmins <= 1;

    const unchanged = editor?.mode === 'edit'
        && editor.displayName.trim() === editor.account.displayName
        && editor.role === editor.account.role
        && editor.enabled === editor.account.enabled;

    const query = search.trim().toLocaleLowerCase('pl-PL');

    const visible = users.filter((account) => {
        const matchesText =
            `${account.username} ${account.displayName}`
                .toLocaleLowerCase('pl-PL')
                .includes(query);

        return matchesText && (
            activity === 'all'
            || (
                activity === 'enabled'
                    ? account.enabled
                    : !account.enabled
            )
        );
    });

    function openEditor(mode, account = null) {
        if (locked || editor || loadError) return;

        setEditor(makeUserEditor(mode, account, roles));
        setFormError('');
        setStale(false);
        setNotice('');
    }

    function closeEditor() {
        if (savingRef.current) return;

        setEditor(null);
        setFormError('');
        setStale(false);
    }

    function refresh() {
        if (savingRef.current || authBusy) return;

        closeEditor();
        setNotice('');
        setReload((previous) => previous + 1);
    }

    function changeField(event) {
        const { name, type, checked, value } = event.target;

        setEditor((previous) => ({
            ...previous,
            [name]: type === 'checkbox' ? checked : value,
        }));
    }

    async function submit(event) {
        event.preventDefault();

        if (!editor || savingRef.current
            || locked || stale || unchanged) return;

        const validationError = validateUserEditor(editor);
        setFormError(validationError);

        if (validationError) return;

        if (editor.mode === 'edit' && ownAccount
            && !window.confirm(
                'Zapis własnego konta zakończy bieżącą sesję. Kontynuować?'
            )) return;

        if (editor.mode === 'reset'
            && !window.confirm(
                `Zresetować hasło konta „${editor.account.username}”? `
                + 'Dotychczasowe sesje utracą dostęp. '
                + 'Użytkownik będzie musiał zmienić hasło tymczasowe.'
            )) return;

        const current = editor;

        savingRef.current = true;
        setSaving(true);
        setNotice('');

        try {
            const body = userEditorBody(current);
            let response;

            if (current.mode === 'create') {
                response = await http.post('/users', body);
            } else if (current.mode === 'edit') {
                response = await http.put(
                    `/users/${current.account.id}`,
                    body
                );
            } else {
                response = await http.post(
                    `/users/${current.account.id}/password`,
                    body
                );
            }

            if (!mountedRef.current) return;

            if (current.mode === 'edit'
                && current.account.id === user.id) {
                finishSession(
                    'Dane Twojego konta zostały zmienione. '
                    + 'Zaloguj się ponownie.'
                );

                navigate('/login', {
                    replace: true,
                    state: { from: '/tasks' },
                });

                return;
            }

            setUsers((previous) =>
                mergeUser(previous, response.data));

            setEditor(null);
            setFormError('');
            setStale(false);

            setNotice(
                current.mode === 'create'
                    ? 'Konto utworzone. Przy pierwszym logowaniu użytkownik musi zmienić hasło tymczasowe.'
                    : current.mode === 'reset'
                        ? 'Hasło zresetowane. Użytkownik musi zalogować się hasłem tymczasowym i je zmienić.'
                        : 'Dane konta zapisane. Dotychczasowe sesje użytkownika utraciły dostęp.'
            );
        } catch (failure) {
            if (!mountedRef.current
                || failure.code === 'ERR_CANCELED') return;

            setStale(requiresUserReload(failure, current.mode));

            setFormError(
                !failure.response
                    ? 'Nie udało się potwierdzić zapisu. Odśwież listę przed ponowieniem operacji.'
                    : failure.message
            );

            setEditor((previous) => previous ? {
                ...previous,
                temporaryPassword: '',
                repeatedPassword: '',
            } : null);
        } finally {
            savingRef.current = false;

            if (mountedRef.current) setSaving(false);
        }
    }

    if (!isAdmin) {
        return (
            <main className="users-page">
                <h1>Brak uprawnień</h1>
                <p>
                    Panel użytkowników jest dostępny dla administratora.
                </p>
                <Link to="/tasks">Przejdź do zadań</Link>
            </main>
        );
    }

    return (
        <main
            className="users-page"
            aria-busy={loading || saving}
        >
            <div className="users-heading">
                <div>
                    <h1>Użytkownicy</h1>
                    <p>
                        Tworzenie kont, uprawnienia i hasła tymczasowe.
                    </p>
                </div>

                <div className="auth-actions">
                    <button
                        className="auth-button"
                        type="button"
                        onClick={refresh}
                        disabled={locked || Boolean(editor)}
                    >
                        Odśwież listę
                    </button>

                    <button
                        className="auth-button auth-button--primary"
                        type="button"
                        onClick={() => openEditor('create')}
                        disabled={
                            locked
                            || Boolean(editor)
                            || Boolean(loadError)
                        }
                    >
                        Nowe konto
                    </button>
                </div>
            </div>

            {notice && (
                <p className="auth-notice" role="status">
                    {notice}
                </p>
            )}

            {loadError && (
                <p className="auth-error" role="alert">
                    {loadError}
                </p>
            )}

            {loading && (
                <p role="status">Ładowanie użytkowników…</p>
            )}

            {editor && (
                <section
                    className="users-panel"
                    aria-labelledby="users-form-title"
                >
                    <h2 id="users-form-title">
                        {editor.mode === 'create'
                            ? 'Nowe konto'
                            : editor.mode === 'edit'
                                ? `Edycja konta: ${editor.account.username}`
                                : `Reset hasła: ${editor.account.username}`}
                    </h2>

                    {editor.mode === 'edit' && ownAccount && (
                        <p className="auth-notice">
                            Zapis własnego konta zakończy bieżącą sesję.
                        </p>
                    )}

                    {lastAdmin && (
                        <p className="auth-notice">
                            To ostatni aktywny administrator.
                            Jego rola i aktywność są chronione.
                        </p>
                    )}

                    {editor.mode !== 'edit' && (
                        <p>
                            Hasło tymczasowe: 8–128 znaków.
                            Użytkownik musi je zmienić po zalogowaniu.
                        </p>
                    )}

                    {editor.mode === 'reset' && (
                        <p>
                            Reset hasła odbierze dostęp dotychczasowym
                            sesjom tego konta. Nie zmieni jego aktywności.
                        </p>
                    )}

                    {formError && (
                        <p className="auth-error" role="alert">
                            {formError}
                        </p>
                    )}

                    <form
                        method="post"
                        onSubmit={submit}
                        noValidate
                    >
                        <fieldset disabled={locked || stale}>
                            <div className="users-form-grid">
                                {editor.mode === 'create' && (
                                    <label
                                        className="auth-field"
                                        htmlFor="users-login"
                                    >
                                        Login
                                        <input
                                            id="users-login"
                                            type="text"
                                            name="username"
                                            autoComplete="off"
                                            value={editor.username}
                                            onChange={changeField}
                                            maxLength={64}
                                            required
                                            autoFocus
                                        />
                                    </label>
                                )}

                                {editor.mode !== 'reset' && (
                                    <>
                                        <label
                                            className="auth-field"
                                            htmlFor="users-name"
                                        >
                                            Imię i nazwisko / nazwa użytkownika
                                            <input
                                                id="users-name"
                                                type="text"
                                                name="displayName"
                                                autoComplete="off"
                                                value={editor.displayName}
                                                onChange={changeField}
                                                maxLength={150}
                                                required
                                                autoFocus={editor.mode === 'edit'}
                                            />
                                        </label>

                                        <label
                                            className="auth-field"
                                            htmlFor="users-role"
                                        >
                                            Rola
                                            <select
                                                id="users-role"
                                                name="role"
                                                value={editor.role}
                                                onChange={changeField}
                                                disabled={lastAdmin}
                                                required
                                            >
                                                {roles.map((role) => (
                                                    <option
                                                        key={role.value}
                                                        value={role.value}
                                                    >
                                                        {role.label}
                                                    </option>
                                                ))}
                                            </select>
                                        </label>
                                    </>
                                )}

                                {editor.mode !== 'edit' && (
                                    <>
                                        <label
                                            className="auth-field"
                                            htmlFor="users-password"
                                        >
                                            Hasło tymczasowe
                                            <input
                                                id="users-password"
                                                type="password"
                                                name="temporaryPassword"
                                                autoComplete="new-password"
                                                value={editor.temporaryPassword}
                                                onChange={changeField}
                                                maxLength={256}
                                                required
                                                autoFocus={editor.mode === 'reset'}
                                            />
                                        </label>

                                        <label
                                            className="auth-field"
                                            htmlFor="users-repeat-password"
                                        >
                                            Powtórz hasło tymczasowe
                                            <input
                                                id="users-repeat-password"
                                                type="password"
                                                name="repeatedPassword"
                                                autoComplete="new-password"
                                                value={editor.repeatedPassword}
                                                onChange={changeField}
                                                maxLength={256}
                                                required
                                            />
                                        </label>
                                    </>
                                )}
                            </div>

                            {editor.mode === 'edit' && (
                                <label className="users-checkbox">
                                    <input
                                        type="checkbox"
                                        name="enabled"
                                        checked={editor.enabled}
                                        onChange={changeField}
                                        disabled={lastAdmin}
                                    />
                                    Konto aktywne — odznaczenie blokuje logowanie
                                </label>
                            )}
                        </fieldset>

                        <div className="auth-actions">
                            <button
                                className="auth-button auth-button--primary"
                                type="submit"
                                disabled={locked || stale || unchanged}
                            >
                                {saving
                                    ? 'Zapisywanie…'
                                    : editor.mode === 'create'
                                        ? 'Utwórz konto'
                                        : editor.mode === 'reset'
                                            ? 'Zresetuj hasło'
                                            : 'Zapisz zmiany'}
                            </button>

                            <button
                                className="auth-button"
                                type="button"
                                onClick={closeEditor}
                                disabled={saving}
                            >
                                Anuluj
                            </button>

                            {stale && (
                                <button
                                    className="auth-button"
                                    type="button"
                                    onClick={refresh}
                                    disabled={locked}
                                >
                                    Zamknij i odśwież listę
                                </button>
                            )}
                        </div>
                    </form>
                </section>
            )}

            {!loading && !loadError && (
                <section
                    className="users-panel"
                    aria-label="Lista kont"
                >
                    <div className="users-filters">
                        <label
                            className="auth-field"
                            htmlFor="users-search"
                        >
                            Szukaj konta
                            <input
                                id="users-search"
                                type="search"
                                value={search}
                                onChange={(event) =>
                                    setSearch(event.target.value)}
                                placeholder="Login lub nazwa"
                            />
                        </label>

                        <label
                            className="auth-field"
                            htmlFor="users-activity"
                        >
                            Aktywność
                            <select
                                id="users-activity"
                                value={activity}
                                onChange={(event) =>
                                    setActivity(event.target.value)}
                            >
                                <option value="all">Wszystkie konta</option>
                                <option value="enabled">Aktywne</option>
                                <option value="disabled">Zablokowane</option>
                            </select>
                        </label>
                    </div>

                    <div
                        className="users-table-wrap"
                        role="region"
                        aria-label="Tabela użytkowników"
                        tabIndex={0}
                    >
                        <table className="users-table">
                            <thead>
                            <tr>
                                <th scope="col">Login</th>
                                <th scope="col">Nazwa</th>
                                <th scope="col">Rola</th>
                                <th scope="col">Konto</th>
                                <th scope="col">Hasło</th>
                                <th scope="col">Działania</th>
                            </tr>
                            </thead>

                            <tbody>
                            {visible.map((account) => (
                                <tr key={account.id}>
                                    <td>
                                        <strong>{account.username}</strong>

                                        {account.id === user.id && (
                                            <small className="users-own">
                                                Twoje konto
                                            </small>
                                        )}
                                    </td>

                                    <td>{account.displayName}</td>
                                    <td>{account.roleLabel}</td>

                                    <td>
                                            <span className={
                                                      `users-badge ${
                                                          account.enabled
                                                              ? 'users-badge--active'
                                                              : ''
                                                      }`
                                                  }>
                                                {account.enabled
                                                    ? 'Aktywne'
                                                    : 'Zablokowane'}
                                            </span>
                                    </td>

                                    <td>
                                        {account.passwordChangeRequired
                                            ? 'Wymagana zmiana'
                                            : 'Bez obowiązku zmiany'}
                                    </td>

                                    <td>
                                        <div className="auth-actions">
                                            <button
                                                className="auth-button"
                                                type="button"
                                                disabled={
                                                    locked || Boolean(editor)
                                                }
                                                onClick={() =>
                                                    openEditor('edit', account)}
                                            >
                                                Edytuj
                                            </button>

                                            {account.id === user.id
                                                ? (
                                                    <Link to="/change-password">
                                                        Zmień własne hasło
                                                    </Link>
                                                )
                                                : (
                                                    <button
                                                        className="auth-button"
                                                        type="button"
                                                        disabled={
                                                            locked
                                                            || Boolean(editor)
                                                        }
                                                        onClick={() =>
                                                            openEditor(
                                                                'reset',
                                                                account
                                                            )}
                                                    >
                                                        Reset hasła
                                                    </button>
                                                )}
                                        </div>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>

                    {!visible.length && (
                        <p>
                            Brak kont spełniających wybrane kryteria.
                        </p>
                    )}
                </section>
            )}
        </main>
    );
}