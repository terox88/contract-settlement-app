import axios from 'axios';

const http = axios.create({
    baseURL: '/api',
    withCredentials: true,
    headers: {
        'Content-Type': 'application/json',
    },
});

const csrfHttp = axios.create({
    baseURL: '/api',
    withCredentials: true,
    headers: {
        Accept: 'application/json',
    },
});

const listeners = new Set();

let generation = 0;
let csrfFlight = null;

export function subscribeAuthEvents(listener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
}

export function resetAuthTransport() {
    generation += 1;
    csrfFlight = null;
}

function getCsrf() {
    if (!csrfFlight) {
        const started = generation;

        const promise = csrfHttp
            .get('/auth/csrf', { skipAuthEvents: true })
            .then(({ data }) => {
                if (started !== generation) {
                    throw new axios.CanceledError();
                }

                if (
                    !data
                    || typeof data.headerName !== 'string'
                    || !data.headerName.trim()
                    || typeof data.token !== 'string'
                    || !data.token.trim()
                ) {
                    throw new Error(
                        'Nie udało się przygotować żądania. Spróbuj ponownie.'
                    );
                }

                return data;
            })
            .finally(() => {
                if (csrfFlight?.promise === promise) {
                    csrfFlight = null;
                }
            });

        csrfFlight = { promise };
    }

    return csrfFlight.promise;
}

http.interceptors.request.use(async (config) => {
    const started = generation;

    config.authGeneration = started;
    config.headers = { ...config.headers };

    if (
        typeof FormData !== 'undefined'
        && config.data instanceof FormData
    ) {
        // Przeglądarka ustawi Content-Type razem z granicą multipart.
        config.headers['Content-Type'] = undefined;
    }

    const method = (config.method ?? 'get').toUpperCase();

    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
        const csrf = await getCsrf();

        if (started !== generation || config.signal?.aborted) {
            throw new axios.CanceledError();
        }

        config.headers[csrf.headerName] = csrf.token;
    }

    return config;
});

http.interceptors.response.use(
    (response) => response,

    (error) => {
        if (axios.isCancel(error)) {
            return Promise.reject(error);
        }

        const status = error.response?.status;
        const data = error.response?.data;
        const code = data?.code;
        const config = error.config;

        if (
            !config?.skipAuthEvents
            && config?.authGeneration === generation
        ) {
            if (status === 401) {
                resetAuthTransport();
                listeners.forEach((listener) => listener('signed-out'));
            } else if (
                status === 403
                && code === 'PASSWORD_CHANGE_REQUIRED'
            ) {
                listeners.forEach(
                    (listener) => listener('password-required')
                );
            }
        }

        const message = data?.message ?? data?.detail;

        if (code === 'CSRF_INVALID') {
            error.message =
                'Nie udało się potwierdzić żądania. Spróbuj ponownie.';
        } else if (typeof message === 'string' && message.trim()) {
            error.message = message;
        } else if (
            !error.response
            && error.message
            && !error.config
        ) {
            // Zachowaj komunikat lokalnej walidacji odpowiedzi CSRF.
        } else if (!error.response) {
            error.message =
                'Nie można połączyć się z serwerem. Spróbuj ponownie.';
        } else {
            error.message = 'Wystąpił nieoczekiwany błąd';
        }

        return Promise.reject(error);
    }
);

export default http;