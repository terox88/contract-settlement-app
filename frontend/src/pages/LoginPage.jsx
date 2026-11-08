import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';

export default function LoginPage() {
    const { login, busy, notice } = useAuth();

    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');

    async function submit(event) {
        event.preventDefault();

        if (busy) return;

        setError('');

        if (!username.trim() || !password) {
            setError('Podaj login i hasło.');
            return;
        }

        try {
            await login(username, password);
        } catch (failure) {
            if (failure.code !== 'ERR_CANCELED') {
                setError(failure.message);
            }
        } finally {
            setPassword('');
        }
    }

    return (
        <main className="auth-screen">
            <section className="auth-card" aria-labelledby="login-title">
                <h1 id="login-title">Rozliczenia umów</h1>
                <p>Zaloguj się na swoje konto.</p>

                {notice && (
                    <p className="auth-notice" role="status">
                        {notice}
                    </p>
                )}

                {error && (
                    <p className="auth-error" role="alert">
                        {error}
                    </p>
                )}

                <form method="post" onSubmit={submit} noValidate>
                    <label className="auth-field" htmlFor="auth-username">
                        Login
                        <input
                            id="auth-username"
                            name="username"
                            autoComplete="username"
                            value={username}
                            onChange={(event) =>
                                setUsername(event.target.value)}
                            maxLength={64}
                            disabled={busy}
                            required
                            autoFocus
                        />
                    </label>

                    <label className="auth-field" htmlFor="auth-password">
                        Hasło
                        <input
                            id="auth-password"
                            name="password"
                            type="password"
                            autoComplete="current-password"
                            value={password}
                            onChange={(event) =>
                                setPassword(event.target.value)}
                            maxLength={256}
                            disabled={busy}
                            required
                        />
                    </label>

                    <button
                        className="auth-button auth-button--primary"
                        type="submit"
                        disabled={busy}
                    >
                        {busy ? 'Logowanie…' : 'Zaloguj się'}
                    </button>
                </form>
            </section>
        </main>
    );
}