export function safeReturnPath(value) {
    if (
        typeof value !== 'string'
        || !value.startsWith('/')
        || value.startsWith('//')
        || /[\\\u0000-\u001f\u007f]/.test(value)
    ) {
        return '/tasks';
    }

    try {
        const url = new URL(value, 'https://app.invalid');
        const path =
            decodeURIComponent(url.pathname).replace(/\/+$/, '') || '/';

        if (
            url.origin !== 'https://app.invalid'
            || path === '/login'
            || path === '/change-password'
            || path === '/api'
            || path.startsWith('/api/')
        ) {
            return '/tasks';
        }

        return url.pathname + url.search + url.hash;
    } catch {
        return '/tasks';
    }
}

export function asCurrentUser(data) {
    if (
        !data
        || typeof data.username !== 'string'
        || !data.username.trim()
        || !['READ_ONLY', 'EDITOR', 'ADMIN'].includes(data.role)
        || typeof data.passwordChangeRequired !== 'boolean'
    ) {
        throw new Error(
            'Serwer zwrócił nieprawidłowe dane logowania.'
        );
    }

    return data;
}

export function passwordChangeError(current, next, repeated) {
    if (!current) {
        return 'Podaj obecne hasło.';
    }

    const length = Array.from(next).length;

    if (!next.trim() || length < 8 || length > 128) {
        return 'Nowe hasło musi mieć od 8 do 128 znaków.';
    }

    if (next !== repeated) {
        return 'Nowe hasła muszą być identyczne.';
    }

    if (next === current) {
        return 'Nowe hasło musi różnić się od obecnego.';
    }

    return '';
}