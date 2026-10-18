import { useEffect, useState } from 'react';
import http from './api/http';
import { loadSameRecipientTargets, readTarget, samePaymentParties, targetKey } from './utils/paymentForm';
import { formatInvoiceAmount } from './utils/subcontractorInvoiceForm';

export default function PaymentInvoicePicker({ currency, onChoose, excluded = [], disabled = false, initialContractId = '', scope = null }) {
    const [contracts, setContracts] = useState([]);
    const [contractId, setContractId] = useState(String(initialContractId));
    const [type, setType] = useState(scope?.type ?? 'sub');
    const [invoices, setInvoices] = useState([]);
    const [query, setQuery] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [attempt, setAttempt] = useState(0);
    useEffect(() => {
        let active = true;
        const controller = new AbortController();
        setError(''); setLoading(true);
        async function load() {
            if (scope) {
                const targets = await loadSameRecipientTargets(http, scope, controller.signal);
                if (active) setInvoices(targets);
                return;
            }
            return Promise.all([
                http.get('/main-contracts', { signal: controller.signal }),
                contractId ? type === 'main'
                    ? http.get(`/main-contractor-invoices/main-contract/${contractId}`, { signal: controller.signal })
                    : http.get('/subcontractor-invoices', { params: { mainContractId: contractId }, signal: controller.signal })
                    : Promise.resolve({ data: [] }),
            ]).then(([contractResponse, invoiceResponse]) => {
                if (!active) return;
                if (!Array.isArray(contractResponse.data) || contractResponse.data.some((contract) => !contract.id)) throw new Error('Nie można odczytać listy umów.');
                if (!Array.isArray(invoiceResponse.data)) throw new Error('Nie można odczytać listy faktur.');
                const rows = invoiceResponse.data.map((invoice) => readTarget(invoice, {
                    type, contractId, id: invoice.id, subcontractId: invoice.subcontractId,
                }));
                setContracts(contractResponse.data); setInvoices(rows);
            });
        }
        load().catch((failure) => { if (active) setError(failure.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [contractId, type, scope?.type, scope?.recipientContractorId, scope?.payerContractorId, attempt]);
    const needle = query.trim().toLocaleLowerCase('pl-PL');
    const visible = invoices.filter((target) => target.currency.toUpperCase() === currency.trim().toUpperCase()
        && (!scope || samePaymentParties(scope, target))
        && !excluded.includes(targetKey(target))
        && `${target.number} ${target.name}`.toLocaleLowerCase('pl-PL').includes(needle));
    return <fieldset disabled={disabled} className="settlement-fields payment-picker"><legend>Wybierz fakturę tego samego odbiorcy</legend>
        {!scope && <><label htmlFor="payment-picker-contract">Umowa główna</label>
        <select id="payment-picker-contract" value={contractId} onChange={(event) => { setContractId(event.target.value); setInvoices([]); }}>
            <option value="">Wybierz umowę</option>{contracts.map((contract) => <option key={contract.id} value={contract.id}>{contract.contractNumber} · {contract.mainContractorName}</option>)}
        </select>
        <label htmlFor="payment-picker-type">Rodzaj faktury</label><select id="payment-picker-type" disabled={Boolean(scope)} value={type} onChange={(event) => { setType(event.target.value); setInvoices([]); }}>
            <option value="sub">Faktura podwykonawcy</option><option value="main">Faktura głównego wykonawcy</option>
        </select></>}
        {scope && <p className="invoice-muted">Faktury tego samego odbiorcy i zleceniodawcy. {scope.name || ''}</p>}
        {loading ? <p role="status">Ładowanie faktur...</p> : error ? <div role="alert" className="error-message">{error}
            <button type="button" onClick={() => setAttempt((value) => value + 1)}>Spróbuj ponownie</button></div> : (scope || contractId) && <>
            <label htmlFor="payment-picker-search">Numer faktury lub wykonawca</label><input id="payment-picker-search" value={query} onChange={(event) => setQuery(event.target.value)} />
            <label htmlFor="payment-picker-invoice">Dodaj fakturę do przelewu · {currency}</label>
            <select id="payment-picker-invoice" value="" onChange={(event) => {
                const target = visible.find((invoice) => targetKey(invoice) === event.target.value);
                if (target) onChoose(target);
            }}><option value="">Wybierz fakturę</option>{visible.map((target) => <option key={targetKey(target)} value={targetKey(target)}>
                {target.name} · {target.number} · brutto {formatInvoiceAmount(target.grossAmount, target.currency)}</option>)}</select>
            {!visible.length && <p className="invoice-muted">Brak pasujących faktur w walucie przelewu albo wszystkie zostały już wybrane.</p>}
        </>}
    </fieldset>;
}
