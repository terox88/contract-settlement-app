export function makeUserEditor(mode, account, roles) {
    return {
        mode,
        account,
        username: account?.username ?? '',
        displayName: account?.displayName ?? '',
        role: account?.role
            ?? roles.find((role) => role.value === 'READ_ONLY')?.value
            ?? '',
        enabled: account?.enabled ?? true,
        temporaryPassword: '',
        repeatedPassword: '',
    };
}

export function validateUserEditor(editor) {
    if (editor.mode === 'create'
        && !/^[a-z0-9][a-z0-9._-]{2,63}$/.test(
            editor.username.trim().toLowerCase()
        )) {
        return 'Login musi mieć 3–64 znaki: litery a–z, cyfry, kropkę, myślnik lub podkreślenie. Pierwszy znak musi być literą albo cyfrą.';
    }

    if (editor.mode !== 'reset') {
        if (!editor.displayName.trim()
            || editor.displayName.length > 150) {
            return 'Podaj nazwę użytkownika, maksymalnie 150 znaków.';
        }

        if (!editor.role) return 'Wybierz rolę.';
    }

    if (editor.mode !== 'edit') {
        const length = Array.from(editor.temporaryPassword).length;

        if (!editor.temporaryPassword.trim()
            || length < 8 || length > 128) {
            return 'Hasło tymczasowe musi mieć od 8 do 128 znaków.';
        }

        if (editor.temporaryPassword !== editor.repeatedPassword) {
            return 'Hasła tymczasowe muszą być identyczne.';
        }
    }

    return '';
}

export function userEditorBody(editor) {
    if (editor.mode === 'reset') {
        return {
            version: editor.account.version,
            temporaryPassword: editor.temporaryPassword,
        };
    }

    if (editor.mode === 'edit') {
        return {
            version: editor.account.version,
            displayName: editor.displayName.trim(),
            role: editor.role,
            enabled: editor.enabled,
        };
    }

    return {
        username: editor.username.trim().toLowerCase(),
        displayName: editor.displayName.trim(),
        role: editor.role,
        temporaryPassword: editor.temporaryPassword,
    };
}

export function mergeUser(users, saved) {
    return [...users.filter((user) => user.id !== saved.id), saved]
        .sort((first, second) =>
            first.username.localeCompare(second.username, 'pl'));
}

export function requiresUserReload(failure, mode) {
    return (failure.response?.status === 409 && mode !== 'create')
        || (!failure.response && failure.code !== 'ERR_CANCELED');
}