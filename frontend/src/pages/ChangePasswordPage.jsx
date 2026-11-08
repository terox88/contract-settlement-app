import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { useAuth } from '../auth/AuthContext';
import {
    passwordChangeError,
    safeReturnPath,
} from '../auth/authUtils';

export default function ChangePasswordPage({ forced }) {
    const { changePassword, busy } = useAuth();

    const location = useLocation();
    const navigate = useNavigate();

    const [current, setCurrent] = useState('');
    const [next, setNext] = useState('');
    const [repeated, setRepeated] = useState('');
    const [error, setError] = useState('');

    async function submit(event) {
        event.preventDefault();

        if (busy) return;

        const validationError = passwordChangeError(
            current,
            next,
            repeated
        );

        setError(validationError);

        if (validationError) return;

        try {
            await changePassword(current, next);
        } catch (failure) {
            if (failure.code !== 'ERR_CANCELED') {
                setError(failure.message);
                setCurrent('');
            }
        }
    }

    return (
        <main className="auth-screen auth-screen--inside">
            <section
                className="auth-card"
                aria-labelledby="password-title"
            >
                <h1 id="password-title">Zmień hasło</h1>

                <p>
                    {forced
                        ? 'Przed dostępem do danych zmień hasło tymczasowe.'
                        : 'Po zmianie hasła zalogujesz się ponownie.'}
                </p>

                <p>
                    Nowe hasło: 8–128 znaków.
                    Możesz używać spacji i polskich znaków.
                </p>

                {error && (
                    <p className="auth-error" role="alert">
                        {error}
                    </p>
                )}

                <form method="post" onSubmit={submit} noValidate>
                    <label
                        className="auth-field"
                        htmlFor="auth-current-password"
                    >
                        Obecne hasło
                        <input
                            id="auth-current-password"
                            type="password"
                            name="currentPassword"
                            autoComplete="current-password"
                            value={current}
                            onChange={(event) =>
                                setCurrent(event.target.value)}
                            maxLength={256}
                            disabled={busy}
                            required
                            autoFocus
                        />
                    </label>

                    <label
                        className="auth-field"
                        htmlFor="auth-new-password"
                    >
                        Nowe hasło
                        <input
                            id="auth-new-password"
                            type="password"
                            name="newPassword"
                            autoComplete="new-password"
                            value={next}
                            onChange={(event) =>
                                setNext(event.target.value)}
                            maxLength={256}
                            disabled={busy}
                            required
                        />
                    </label>

                    <label
                        className="auth-field"
                        htmlFor="auth-repeat-password"
                    >
                        Powtórz nowe hasło
                        <input
                            id="auth-repeat-password"
                            type="password"
                            name="repeatPassword"
                            autoComplete="new-password"
                            value={repeated}
                            onChange={(event) =>
                                setRepeated(event.target.value)}
                            maxLength={256}
                            disabled={busy}
                            required
                        />
                    </label>

                    <div className="auth-actions">
                        <button
                            className="auth-button auth-button--primary"
                            type="submit"
                            disabled={busy}
                        >
                            {busy ? 'Zapisywanie…' : 'Zmień hasło'}
                        </button>

                        {!forced && (
                            <button
                                className="auth-button"
                                type="button"
                                disabled={busy}
                                onClick={() => navigate(
                                    safeReturnPath(location.state?.from),
                                    { replace: true }
                                )}
                            >
                                Anuluj
                            </button>
                        )}
                    </div>
                </form>
            </section>
        </main>
    );
}