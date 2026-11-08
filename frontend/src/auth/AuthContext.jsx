import {
    createContext,
    useCallback,
    useContext,
    useEffect,
    useMemo,
    useRef,
    useState,
} from 'react';

import http, {
    resetAuthTransport,
    subscribeAuthEvents,
} from '../api/http';

import { asCurrentUser } from './authUtils';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
    const [user, setUser] = useState(null);
    const [status, setStatus] = useState('loading');
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [busy, setBusy] = useState(false);
    const [attempt, setAttempt] = useState(0);

    const busyRef = useRef(false);

    useEffect(() => {
        let active = true;
        const controller = new AbortController();

        setStatus('loading');
        setError('');

        const unsubscribe = subscribeAuthEvents((event) => {
            if (event === 'signed-out') {
                setUser(null);
                setNotice('Sesja wygasła. Zaloguj się ponownie.');
            } else if (event === 'password-required') {
                setUser((previous) => previous
                    ? {
                        ...previous,
                        passwordChangeRequired: true,
                    }
                    : null);
            }
        });

        http.get('/auth/me', {
            signal: controller.signal,
            skipAuthEvents: true,
        })
            .then(({ data }) => {
                if (active) {
                    setUser(asCurrentUser(data));
                    setStatus('ready');
                }
            })
            .catch((failure) => {
                if (!active || failure.code === 'ERR_CANCELED') {
                    return;
                }

                setUser(null);

                if (failure.response?.status === 401) {
                    setStatus('ready');
                } else {
                    setError(failure.message);
                    setStatus('error');
                }
            });

        return () => {
            active = false;
            controller.abort();
            unsubscribe();
        };
    }, [attempt]);

    const begin = useCallback(() => {
        if (busyRef.current) {
            throw new Error(
                'Poczekaj na zakończenie bieżącej operacji.'
            );
        }

        busyRef.current = true;
        setBusy(true);
        resetAuthTransport();
    }, []);

    const end = useCallback(() => {
        busyRef.current = false;
        setBusy(false);
    }, []);

    const login = useCallback(async (username, password) => {
        begin();
        setNotice('');

        try {
            const body = new URLSearchParams({
                username: username.trim(),
                password,
            });

            const { data } = await http.post('/auth/login', body, {
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                skipAuthEvents: true,
            });

            const current = asCurrentUser(data);

            resetAuthTransport();
            setUser(current);
        } catch (failure) {
            resetAuthTransport();

            if (failure.code !== 'ERR_CANCELED') {
                setUser(null);
            }

            throw failure;
        } finally {
            end();
        }
    }, [begin, end]);

    const logout = useCallback(async () => {
        begin();

        try {
            try {
                await http.post('/auth/logout', null, {
                    skipAuthEvents: true,
                });
            } catch (failure) {
                if (failure.response?.status !== 401) {
                    throw failure;
                }
            }

            resetAuthTransport();
            setUser(null);
            setNotice('Wylogowano.');
        } finally {
            end();
        }
    }, [begin, end]);

    const changePassword = useCallback(
        async (currentPassword, newPassword) => {
            begin();

            try {
                await http.post('/auth/password', {
                    currentPassword,
                    newPassword,
                });

                resetAuthTransport();
                setUser(null);
                setNotice(
                    'Hasło zostało zmienione. Zaloguj się nowym hasłem.'
                );
            } finally {
                end();
            }
        },
        [begin, end]
    );

    const finishSession = useCallback((message) => {
        resetAuthTransport();
        setUser(null);
        setError('');
        setNotice(message);
        setStatus('ready');
    }, []);

    const retry = useCallback(
        () => setAttempt((previous) => previous + 1),
        []
    );

    const value = useMemo(() => ({
        user,
        status,
        error,
        notice,
        busy,
        login,
        logout,
        changePassword,
        finishSession,
        retry,
        canEdit: user?.role === 'EDITOR' || user?.role === 'ADMIN',
        isAdmin: user?.role === 'ADMIN',
    }), [
        user,
        status,
        error,
        notice,
        busy,
        login,
        logout,
        changePassword,
        finishSession,
        retry,
    ]);

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const context = useContext(AuthContext);

    if (!context) {
        throw new Error('useAuth wymaga AuthProvider.');
    }

    return context;
}