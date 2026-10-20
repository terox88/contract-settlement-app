import { useEffect, useState } from 'react';
import { expiryNotice, formatAdministrationDate, isDocumentSecurity, localToday } from './utils/contractAdministration';
import { formatInvoiceAmount } from './utils/subcontractorInvoiceForm';
import './MainContractAdministrationCard.css';

export default function MainContractAdministrationCard({ administration }) {
    const data = administration ?? {};
    const [today, setToday] = useState(localToday);
    useEffect(() => {
        const refresh = () => setToday(localToday());
        const interval = setInterval(refresh, 60000);
        window.addEventListener('focus', refresh);
        document.addEventListener('visibilitychange', refresh);
        return () => { clearInterval(interval); window.removeEventListener('focus', refresh); document.removeEventListener('visibilitychange', refresh); };
    }, []);
    const amount = value => value == null ? 'Nie uzupełniono kwoty' : formatInvoiceAmount(value, 'PLN');
    return <section className="card contract-administration" aria-label="Gwarancja, ubezpieczenia i zabezpieczenie">
        <h2>Zakończenie, gwarancja i zabezpieczenia</h2>
        <div className="contract-administration-grid">
            <Item label="Faktyczne zakończenie umowy">{formatAdministrationDate(data.actualCompletionDate)}</Item>
            <Item label="Okres gwarancji i rękojmi">{data.warrantyMonths == null ? 'Nie uzupełniono' : `${data.warrantyMonths} mies.`}</Item>
            <Item label="Koniec gwarancji i rękojmi">{data.warrantyEndDate ? formatAdministrationDate(data.warrantyEndDate) : 'Uzupełnij faktyczne zakończenie i okres gwarancji.'}</Item>
            <Expiry label="Ubezpieczenie OC — ważne do" date={data.ocInsuranceValidUntil} today={today} />
            <Expiry label="Ubezpieczenie CAR — ważne do" date={data.carInsuranceValidUntil} today={today} />
            <Item label="Zabezpieczenie należytego wykonania">{data.securityTypeLabel || 'Nie uzupełniono'}</Item>
            {data.securityType && data.securityType !== 'NOT_ESTABLISHED' && <Item label="Pełna kwota zabezpieczenia">{amount(data.securityAmount)}</Item>}
            {isDocumentSecurity(data.securityType) && <>
                <Item label="Wariant zabezpieczenia">{data.securityValidityModeLabel || 'Nie uzupełniono'}</Item>
                <Expiry label={data.securityValidityMode === 'TWO_STAGE' ? 'Zabezpieczenie realizacji — ważne do' : 'Całe zabezpieczenie — ważne do'} date={data.securityValidUntil} today={today} />
                {data.securityValidityMode === 'TWO_STAGE' && <>
                    <Item label="Zredukowana kwota na gwarancję i rękojmię">{amount(data.retentionSecurityAmount)}</Item>
                    <Expiry label="Zredukowane zabezpieczenie — ważne do" date={data.retentionSecurityValidUntil} today={today} />
                </>}
            </>}
        </div>
        <p className="main-form-hint">Okres gwarancji i rękojmi liczymy w miesiącach kalendarzowych od faktycznego zakończenia umowy. Terminy polis i dokumentów zabezpieczenia są wprowadzane niezależnie.</p>
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
