import { useState } from 'react';
import {
    Link,
    Navigate,
    useLocation,
    useNavigate,
} from 'react-router-dom';

import { useAuth } from './AuthContext';
import LoginPage from '../pages/LoginPage';
import ChangePasswordPage from '../pages/ChangePasswordPage';
import { safeReturnPath } from './authUtils';
import './Auth.css';

export default function AuthGate({ children }) {
    const {
        user,
        status,
        error,
        retry,
        canEdit,
    } = useAuth();

    const location = useLocation();

    const path = location.pathname.replace(/\/+$/, '') || '/';
    const authPath =
        path === '/login' || path === '/change-password';

    const from = safeReturnPath(
        authPath
            ? location.state?.from
            : location.pathname + location.search + location.hash
    );

    if (status === 'loading') {
        return (
            <main className="auth-screen">
                <p role="status">Sprawdzanie sesji…</p>
            </main>
        );
    }

    if (status === 'error') {
        return (
            <main className="auth-screen">
                <section className="auth-card">
                    <h1>Nie można sprawdzić logowania</h1>

                    <p className="auth-error" role="alert">
                        {error}
                    </p>

                    <button
                        className="auth-button auth-button--primary"
                        onClick={retry}
                    >
                        Spróbuj ponownie
                    </button>
                </section>
            </main>
        );
    }

    if (!user) {
        return path === '/login'
            ? <LoginPage />
            : <Navigate to="/login" replace state={{ from }} />;
    }

    if (
        user.passwordChangeRequired
        && path !== '/change-password'
    ) {
        return (
            <Navigate
                to="/change-password"
                replace
                state={{ from }}
            />
        );
    }

    if (path === '/login') {
        return <Navigate to={from} replace />;
    }

    let content = children;

    if (path === '/change-password') {
        content = (
            <ChangePasswordPage
                forced={user.passwordChangeRequired}
            />
        );
    } else if (!canEdit && /\/(new|edit)$/.test(path)) {
        content = (
            <main className="auth-screen auth-screen--inside">
                <section className="auth-card">
                    <h1>Brak uprawnień do edycji</h1>
                    <p>
                        Twoje konto pozwala przeglądać dane
                        i pobierać dokumenty.
                    </p>
                    <Link to="/tasks">Przejdź do zadań</Link>
                </section>
            </main>
        );
    }

    return (
        <>
            <UserToolbar />
            {content}
        </>
    );
}

function UserToolbar() {
    const { user, logout, busy } = useAuth();

    const location = useLocation();
    const navigate = useNavigate();

    const [error, setError] = useState('');

    async function signOut() {
        setError('');

        try {
            await logout();
            navigate('/login', {
                replace: true,
                state: null,
            });
        } catch (failure) {
            if (failure.code !== 'ERR_CANCELED') {
                setError(failure.message);
            }
        }
    }

    return (
        <header className="auth-toolbar">
            <div className="auth-toolbar__identity">
                {!user.passwordChangeRequired && (
                    <Link to="/tasks">Zadania</Link>
                )}

                <span>{user.displayName || user.username}</span>
                <small>{user.roleLabel || user.role}</small>
            </div>

            <div className="auth-actions">
                {!user.passwordChangeRequired
                    && !/^\/change-password\/?$/.test(
                        location.pathname
                    )
                    && (
                        <button
                            className="auth-button"
                            type="button"
                            disabled={busy}
                            onClick={() => navigate(
                                '/change-password',
                                {
                                    state: {
                                        from: safeReturnPath(
                                            location.pathname
                                            + location.search
                                            + location.hash
                                        ),
                                    },
                                }
                            )}
                        >
                            Zmień hasło
                        </button>
                    )}

                <button
                    className="auth-button"
                    type="button"
                    onClick={signOut}
                    disabled={busy}
                >
                    Wyloguj
                </button>
            </div>

            {error && (
                <p
                    className="auth-error auth-toolbar__error"
                    role="alert"
                >
                    {error}
                </p>
            )}
        </header>
    );
}