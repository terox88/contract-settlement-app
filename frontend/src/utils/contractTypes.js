export function readContractTypeOptions(data) {
    if (!Array.isArray(data) || data.length === 0) {
        throw new Error('Nie można wczytać rodzajów umowy. Spróbuj ponownie.');
    }
    const values = new Set();
    return data.map((option) => {
        if (!option || typeof option.value !== 'string' || !option.value.trim()
            || typeof option.label !== 'string' || !option.label.trim()
            || values.has(option.value)) {
            throw new Error('Nie można wczytać rodzajów umowy. Spróbuj ponownie.');
        }
        values.add(option.value);
        return { value: option.value, label: option.label };
    });
}

export function formatContractType(contract) {
    return contract?.contractTypeLabel || contract?.contractType || '—';
}
