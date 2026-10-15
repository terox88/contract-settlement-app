import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import http from './api/http';
import { formatContractType } from './utils/contractTypes';
import { formatAmendmentDate } from './utils/contractAmendmentForm';
import { REMUNERATION_TYPES } from './utils/mainContractForm';
import { formatBudgetCents } from './utils/budgetForm';
import {
    formatDeductionRule, formatSubcontractAmount, getSubcontractEndWarning, getSubcontractsSummary, readSubcontracts,
} from './utils/subcontractForm';
import './SubcontractsCard.css';

export default function SubcontractsCard({ contract }) {
    const contractId = contract.id;
    const [subcontracts, setSubcontracts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [loadAttempt, setLoadAttempt] = useState(0);
    const [summaryContract, setSummaryContract] = useState(contract);

    useEffect(() => {
        let active = true;
        const controller = new AbortController();
        setLoading(true);
        setError('');
        Promise.all([
            http.get('/subcontracts', { params: { mainContractId: contractId }, signal: controller.signal }),
            http.get(`/main-contracts/${contractId}`, { signal: controller.signal }),
        ])
            .then(([response, mainResponse]) => {
                if (!active) return;
                if (String(mainResponse.data?.id) !== String(contractId)) {
                    throw new Error('Nie można odczytać tej umowy głównej. Spróbuj ponownie.');
                }
                const rows = readSubcontracts(response.data, contractId);
                setSubcontracts(rows);
                setSummaryContract(mainResponse.data);
            })
            .catch((failure) => { if (active) setError(failure.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [contractId, loadAttempt]);

    const summary = getSubcontractsSummary(summaryContract, subcontracts);

    return (
        <section className="card subcontracts-card" aria-label="Umowy podwykonawcze">
            <div className="subcontracts-heading">
                <div><h2>Umowy podwykonawcze</h2><p>Umowy powiązane z tą umową główną.</p></div>
                <Link to={`/main-contracts/${contractId}/subcontracts/new`} className="main-form-button">Dodaj umowę podwykonawczą</Link>
            </div>
            {loading ? <p className="subcontracts-muted" role="status">Ładowanie umów podwykonawczych...</p>
                : error ? <div className="error-message" role="alert">
                    {error}<button type="button" className="retry-button"
                        onClick={() => setLoadAttempt((value) => value + 1)}>Spróbuj ponownie</button>
                </div> : <>
                    <dl className="subcontracts-summary">
                        <Metric label="Suma aktualnych wartości podwykonawczych brutto" value={formatBudgetCents(summary?.totalCents)} />
                        <Metric label="Aktualna wartość umowy głównej brutto" value={formatSubcontractAmount(summaryContract.currentValue)} />
                        <Metric label="Pozostało do rozdysponowania" value={formatBudgetCents(summary?.remainingCents)} />
                    </dl>
                    <p className="subcontracts-summary-note">Wartości uwzględniają aneksy. Pozostała kwota dotyczy umów podwykonawczych.</p>
                    {summary?.remainingCents < 0n && <p className="subcontracts-end-warning" role="alert">
                        Suma umów podwykonawczych przekracza aktualną wartość umowy głównej.
                    </p>}
                    {subcontracts.length === 0 ? <p className="subcontracts-muted">Nie dodano jeszcze umów podwykonawczych.</p>
                    : <div className="subcontracts-list">
                        {subcontracts.map((subcontract) => <article key={subcontract.id} className="subcontracts-item">
                            <div className="subcontracts-item-heading">
                                <div>
                                    <span className="subcontracts-number">{subcontract.contractNumber}</span>
                                    <h3>{subcontract.subcontractorName}</h3>
                                    <p className="subcontracts-muted">NIP: {subcontract.subcontractorTaxId}</p>
                                </div>
                                <div className="subcontracts-actions">
                                    <Link to={`/main-contracts/${contractId}/subcontracts/${subcontract.id}/invoices`} className="main-form-button">
                                        Faktury
                                    </Link>
                                    <Link to={`/main-contracts/${contractId}/subcontracts/${subcontract.id}/edit`} className="main-form-button">
                                        Edytuj umowę
                                    </Link>
                                </div>
                            </div>
                            {getSubcontractEndWarning(summaryContract.currentEndDate, subcontract.currentEndDate) &&
                                <p className="subcontracts-end-warning" role="status">
                                    {getSubcontractEndWarning(summaryContract.currentEndDate, subcontract.currentEndDate)}
                                </p>}
                            {subcontract.shortDescription && <p className="subcontracts-description">{subcontract.shortDescription}</p>}
                            <dl className="subcontracts-meta">
                                <Metric label="Rodzaj umowy" value={formatContractType(subcontract)} />
                                <Metric label="Data zawarcia" value={formatAmendmentDate(subcontract.contractDate)} />
                                <Metric label="Wynagrodzenie" value={REMUNERATION_TYPES.find((option) => option.value === subcontract.remunerationType)?.label
                                    ?? subcontract.remunerationType ?? '—'} />
                                <Metric label="VAT" value={subcontract.vatRate != null ? `${subcontract.vatRate}%` : '—'} />
                                <Metric label="Rozpoczęcie" value={formatAmendmentDate(subcontract.currentStartDate)} />
                                <Metric label="Zakończenie" value={formatAmendmentDate(subcontract.currentEndDate)} />
                                <Metric label="Wartość pierwotna brutto" value={formatSubcontractAmount(subcontract.initialValue)} />
                                <Metric label="Aktualna wartość brutto" value={formatSubcontractAmount(subcontract.currentValue)} />
                            </dl>
                            <dl className="subcontracts-financials">
                                <Metric label="Zafakturowano" value={formatSubcontractAmount(subcontract.invoicedAmount)} />
                                <Metric label="Zapłacono" value={formatSubcontractAmount(subcontract.paidAmount)} />
                                <Metric label="Potrącono" value={formatSubcontractAmount(subcontract.deductedAmount)} />
                                <Metric label="Saldo faktur do rozliczenia" value={formatSubcontractAmount(subcontract.outstandingInvoiceAmount)} />
                            </dl>
                            <div className="subcontracts-deductions">
                                <h4>Potrącenia umowne</h4>
                                <dl className="subcontracts-meta">
                                    <Metric label={`Zabezpieczenie · ${formatDeductionRule(subcontract.performanceSecurityDeduction)}`}
                                        value={formatSubcontractAmount(subcontract.performanceSecurityDeductionAmount)} />
                                    <Metric label={`Inne potrącenia · ${formatDeductionRule(subcontract.otherDeduction)}`}
                                        value={formatSubcontractAmount(subcontract.otherDeductionAmount)} />
                                </dl>
                                <p className="subcontracts-muted">Kwoty umowne liczone od aktualnej wartości umowy.</p>
                            </div>
                        </article>)}
                    </div>}
                </>}
        </section>
    );
}

function Metric({ label, value }) {
    return <div><dt>{label}</dt><dd>{value}</dd></div>;
}
