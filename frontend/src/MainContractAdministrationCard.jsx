import { useRef, useState } from 'react';
import http from './api/http';
import useCalendarDate from './hooks/useCalendarDate';
import { expiryNotice, formatAdministrationDate, isDocumentSecurity, securityStages } from './utils/contractAdministration';
import { formatBudgetCents } from './utils/budgetForm';
import { formatInvoiceAmount } from './utils/subcontractorInvoiceForm';
import './MainContractAdministrationCard.css';

export default function MainContractAdministrationCard({ administration, contractId, onChange }) {
    const data = administration ?? {};
    const today = useCalendarDate();
    const [saving, setSaving] = useState(null);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const pending = useRef(false);
    const stages = securityStages(data);
    const amount = value => value == null ? 'Nie uzupełniono kwoty' : formatInvoiceAmount(value, 'PLN');

    async function markReturn(stage) {
        if (pending.current || !contractId || !onChange) return;
        pending.current = true;
        setSaving(stage.stage); setError(''); setMessage('');
        try {
            const { data: saved } = await http.patch(`/main-contracts/${contractId}/security-return`,
                { stage: stage.stage, returned: !stage.returned });
            const field = stage.stage === 'PERFORMANCE' ? 'securityReturned' : 'retentionSecurityReturned';
            if (typeof saved?.[field] !== 'boolean' || saved[field] !== !stage.returned) {
                throw new Error('Nie można potwierdzić stanu zwrotu. Odśwież widok umowy.');
            }
            onChange(saved);
            setMessage(stage.returned ? 'Cofnięto oznaczenie zwrotu.' : `Zapisano zwrot: ${stage.label.toLocaleLowerCase('pl-PL')}.`);
        } catch (failure) {
            setError(failure.message || 'Nie udało się zapisać zwrotu zabezpieczenia.');
        } finally {
            pending.current = false; setSaving(null);
        }
    }

    return <section className="card contract-administration" aria-label="Gwarancja, ubezpieczenia i zabezpieczenie">
        <h2>Zakończenie, gwarancja i zabezpieczenia</h2>
        <div className="contract-administration-grid">
            <Item label="Faktyczne zakończenie umowy">{formatAdministrationDate(data.actualCompletionDate)}</Item>
            <Item label="Okres gwarancji i rękojmi">{data.warrantyMonths == null ? 'Nie uzupełniono' : `${data.warrantyMonths} mies.`}</Item>
            <Item label="Koniec gwarancji i rękojmi">{data.warrantyEndDate ? formatAdministrationDate(data.warrantyEndDate) : 'Uzupełnij faktyczne zakończenie i okres gwarancji.'}</Item>
            <Expiry label="Ubezpieczenie OC — ważne do" date={data.ocInsuranceValidUntil} today={today} />
            <Expiry label="Ubezpieczenie CAR — ważne do" date={data.carInsuranceValidUntil} today={today} />
            <Item label="Zabezpieczenie należytego wykonania">{data.securityTypeLabel || 'Nie uzupełniono'}</Item>
            {stages.length > 0 && <Item label="Pełna kwota zabezpieczenia (historyczna)">{amount(data.securityAmount)}</Item>}
            {stages.length > 0 && <Item label="Etapy zabezpieczenia">{data.securityValidityModeLabel || 'Jeden etap — całe zabezpieczenie'}</Item>}
        </div>
        {stages.length > 0 && <div className="security-return-grid">
            {stages.map(stage => <div key={stage.stage} className={`security-return-stage${stage.returned ? ' security-return-stage--returned' : ''}`}>
                <div className="security-return-heading"><h3>{stage.label}</h3>
                    <span className={`security-return-badge${stage.returned ? ' security-return-badge--done' : ''}`}>{stage.returned ? 'Zwrócono' : 'Nie oznaczono zwrotu'}</span>
                </div>
                <p className="security-return-amount">Kwota {stage.returned ? 'zwrócona' : 'do zwrotu'}: <strong>{stage.amountCents == null ? 'Nie uzupełniono kwoty' : formatBudgetCents(stage.amountCents)}</strong></p>
                {!stage.returned && isDocumentSecurity(data.securityType) && <Expiry label="Dokument — ważny do" date={stage.date} today={today} />}
                {contractId && onChange && <button type="button" className="main-form-button" disabled={saving !== null}
                    aria-label={`${stage.returned ? 'Cofnij oznaczenie zwrotu' : 'Oznacz zwrot'}: ${stage.label}`}
                    onClick={() => markReturn(stage)}>
                    {saving === stage.stage ? 'Zapisywanie…' : stage.returned ? 'Cofnij oznaczenie zwrotu' : 'Oznacz jako zwrócone'}
                </button>}
            </div>)}
        </div>}
        {error && <p className="main-form-field-error" role="alert">{error}</p>}
        {message && <p className="security-return-message" role="status">{message}</p>}
        <p className="main-form-hint">Okres gwarancji i rękojmi liczymy w miesiącach kalendarzowych od faktycznego zakończenia umowy. Oznaczenie zwrotu zachowuje kwoty i historyczne terminy.</p>
    </section>;
}
function Item({ label, children }) {
    return <div className="contract-administration-item"><span className="label">{label}</span><strong>{children}</strong></div>;
}
function Expiry({ label, date, today }) {
    const notice = expiryNotice(date, today);
    return <div className={`contract-administration-item${notice ? notice.expired ? ' contract-expiry-expired' : ' contract-expiry-warning' : ''}`}>
        <span className="label">{label}</span><strong>{formatAdministrationDate(date)}</strong>
        {notice && <p className="contract-expiry-notice" role="status">{notice.text}</p>}
    </div>;
}
