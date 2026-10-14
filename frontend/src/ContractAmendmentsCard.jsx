import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import http from './api/http';
import { formatAmendmentChange, formatAmendmentDate, readAmendmentList } from './utils/contractAmendmentForm';
import './ContractAmendmentsCard.css';

export default function ContractAmendmentsCard({ contract }) {
    const contractId = contract.id;
    const [amendments, setAmendments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [loadAttempt, setLoadAttempt] = useState(0);

    useEffect(() => {
        let active = true;
        const controller = new AbortController();
        setLoading(true);
        setError('');
        http.get(`/contract-amendments/contract/${contractId}`, { signal: controller.signal })
            .then((response) => { if (active) setAmendments(readAmendmentList(response.data, contractId)); })
            .catch((failure) => { if (active) setError(failure.message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [contractId, loadAttempt]);

    return (
        <section className="card amendments-card" aria-label="Aneksy do umowy">
            <div className="amendments-heading">
                <div>
                    <h2>Aneksy</h2>
                    <p>Zmiany umowy w kolejności dat obowiązywania.</p>
                </div>
                <Link to={`/main-contracts/${contractId}/amendments/new`} className="main-form-button">
                    Dodaj aneks
                </Link>
            </div>

            {loading ? <p className="amendments-muted" role="status">Ładowanie aneksów...</p>
                : error ? <div className="error-message" role="alert">
                    {error}
                    <button type="button" className="retry-button"
                        onClick={() => setLoadAttempt((value) => value + 1)}>Spróbuj ponownie</button>
                </div>
                    : amendments.length === 0 ? <p className="amendments-muted">Do tej umowy nie dodano jeszcze aneksów.</p>
                        : <div className="amendments-list">
                            {amendments.map((amendment) => (
                                <article className="amendments-item" key={amendment.id}>
                                    <div className="amendments-item-heading">
                                        <h3>{amendment.amendmentNumber}</h3>
                                        <Link to={`/main-contracts/${contractId}/amendments/${amendment.id}/edit`}
                                            className="main-form-button" aria-label={`Edytuj aneks ${amendment.amendmentNumber}`}>
                                            Edytuj
                                        </Link>
                                    </div>
                                    <dl className="amendments-meta">
                                        <div><dt>Data aneksu</dt><dd>{formatAmendmentDate(amendment.amendmentDate)}</dd></div>
                                        <div><dt>Obowiązuje od</dt><dd>{formatAmendmentDate(amendment.effectiveDate)}</dd></div>
                                        <div><dt>Zmiana wartości brutto</dt><dd>{formatAmendmentChange(amendment.valueChange)}</dd></div>
                                        <div><dt>Nowy termin zakończenia</dt><dd>{amendment.newEndDate
                                            ? formatAmendmentDate(amendment.newEndDate) : 'Bez zmiany terminu'}</dd></div>
                                    </dl>
                                    {amendment.description && <p className="amendments-description">{amendment.description}</p>}
                                </article>
                            ))}
                        </div>}
        </section>
    );
}
