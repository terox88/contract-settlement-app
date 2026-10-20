import { useId, useRef, useState } from 'react';
import { attachmentSize } from './utils/attachments';
import './FileDropZone.css';

export default function FileDropZone({ id, name, label = 'Plik', file, onChange, accept,
    disabled = false, required = false, invalid = false, describedBy }) {
    const generatedId = useId();
    const inputId = id || generatedId;
    const [dragging, setDragging] = useState(false);
    const [error, setError] = useState('');
    const dragDepth = useRef(0);
    const containsFiles = (event) => Array.from(event.dataTransfer?.types ?? []).includes('Files');

    function choose(files) {
        if (disabled) return;
        if (files.length !== 1) {
            setError('Wybierz jeden plik. Kilka plików i foldery nie są obsługiwane.');
            return;
        }
        setError('');
        onChange(files[0]);
    }

    return <div className={`file-drop-zone${dragging && !disabled ? ' file-drop-zone-active' : ''}${disabled ? ' file-drop-zone-disabled' : ''}`}
        onDragEnter={(event) => {
            if (!containsFiles(event)) return;
            event.preventDefault(); event.stopPropagation();
            if (!disabled) { dragDepth.current += 1; setDragging(true); }
        }}
        onDragOver={(event) => {
            if (!containsFiles(event)) return;
            event.preventDefault(); event.stopPropagation();
            event.dataTransfer.dropEffect = disabled ? 'none' : 'copy';
        }}
        onDragLeave={(event) => {
            event.preventDefault(); event.stopPropagation();
            dragDepth.current = Math.max(0, dragDepth.current - 1);
            if (!dragDepth.current) setDragging(false);
        }}
        onDrop={(event) => {
            event.preventDefault(); event.stopPropagation();
            dragDepth.current = 0; setDragging(false);
            if (disabled || !containsFiles(event)) return;
            const items = Array.from(event.dataTransfer.items ?? []);
            if (items.some((item) => item.webkitGetAsEntry?.()?.isDirectory)) {
                setError('Wybierz jeden plik. Foldery nie są obsługiwane.'); return;
            }
            choose(Array.from(event.dataTransfer.files ?? []));
        }}>
        <label className="file-drop-zone-label" htmlFor={inputId}>{label}{required ? ' *' : ''}</label>
        <p className="file-drop-zone-hint" id={`${inputId}-hint`}>Przeciągnij jeden plik tutaj lub wybierz go z dysku.</p>
        <div className="file-drop-zone-browse">
            <span aria-hidden="true">Przeglądaj…</span>
            <input className="file-drop-zone-input" id={inputId} name={name} type="file" accept={accept}
                disabled={disabled} required={required && !file} aria-required={required}
                aria-invalid={invalid || Boolean(error)}
                aria-describedby={[`${inputId}-hint`, `${inputId}-selected`, describedBy, error && `${inputId}-drop-error`].filter(Boolean).join(' ')}
                onChange={(event) => {
                    if (event.target.files?.length) choose(Array.from(event.target.files));
                    event.target.value = '';
                }} />
        </div>
        <p className="file-drop-zone-selected" id={`${inputId}-selected`} role="status">
            {file ? <><strong>{file.name}</strong> · {attachmentSize(file.size)}</> : 'Nie wybrano pliku.'}
        </p>
        {file && <button type="button" className="main-form-button" disabled={disabled} onClick={() => {
            if (!disabled) { setError(''); onChange(null); }
        }}>Usuń wybór pliku</button>}
        {error && <p className="main-form-field-error" id={`${inputId}-drop-error`} role="alert">{error}</p>}
    </div>;
}
