import {
    useEffect,
    useState,
} from 'react';

import {
    Link,
    useParams,
} from 'react-router-dom';

import http from '../api/http';

import FinancialLimitsCard from '../FinancialLimitsCard';
import ContractAmendmentsCard from '../ContractAmendmentsCard';
import SubcontractsCard from '../SubcontractsCard';
import { formatContractType } from '../utils/contractTypes';
import './MainContractFormPage.css';

const currencyFormatter =
    new Intl.NumberFormat(
        'pl-PL',
        {
            style: 'currency',
            currency: 'PLN',
            maximumFractionDigits: 2,
        }
    );

const dateFormatter =
    new Intl.DateTimeFormat(
        'pl-PL'
    );

const REMUNERATION_TYPE_LABELS = {
    LUMP_SUM: 'Ryczałt',
    UNIT_PRICE: 'Kosztorysowe',
};

export default function MainContractDetailsPage() {
    const { contractId } =
        useParams();

    const [contract, setContract] =
        useState(null);

    const [loading, setLoading] =
        useState(true);

    const [error, setError] =
        useState(null);

    useEffect(() => {
        loadContract();
    }, [contractId]);

    async function loadContract() {
        try {
            setLoading(true);
            setError(null);

            const response =
                await http.get(
                    `/main-contracts/${contractId}`
                );

            setContract(
                response.data
            );

        } catch (error) {

            setError(
                error.message
            );

        } finally {

            setLoading(false);
        }
    }

    if (loading) {
        return (
            <div className="loading-message">
                Ładowanie umowy...
            </div>
        );
    }

    if (error) {
        return (
            <div className="error-message">

                {error}

                <button
                    type="button"
                    className="retry-button"
                    onClick={loadContract}
                >
                    Spróbuj ponownie
                </button>

            </div>
        );
    }

    if (!contract) {
        return null;
    }

    const paymentExceeded =
        Number(
            contract.remainingToPay ?? 0
        ) < 0;

    const invoiceExceeded =
        Number(
            contract.remainingToInvoice ?? 0
        ) < 0;

    return (
        <>
            <Link
                to={
                    `/tasks/${contract.investmentTaskId}`
                }
                className="back-link"
            >
                ← Wróć do zadania
            </Link>

            <section className="card contract-details-header">

                <div className="contract-details-top">

                    <div>

            <span className="contract-number">
              {contract.contractNumber}
            </span>

                        <h1>
                            {contract.mainContractorName}
                        </h1>

                        {contract.shortDescription && (
                            <p className="contract-details-description">
                                {contract.shortDescription}
                            </p>
                        )}

                    </div>

                    <div className="main-form-contract-actions">
                        <span className="contract-type">
            {formatContractType(
                contract
            )}
                        </span>
                        <Link
                            to={`/main-contracts/${contract.id}/edit`}
                            className="main-form-button"
                        >
                            Edytuj umowę
                        </Link>
                    </div>

                </div>

                <div className="contract-details-basic">

                    <DetailItem
                        label="Numer zadania"
                        value={
                            contract.investmentTaskNumber
                        }
                    />

                    <DetailItem
                        label="Data umowy"
                        value={
                            formatDate(
                                contract.contractDate
                            )
                        }
                    />

                    <DetailItem
                        label="Rodzaj wynagrodzenia"
                        value={
                            formatRemunerationType(
                                contract.remunerationType
                            )
                        }
                    />

                    <DetailItem
                        label="VAT"
                        value={
                            contract.vatRate != null
                                ? `${contract.vatRate}%`
                                : '—'
                        }
                    />

                </div>

            </section>

            <div className="section-heading">

                <div>
                    <h2>
                        Podsumowanie finansowe
                    </h2>

                    <p>
                        Aktualny stan wartości umowy,
                        faktur i płatności.
                    </p>
                </div>

            </div>

            <section className="card">

                <div className="contract-summary-grid">

                    <FinancialSummaryItem
                        label="Wartość umowy"
                        value={
                            contract.currentValue
                        }
                    />

                    <FinancialSummaryItem
                        label="Zafakturowano"
                        value={
                            contract.invoicedAmount
                        }
                    />

                    <FinancialSummaryItem
                        label="Zapłacono"
                        value={
                            contract.paidAmount
                        }
                    />

                    <FinancialSummaryItem
                        label="Pozostało do zapłaty"
                        value={
                            contract.remainingToPay
                        }
                        warning={
                            paymentExceeded
                        }
                    />

                </div>

                <div className="contract-secondary-financials">

                    <DetailItem
                        label="Wartość pierwotna"
                        value={
                            formatCurrency(
                                contract.initialValue
                            )
                        }
                    />

                    <DetailItem
                        label="Pozostało do zafakturowania"
                        value={
                            formatCurrency(
                                contract.remainingToInvoice
                            )
                        }
                        warning={
                            invoiceExceeded
                        }
                    />

                    <DetailItem
                        label="Kary umowne"
                        value={
                            formatCurrency(
                                contract.totalPenaltyAmount
                            )
                        }
                    />

                </div>

            </section>

            <div className="section-heading">

                <div>
                    <h2>
                        Terminy
                    </h2>
                </div>

            </div>

            <section className="card">

                <div className="contract-dates-grid">

                    <DetailItem
                        label="Rozpoczęcie"
                        value={
                            formatDate(
                                contract.currentStartDate
                            )
                        }
                    />

                    <DetailItem
                        label="Zakończenie"
                        value={
                            formatDate(
                                contract.currentEndDate
                            )
                        }
                    />

                    <DetailItem
                        label="Pierwotny termin zakończenia"
                        value={
                            formatDate(
                                contract.initialEndDate
                            )
                        }
                    />

                </div>

            </section>

            <ContractAmendmentsCard key={contract.id} contract={contract} />

            <SubcontractsCard key={contract.id} contract={contract} />

            <div className="contract-sections-grid">

                <FinancialLimitsCard contract={contract} />

                <SectionCard
                    title="Kary umowne"
                    description={
                        'Nałożone kary i ich łączna wartość.'
                    }
                />

                <SectionCard
                    title="Faktury GW"
                    description={
                        'Faktury generalnego wykonawcy i ich rozliczenie.'
                    }
                />

                <SectionCard
                    title="Załączniki"
                    description={
                        'Umowa, aneksy, protokoły i pozostałe dokumenty.'
                    }
                />

            </div>
        </>
    );
}

function FinancialSummaryItem({
                                  label,
                                  value,
                                  warning = false,
                              }) {
    return (
        <div className="financial-summary-item">

      <span className="label">
        {label}
      </span>

            <strong
                className={
                    warning
                        ? 'financial-summary-value value-warning'
                        : 'financial-summary-value'
                }
            >
                {formatCurrency(value)}
            </strong>

        </div>
    );
}

function DetailItem({
                        label,
                        value,
                        warning = false,
                    }) {
    return (
        <div className="detail-item">

      <span className="label">
        {label}
      </span>

            <strong
                className={
                    warning
                        ? 'value-warning'
                        : ''
                }
            >
                {value ?? '—'}
            </strong>

        </div>
    );
}

function SectionCard({
                         title,
                         description,
                     }) {
    return (
        <section className="card contract-section-card">

            <h3>
                {title}
            </h3>

            <p>
                {description}
            </p>

            <span className="section-coming-soon">
        Rozwiniemy w kolejnym kroku
      </span>

        </section>
    );
}

function formatCurrency(value) {
    return currencyFormatter.format(
        Number(value ?? 0)
    );
}

function formatDate(value) {
    if (!value) {
        return '—';
    }

    return dateFormatter.format(
        new Date(
            `${value}T00:00:00`
        )
    );
}

function formatRemunerationType(type) {
    return (
        REMUNERATION_TYPE_LABELS[type]
        ?? type
        ?? '—'
    );
}
