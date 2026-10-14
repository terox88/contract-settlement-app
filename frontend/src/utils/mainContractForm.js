import { parseBudgetAmount as parseAmount } from './budgetForm.js';

export const REMUNERATION_TYPES = [
    { value: 'LUMP_SUM', label: 'Ryczałt' },
    { value: 'UNIT_PRICE', label: 'Kosztorysowe' },
];

export const CONTRACTOR_FIELDS = [
    { name: 'name', label: 'Nazwa wykonawcy', required: true, wide: true },
    { name: 'street', label: 'Ulica' },
    { name: 'buildingNumber', label: 'Numer budynku' },
    { name: 'apartmentNumber', label: 'Numer lokalu' },
    { name: 'postalCode', label: 'Kod pocztowy' },
    { name: 'city', label: 'Miejscowość' },
    { name: 'country', label: 'Kraj' },
    { name: 'email', label: 'E-mail', type: 'email', wide: true },
];

export function emptyContract() {
    const date = new Date();
    const today = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
    return {
        contractNumber: '', contractDate: today, initialValue: '', vatRate: '23',
        initialStartDate: '', initialEndDate: '', contractType: '',
        remunerationType: '', shortDescription: '',
    };
}

export function emptyContractor() {
    return Object.fromEntries(CONTRACTOR_FIELDS.map((field) => [
        field.name, field.name === 'country' ? 'Polska' : '',
    ]));
}

export function normalizeTaxId(value) {
    return String(value ?? '').replace(/[^0-9]/g, '');
}

export function validDate(value) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value.startsWith('0000')) return false;
    const date = new Date(`${value}T00:00:00Z`);
    return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

export function contractToForm(data) {
    return Object.fromEntries(Object.keys(emptyContract()).map((field) => [
        field, String(data[field] ?? ''),
    ]));
}

export function validateContractData(contract, contractTypes = []) {
    const errors = {};
    if (!contract.contractNumber.trim()) errors.contractNumber = 'Podaj numer umowy.';
    else if (contract.contractNumber.trim().length > 255) errors.contractNumber = 'Numer może mieć maksymalnie 255 znaków.';
    if (!validDate(contract.contractDate)) errors.contractDate = 'Podaj prawidłową datę zawarcia.';
    if (!contractTypes.some((option) => option.value === contract.contractType)) errors.contractType = 'Wybierz rodzaj umowy z listy.';
    if (!REMUNERATION_TYPES.some((option) => option.value === contract.remunerationType)) errors.remunerationType = 'Wybierz rodzaj wynagrodzenia.';

    const amount = parseAmount(contract.initialValue);
    if (!amount) errors.initialValue = 'Podaj kwotę nieujemną, z maksymalnie dwoma miejscami po przecinku.';
    else if (amount.cents > 999999999999999n) errors.initialValue = 'Kwota przekracza zakres wartości umowy.';
    const vat = parseAmount(contract.vatRate);
    if (!vat || vat.cents > 10000n) errors.vatRate = 'Podaj stawkę od 0 do 100%, z maksymalnie dwoma miejscami po przecinku.';

    for (const field of ['initialStartDate', 'initialEndDate']) {
        if (contract[field] && !validDate(contract[field])) errors[field] = 'Podaj prawidłową datę.';
    }
    if (validDate(contract.initialStartDate) && validDate(contract.initialEndDate)
        && contract.initialEndDate < contract.initialStartDate) {
        errors.initialEndDate = 'Termin zakończenia nie może być wcześniejszy od rozpoczęcia.';
    }
    if (contract.shortDescription.trim().length > 500) errors.shortDescription = 'Opis może mieć maksymalnie 500 znaków.';

    return errors;
}

export function validateMainContractForm(contract, contractor, nip, lookup, selected, contractTypes = []) {
    const errors = validateContractData(contract, contractTypes);

    const taxId = normalizeTaxId(nip);
    if (taxId.length !== 10) errors.taxId = 'Podaj NIP zawierający 10 cyfr.';
    else if (selected) {
        if (!selected.id || normalizeTaxId(selected.taxId) !== taxId) {
            errors.taxId = 'Wyszukaj wykonawcę ponownie dla aktualnego NIP.';
        }
    } else if (lookup.nip !== taxId || lookup.status !== 'missing') {
        errors.taxId = lookup.status === 'found' && lookup.nip === taxId
            ? 'Kliknij „Użyj tego wykonawcy”, aby powiązać umowę ze znalezionym wykonawcą.'
            : 'Poczekaj na sprawdzenie NIP lub ponów wyszukiwanie.';
    }

    if (!selected) {
        if (!contractor.name.trim()) errors['contractor.name'] = 'Podaj nazwę wykonawcy.';
        for (const field of CONTRACTOR_FIELDS) {
            if (contractor[field.name].trim().length > 255) {
                errors[`contractor.${field.name}`] = 'Pole może mieć maksymalnie 255 znaków.';
            }
        }
        const email = contractor.email.trim();
        if (email && !/^[^\s@]+@[^\s@]+$/.test(email)) errors['contractor.email'] = 'Podaj prawidłowy adres e-mail.';
    }
    return errors;
}

export function buildMainContractUpdatePayload(contract) {
    return {
        contractNumber: contract.contractNumber.trim(),
        contractDate: contract.contractDate,
        initialValue: parseAmount(contract.initialValue).amount,
        vatRate: parseAmount(contract.vatRate).amount,
        initialStartDate: contract.initialStartDate || null,
        initialEndDate: contract.initialEndDate || null,
        contractType: contract.contractType,
        remunerationType: contract.remunerationType,
        shortDescription: contract.shortDescription.trim() || null,
    };
}

export function buildMainContractPayload(taskId, contract, contractor, nip, selected) {
    const numericTaskId = Number(taskId);
    const payload = {
        investmentTaskId: Number.isSafeInteger(numericTaskId) ? numericTaskId : String(taskId),
        ...buildMainContractUpdatePayload(contract),
    };

    if (selected) payload.mainContractorId = selected.id;
    else {
        payload.newMainContractor = Object.fromEntries(CONTRACTOR_FIELDS.map((field) => [
            field.name, contractor[field.name].trim() || null,
        ]));
        payload.newMainContractor.taxId = normalizeTaxId(nip);
    }
    return payload;
}
