import { useEffect, useId, useRef, useState } from 'react';
import http from './api/http';
import AttachmentNameEditor from './AttachmentNameEditor';
import FileDropZone from './FileDropZone';
import { attachmentTitle, attachmentDownloadName } from './utils/attachmentNames';
import { attachmentEndpoint, attachmentError, attachmentOwner, attachmentSize, buildAttachmentRequest,
    isPdf, loadAttachmentBlob, readAttachments, readAttachmentTypes, saveAttachmentBlob, validateAttachmentForm } from './utils/attachments';
import './AttachmentsPanel.css';

export default function AttachmentsPanel(props) {
    return <Panel key={`${props.ownerType}-${props.ownerId}`} {...props} />;
}

function Panel({ ownerType, ownerId, title = 'Załączniki', onChange, readOnly = false }) {
    const [items, setItems] = useState([]), [types, setTypes] = useState([]), [loading, setLoading] = useState(true);
    const [error, setError] = useState(''), [actionError, setActionError] = useState(''), [success, setSuccess] = useState('');
    const [attempt, setAttempt] = useState(0), [busy, setBusy] = useState(false), [reset, setReset] = useState(0);
    const [removing, setRemoving] = useState(null), [preview, setPreview] = useState(null);
    const busyRef = useRef(false), alive = useRef(false);
    useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
    useEffect(() => {
        let active = true; const controller = new AbortController();
        setLoading(true); setError('');
        Promise.all([http.get(attachmentEndpoint(ownerType, ownerId), { signal: controller.signal }),
            http.get('/attachments/types', { params: { owner: attachmentOwner(ownerType) }, signal: controller.signal })])
            .then(([files, options]) => {
                if (!active) return;
                const documents = readAttachments(files.data), choices = readAttachmentTypes(options.data);
                setItems(documents); setTypes(choices);
            }).catch(async (failure) => { const message = await attachmentError(failure); if (active) setError(message); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; controller.abort(); };
    }, [ownerType, ownerId, attempt]);
    useEffect(() => () => { if (preview) URL.revokeObjectURL(preview.url); }, [preview]);

    async function changed() {
        try { await onChange?.(); }
        catch { if (alive.current) setActionError('Dokument został zmieniony. Nie udało się odświeżyć podsumowania faktury — odśwież widok.'); }
    }
    async function mutate(request, message) {
        if (busyRef.current || loading || error) return;
        busyRef.current = true; setBusy(true); setActionError(''); setSuccess('');
        try {
            if (request.method === 'delete') await http.delete(request.url);
            else await http[request.method ?? 'post'](request.url, request.data, request.config);
            if (!alive.current) { await changed(); return; }
            setSuccess(message); setRemoving(null); setReset((value) => value + 1);
            if (request.method === 'delete') setPreview(null);
            try {
                const response = await http.get(attachmentEndpoint(ownerType, ownerId));
                if (alive.current) setItems(readAttachments(response.data));
            } catch { if (alive.current) setError('Zmiana została zapisana. Nie udało się odświeżyć listy dokumentów.'); }
            await changed();
        } catch (failure) { const message = await attachmentError(failure); if (alive.current) setActionError(message); }
        finally { busyRef.current = false; if (alive.current) setBusy(false); }
    }
    async function open(item, display) {
        if (busyRef.current) return;
        busyRef.current = true; setBusy(true); setActionError('');
        try {
            const blob = await loadAttachmentBlob(http, item);
            if (!alive.current) return;
            if (display) setPreview({ id: item.id, name: attachmentTitle(item),
                url: URL.createObjectURL(new Blob([blob], { type: 'application/pdf' })) });
            else saveAttachmentBlob(blob, attachmentDownloadName(item));
        } catch (failure) { if (alive.current) setActionError(failure.message); }
        finally { busyRef.current = false; if (alive.current) setBusy(false); }
    }
    return <section className="attachments-panel" aria-label={title}>
        <div className="attachments-heading"><h3>{title}</h3><button type="button" className="main-form-button" disabled={busy || loading}
            onClick={() => setAttempt((value) => value + 1)}>Odśwież dokumenty</button></div>
        {success && <p className="attachments-success" role="status">{success}</p>}
        {actionError && <p className="error-message" role="alert">{actionError}</p>}
        {loading ? <p role="status">Ładowanie dokumentów...</p> : error ? <div className="error-message" role="alert">{error}
            <button type="button" className="retry-button" disabled={busy} onClick={() => setAttempt((value) => value + 1)}>Spróbuj ponownie</button></div> : <>
            {!items.length && <p className="attachments-muted">Nie dodano jeszcze plików.</p>}
            <ul className="attachments-list">{items.map((item) => <li key={`${item.id}-${item.metadataVersion ?? 0}`}>
                <div><strong className="attachments-filename">{attachmentTitle(item)}</strong>
                    {attachmentTitle(item) !== item.originalFilename && <p className="attachments-muted">Plik źródłowy: {item.originalFilename}</p>}
                    <p className="attachments-muted">{item.typeLabel || types.find((type) => type.value === item.type)?.label || item.type}
                        {item.size != null && ` · ${attachmentSize(item.size)}`}</p>
                    {item.description && <p className="attachments-description">{item.description}</p>}</div>
                <div className="attachments-actions">
                    {isPdf(item) && <button type="button" className="main-form-button" disabled={busy} onClick={() => open(item, true)} aria-label={`Podgląd ${item.originalFilename}`}>Podgląd PDF</button>}
                    <button type="button" className="main-form-button" disabled={busy} onClick={() => open(item, false)} aria-label={`Pobierz ${item.originalFilename}`}>Pobierz</button>
                    {!readOnly && <AttachmentNameEditor item={item} disabled={busy} onSave={mutate} />}
                    {!readOnly && <button type="button" className="main-form-button" disabled={busy} onClick={() => setRemoving(item)} aria-label={`Usuń ${item.originalFilename}`}>Usuń</button>}
                </div>
            </li>)}</ul>
            {removing && <div className="attachments-confirm" role="group" aria-label="Potwierdzenie usunięcia">
                <p>Usunąć załącznik „{attachmentTitle(removing)}”? Plik zostanie usunięty z systemu.</p>
                <div className="attachments-actions"><button type="button" className="main-form-button" disabled={busy}
                    onClick={() => mutate({ method: 'delete', url: `/attachments/${removing.id}` }, 'Usunięto załącznik.')}>Usuń załącznik</button>
                    <button type="button" className="main-form-button" disabled={busy} onClick={() => setRemoving(null)}>Anuluj</button></div>
            </div>}
            {!readOnly && <UploadForm key={reset} ownerType={ownerType} ownerId={ownerId} types={types} disabled={busy} onSave={mutate} />}
        </>}
        {busy && <p role="status">Trwa operacja na dokumencie...</p>}
        {preview && <PdfPreview preview={preview} onClose={() => setPreview(null)} />}
    </section>;
}

function UploadForm({ ownerType, ownerId, types, disabled, onSave }) {
    const prefix = useId(), formRef = useRef(null);
    const [form, setForm] = useState({ file: null, type: types[0]?.value ?? '', description: '', mode: 'all', pages: '', parts: ['', ''] });
    const [errors, setErrors] = useState({});
    const pdf = isPdf(form.file);
    function change(name, value) { setForm((current) => ({ ...current, [name]: value })); setErrors({}); }
    function save(event) {
        event.preventDefault();
        if (disabled) return;
        const validation = validateAttachmentForm(form, types); setErrors(validation);
        if (Object.keys(validation).length) {
            formRef.current?.elements.namedItem(Object.keys(validation)[0])?.focus(); return;
        }
        const count = form.mode === 'split' ? form.parts.length : 1;
        onSave(buildAttachmentRequest(ownerType, ownerId, form, types), count === 1 ? 'Dodano załącznik.' : `Dodano załączniki: ${count}.`);
    }
    return <form className="attachments-upload" ref={formRef} onSubmit={save} noValidate>
        <fieldset disabled={disabled}><legend>Dodaj dokument</legend>
            <div className="attachments-form-grid"><div>
                <FileDropZone id={`${prefix}-file`} name="file" file={form.file} required disabled={disabled}
                    invalid={Boolean(errors.file)} describedBy={errors.file ? `${prefix}-file-error` : undefined}
                    onChange={(file) => { setForm((current) => ({ ...current, file, mode: 'all', pages: '', parts: ['', ''] })); setErrors({}); }} />
                {errors.file && <p id={`${prefix}-file-error`} className="main-form-field-error" role="alert">{errors.file}</p>}</div>
                <div><label htmlFor={`${prefix}-type`}>Rodzaj dokumentu *</label>
                    <select id={`${prefix}-type`} name="type" value={form.type} onChange={(event) => change('type', event.target.value)} required
                        aria-invalid={Boolean(errors.type)}>{types.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}</select>
                    {errors.type && <p className="main-form-field-error" role="alert">{errors.type}</p>}</div></div>
            <label htmlFor={`${prefix}-description`}>Opis</label>
            <textarea id={`${prefix}-description`} name="description" value={form.description} maxLength={1000} rows={2}
                onChange={(event) => change('description', event.target.value)} />
            {errors.description && <p className="main-form-field-error" role="alert">{errors.description}</p>}
            {pdf && <><label htmlFor={`${prefix}-mode`}>Zapisanie PDF</label>
                <select id={`${prefix}-mode`} value={form.mode} onChange={(event) => change('mode', event.target.value)}>
                    <option value="all">Cały plik</option><option value="pages">Wybrane strony</option><option value="split">Podziel na kilka załączników</option></select>
                {form.mode === 'pages' && <><label htmlFor={`${prefix}-pages`}>Strony do zapisania</label>
                    <input id={`${prefix}-pages`} name="pages" value={form.pages} placeholder="np. 1-3,7,10-12" onChange={(event) => change('pages', event.target.value)} />
                    <p className="attachments-muted">Puste pole zapisuje cały plik. Numery stron odnoszą się do źródłowego PDF.</p></>}
                {form.mode === 'split' && <div className="attachments-parts"><p>Każda część zostanie osobnym załącznikiem wybranego rodzaju. Numery stron odnoszą się do źródłowego PDF.</p>
                    {form.parts.map((part, index) => <div key={index} className="attachments-part"><label htmlFor={`${prefix}-part-${index}`}>Część {index + 1} — strony *</label>
                        <input id={`${prefix}-part-${index}`} name={index === 0 ? 'parts' : `part-${index}`} value={part} placeholder="np. 1-3,7"
                            onChange={(event) => change('parts', form.parts.map((value, position) => position === index ? event.target.value : value))} />
                        {form.parts.length > 2 && <button type="button" className="main-form-button" onClick={() => change('parts', form.parts.filter((_, position) => position !== index))}>Usuń część {index + 1}</button>}</div>)}
                    <button type="button" className="main-form-button" disabled={form.parts.length >= 20} onClick={() => change('parts', [...form.parts, ''])}>Dodaj część</button></div>}
            </>}
            {(errors.pages || errors.parts) && <p className="main-form-field-error" role="alert">{errors.pages || errors.parts}</p>}
            <button className="main-form-button main-form-button-primary">{form.mode === 'split' ? 'Zapisz części PDF' : 'Dodaj załącznik'}</button>
        </fieldset>
    </form>;
}

function PdfPreview({ preview, onClose }) {
    const ref = useRef(null), title = useId();
    useEffect(() => { const dialog = ref.current; dialog.showModal(); return () => { if (dialog.open) dialog.close(); }; }, []);
    return <dialog className="attachments-preview" ref={ref} aria-labelledby={title} onCancel={(event) => { event.preventDefault(); onClose(); }}>
        <div className="attachments-heading"><h3 id={title}>{preview.name}</h3><button type="button" className="main-form-button" onClick={onClose}>Zamknij podgląd</button></div>
        <iframe src={preview.url} title={`Podgląd PDF: ${preview.name}`} />
    </dialog>;
}
