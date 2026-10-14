import axios from 'axios';

const http = axios.create({
    baseURL: '/api',
    headers: {
        'Content-Type': 'application/json',
    },
});

http.interceptors.response.use(
    (response) => response,

    (error) => {
        const data = error.response?.data;
        const message = data?.message ?? data?.detail;

        error.message =
            typeof message === 'string' && message.trim()
                ? message
                : !error.response && error.code !== 'ERR_CANCELED'
                    ? 'Nie można połączyć się z serwerem. Spróbuj ponownie.'
                    : 'Wystąpił nieoczekiwany błąd';

        // Zachowujemy response.status i response.data dla wywołujących.
        return Promise.reject(error);
    }
);

export default http;