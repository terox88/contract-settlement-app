import { useEffect, useRef, useState } from 'react';
import http from './api/http';
import { emptyInvoice, formatInvoiceAmount, formatInvoiceCents } from './utils/subcontractorInvoiceForm';
import { parseBudgetAmount } from './utils/budgetForm';
import { formatAmendmentDate } from './utils/contractAmendmentForm';
import { penaltyRequest, readPenalties, validatePenalty } from './utils/penalties';
import './ReviewPanels.css';

export default function ContractPenaltiesCard({ contract, onChange }) {
    const [items, setItems] = useState([]), [loading, setLoading] = useState(true), [error, setError] = useState('');
    const [actionError, setActionError] = useState(''), [success, setSuccess] = useState(''), [attempt, setAttempt] = useState(0);
    const [editing, setEditing] = useState(null), [removing, setRemoving] = useState(null), [busy, setBusy] = useState(false);
    const alive = useRef(false), lock = useRef(false);
    const url = `/contract-penalties/contract/${contract.id}`;
    useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
    useEffect(() => {
        let active = true; const controller = new AbortController(); setLoading(true); setError('');
        http.get(url, { signal: controller.signal }).then(({data}) => { const rows = readPenalties(data, contract.id); if (active) setItems(rows); })
            .catch(failure => { if (active) setError(failure.message); }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [contract.id, attempt]);
    async function mutate(request) {
        if (lock.current || loading || error) return;
        lock.current = true; setBusy(true); setActionError(''); setSuccess('');
        try {
            await http[request.method](request.url, request.data);
            if (alive.current) { setEditing(null); setRemoving(null); setSuccess('Zapisano zmianę w karach umownych.'); }
            try {
                const { data } = await http.get(url); const rows = readPenalties(data, contract.id);
                if (alive.current) setItems(rows); await onChange?.();
            } catch { if (alive.current) setError('Zmiana została zapisana. Odśwież dane kar i podsumowanie umowy.'); }
        } catch (failure) { if (alive.current) setActionError(failure.message); }
        finally { lock.current = false; if (alive.current) setBusy(false); }
    }
    const total = items.reduce((sum,row) => sum + parseBudgetAmount(row.amount).cents, 0n);
    return <section className="card contract-penalties-card" aria-label="Kary umowne"><h2>Kary umowne</h2>
        <p>Rejestr naliczonych kar. Rozliczenie kar z wykonawcą prowadzisz osobno od kwot faktur i przelewów.</p>
        {success && <p className="attachments-success" role="status">{success}</p>}{actionError && <p className="error-message" role="alert">{actionError}</p>}
        {loading ? <p role="status">Ładowanie kar...</p> : error ? <p className="error-message" role="alert">{error}<button type="button" disabled={busy} onClick={() => setAttempt(v => v + 1)}>Odśwież</button></p> : <>
            <p>Łączna wartość kar: <strong>{formatInvoiceCents(total, 'PLN')}</strong></p>
            {!items.length && <p>Nie wprowadzono kar umownych.</p>}
            {items.map(item => <article key={item.id} className="penalty-editor"><strong>{formatInvoiceAmount(item.amount)}</strong> · {formatAmendmentDate(item.imposedDate)}
                {item.description && <p>{item.description}</p>}<div className="review-actions">
                    <button type="button" className="main-form-button" disabled={busy} onClick={() => { setEditing(item); setRemoving(null); }}>Edytuj karę</button>
                    <button type="button" className="main-form-button" disabled={busy} onClick={() => { setRemoving(item); setEditing(null); }}>Usuń karę</button></div>
            </article>)}
            {removing && <div className="review-panel"><p>Usunąć karę {formatInvoiceAmount(removing.amount)} z {formatAmendmentDate(removing.imposedDate)}?</p>
                <button type="button" className="main-form-button" disabled={busy} onClick={() => mutate({ method: 'delete', url: `/contract-penalties/${removing.id}` })}>Potwierdź usunięcie kary</button>
                <button type="button" className="main-form-button" disabled={busy} onClick={() => setRemoving(null)}>Anuluj</button></div>}
            {editing ? <PenaltyForm key={editing.id ?? 'new'} contract={contract} item={editing} disabled={busy} onSave={mutate} onCancel={() => setEditing(null)} />
                : <button type="button" className="main-form-button main-form-button-primary" disabled={busy} onClick={() => { setEditing({}); setRemoving(null); }}>Dodaj karę umowną</button>}
        </>}
    </section>;
}
function PenaltyForm({ contract, item, disabled, onSave, onCancel }) {
    const [form, setForm] = useState({ imposedDate: item.imposedDate ?? emptyInvoice().issueDate, amount: item.amount == null ? '' : String(item.amount), description: item.description ?? '' });
    const [errors, setErrors] = useState({});
    return <form className="penalty-editor" noValidate onSubmit={event => {
        event.preventDefault(); if (disabled) return;
        const errors = validatePenalty(form, contract.contractDate); setErrors(errors);
        if (!Object.keys(errors).length) onSave(penaltyRequest(contract, item.id, form));
    }}><fieldset disabled={disabled}><legend>{item.id ? 'Edytuj karę' : 'Nowa kara'}</legend>
        <label>Data nałożenia *<input name="imposedDate" type="date" value={form.imposedDate} onChange={event => setForm(current => ({ ...current, imposedDate: event.target.value }))} /></label>
        {errors.imposedDate && <p className="main-form-field-error" role="alert">{errors.imposedDate}</p>}
        <label>Kwota kary (zł) *<input name="amount" inputMode="decimal" value={form.amount} onChange={event => setForm(current => ({ ...current, amount: event.target.value }))} /></label>
        {errors.amount && <p className="main-form-field-error" role="alert">{errors.amount}</p>}
        <label>Opis / podstawa naliczenia<textarea name="description" maxLength={1000} value={form.description} onChange={event => setForm(current => ({ ...current, description: event.target.value }))} /></label>
        {errors.description && <p className="main-form-field-error" role="alert">{errors.description}</p>}
        <div className="review-actions"><button className="main-form-button main-form-button-primary">Zapisz karę</button><button type="button" className="main-form-button" onClick={onCancel}>Anuluj</button></div>
    </fieldset></form>;
}
