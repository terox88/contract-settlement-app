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
        const message =
            error.response?.data?.message
            ?? 'Wystąpił nieoczekiwany błąd';

        return Promise.reject(
            new Error(message)
        );
    }
);

export default http;