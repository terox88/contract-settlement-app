import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import http from '../api/http';
import SubcontractsCard from '../SubcontractsCard';
import AttachmentDisclosure from '../AttachmentDisclosure';
import ContractAmendmentsCard from '../ContractAmendmentsCard';
import { formatContractType } from '../utils/contractTypes';
import { formatAmendmentDate } from '../utils/contractAmendmentForm';
import { formatDeductionRule, formatSubcontractAmount } from '../utils/subcontractForm';
import './MainContractFormPage.css';
import './SubcontractorInvoicesPage.css';
import './PaymentsPage.css';

export default function SubcontractDetailsPage() {
    const { contractId, subcontractId } = useParams();
    return <Details key={`${contractId}-${subcontractId}`} contractId={contractId} subcontractId={subcontractId} />;
}

function Details({ contractId, subcontractId }) {
    const [contract, setContract] = useState(null), [error, setError] = useState(''), [loading, setLoading] = useState(true), [attempt, setAttempt] = useState(0);
    const base = `/main-contracts/${contractId}/subcontracts/${subcontractId}`;
    useEffect(() => {
        let active = true; const controller = new AbortController(); setLoading(true); setError('');
        http.get(`/subcontracts/${subcontractId}`, { signal: controller.signal }).then(({ data }) => {
            if (!active) return;
            if (String(data?.id) !== String(subcontractId) || String(data.mainContractId) !== String(contractId)) throw new Error('Nieprawidłowa umowa podwykonawcza.');
            setContract(data);
        }).catch((failure) => { if (active) setError(failure.message); }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [contractId, subcontractId, attempt]);
    if (loading) return <p role="status">Ładowanie szczegółów umowy...</p>;
    if (error) return <div className="error-message" role="alert">{error}<button onClick={() => setAttempt((value) => value + 1)}>Ponów</button></div>;
    if (!contract) return null;
    const parentPath = contract.parentSubcontractId ? `/main-contracts/${contractId}/subcontracts/${contract.parentSubcontractId}` : `/main-contracts/${contractId}`;
    return <>
        <Link to={parentPath} className="back-link">← Umowa zleceniodawcy</Link>
        <section className="card main-form-card"><div className="invoice-heading"><div>
            <h1>{contract.subcontractorName}</h1><p>Umowa {contract.contractNumber} · NIP {contract.subcontractorTaxId}</p>
            <p>Zleceniodawca: {contract.payerContractorName} · {formatContractType(contract)}</p>
        </div><div className="settlement-actions"><Link className="main-form-button" to={`${base}/edit`}>Edytuj umowę</Link>
            <Link className="main-form-button main-form-button-primary" to={`${base}/invoices`}>Faktury i rozliczenia</Link></div></div>
            {contract.shortDescription && <p>{contract.shortDescription}</p>}
            <dl className="invoice-metrics">
                <Metric label="Wartość pierwotna brutto" value={formatSubcontractAmount(contract.initialValue)} />
                <Metric label="Aktualna wartość brutto" value={formatSubcontractAmount(contract.currentValue)} />
                <Metric label="Zafakturowano" value={formatSubcontractAmount(contract.invoicedAmount)} />
                <Metric label="Zapłacono tej firmie" value={formatSubcontractAmount(contract.paidAmount)} />
                <Metric label="Potrącono" value={formatSubcontractAmount(contract.deductedAmount)} />
                <Metric label="Saldo faktur" value={formatSubcontractAmount(contract.outstandingInvoiceAmount)} />
                <Metric label="Data zawarcia" value={formatAmendmentDate(contract.contractDate)} />
                <Metric label="Rozpoczęcie" value={formatAmendmentDate(contract.currentStartDate)} />
                <Metric label="Zakończenie" value={formatAmendmentDate(contract.currentEndDate)} />
                <Metric label="Zabezpieczenie" value={formatDeductionRule(contract.performanceSecurityDeduction)} />
                <Metric label="Inne potrącenia" value={formatDeductionRule(contract.otherDeduction)} />
            </dl>
            <p className="invoice-muted">Płatności tej firmy i jej dalszych podwykonawców są odrębnymi rozliczeniami.</p>
        </section>
        <AttachmentDisclosure ownerType="contract" ownerId={contract.id} title="Dokumenty umowy — dodaj lub otwórz" />
        <ContractAmendmentsCard contract={contract} basePath={base} />
        <SubcontractsCard contract={contract} parentSubcontractId={contract.id} />
    </>;
}
function Metric({ label, value }) { return <div><dt>{label}</dt><dd>{value ?? '—'}</dd></div>; }
