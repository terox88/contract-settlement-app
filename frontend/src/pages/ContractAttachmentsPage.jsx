import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import http from '../api/http';
import AttachmentNameEditor from '../AttachmentNameEditor';
import { attachmentDownloadName, attachmentTitle, filterContractAttachments, readContractAttachments } from '../utils/attachmentNames';
import { attachmentError, attachmentSize, loadAttachmentBlob, readAttachments, saveAttachmentBlob } from '../utils/attachments';
import '../AttachmentsPanel.css';
import '../ReviewPanels.css';

export default function ContractAttachmentsPage() {
    const { contractId } = useParams();
    return <Workspace key={contractId} contractId={contractId} />;
}
function Workspace({ contractId }) {
    const [contract, setContract] = useState(null), [rows, setRows] = useState([]), [loading, setLoading] = useState(true);
    const [error, setError] = useState(''), [actionError, setActionError] = useState(''), [success, setSuccess] = useState('');
    const [attempt, setAttempt] = useState(0), [type, setType] = useState(''), [query, setQuery] = useState(''), [busy, setBusy] = useState(false);
    const lock = useRef(false), alive = useRef(false);
    const endpoint = `/main-contracts/${contractId}/attachment-workspace`;
    useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
    useEffect(() => {
        let active = true; const controller = new AbortController(); setLoading(true); setError('');
        Promise.all([http.get(`/main-contracts/${contractId}`, { signal: controller.signal }), http.get(endpoint, { signal: controller.signal })])
            .then(([parent, response]) => {
                if (String(parent.data?.id) !== String(contractId)) throw new Error('Nie można odczytać umowy.');
                const values = readContractAttachments(response.data); readAttachments(values.map(row => row.attachment));
                if (active) { setContract(parent.data); setRows(values); }
            }).catch(failure => { if (active) setError(failure.message); }).finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [contractId, attempt]);
    async function action(operation) {
        if (lock.current || loading || error) return;
        lock.current = true; setBusy(true); setActionError(''); setSuccess('');
        try { await operation(); }
        catch (failure) { const message = await attachmentError(failure); if (alive.current) setActionError(message); }
        finally { lock.current = false; if (alive.current) setBusy(false); }
    }
    const types = [...new Map(rows.map(row => [row.attachment.type, row.attachment.typeLabel || row.attachment.type])).entries()];
    const visible = filterContractAttachments(rows, type, query), categoryCount = rows.filter(row => !type || row.attachment.type === type).length;
    return <>
        <Link className="back-link" to={`/main-contracts/${contractId}`}>← Szczegóły umowy głównej</Link>
        <section className="card attachments-panel"><h1>Dokumenty umowy {contract?.contractNumber ?? ''}</h1>
            <p>Umowa główna i podwykonawcze wszystkich szczebli, aneksy, faktury, oświadczenia oraz potwierdzenia powiązanych przelewów. Każdy załącznik jest pokazany jeden raz.</p>
            {success && <p className="attachments-success" role="status">{success}</p>}{actionError && <p className="error-message" role="alert">{actionError}</p>}
            {loading ? <p role="status">Ładowanie dokumentów...</p> : error ? <p className="error-message" role="alert">{error}</p> : <>
                <div className="contract-document-filters"><label>Kategoria<select value={type} disabled={busy} onChange={event => setType(event.target.value)}><option value="">Wszystkie kategorie</option>
                    {types.map(([value,label]) => <option value={value} key={value}>{label}</option>)}</select></label>
                    <label>Nazwa, opis lub powiązany rekord<input value={query} onChange={event => setQuery(event.target.value)} /></label>
                    <button type="button" className="main-form-button" disabled={busy || !categoryCount} onClick={() => action(async () => {
                        const { data } = await http.get(`${endpoint}/zip`, { params: type ? { type } : {}, responseType: 'blob' });
                        if (!(data instanceof Blob)) throw new Error('Nie można odczytać archiwum.');
                        if (alive.current) saveAttachmentBlob(data, `umowa-${contractId}-${type || 'wszystkie'}.zip`);
                    })}>Pobierz ZIP: {type ? types.find(([value]) => value === type)?.[1] : 'wszystkie dokumenty'} ({categoryCount})</button>
                </div>
                <p className="attachments-muted">Widoczne: {visible.length} z {rows.length}. ZIP obejmuje całą wybraną kategorię; wyszukiwanie tekstowe filtruje tylko listę.</p>
                {!visible.length && <p>Brak dokumentów spełniających wybrane kryteria.</p>}
                <ul className="attachments-list">{visible.map(({ attachment: item, ownerLabel }) => <li key={`${item.id}-${item.metadataVersion}`}>
                    <div><strong className="attachments-filename">{attachmentTitle(item)}</strong>
                        <p>{item.typeLabel || item.type} · {ownerLabel}</p><p className="attachments-muted">Plik źródłowy: {item.originalFilename} · {attachmentSize(item.size)}</p>
                        {item.description && <p>{item.description}</p>}</div>
                    <div className="attachments-actions"><button type="button" className="main-form-button" disabled={busy} onClick={() => action(async () => {
                        const blob = await loadAttachmentBlob(http, item); if (alive.current) saveAttachmentBlob(blob, attachmentDownloadName(item));
                    })}>Pobierz</button>
                        <AttachmentNameEditor item={item} disabled={busy} onSave={(request, message) => action(async () => {
                            await http.put(request.url, request.data);
                            if (alive.current) { setSuccess(message); setAttempt(value => value + 1); }
                        })} /></div>
                </li>)}</ul>
            </>}
            <button type="button" className="main-form-button" disabled={busy || loading} onClick={() => setAttempt(value => value + 1)}>Odśwież dokumenty</button>
            <p className="attachments-muted">Pliki dodajesz i usuwasz w panelu dokumentów odpowiedniej umowy, aneksu, faktury, oświadczenia lub przelewu.</p>
        </section>
    </>;
}
