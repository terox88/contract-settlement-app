import { useState } from 'react';
import { attachmentTitle, renameAttachmentRequest } from './utils/attachmentNames';
import './ReviewPanels.css';

export default function AttachmentNameEditor({ item, disabled, onSave }) {
    const [open, setOpen] = useState(false), [name, setName] = useState(attachmentTitle(item)), [error, setError] = useState('');
    function save() {
        if (disabled) return;
        try { const request = renameAttachmentRequest(item, name); setError(''); onSave(request, 'Zmieniono nazwę dokumentu.'); }
        catch (failure) { setError(failure.message); }
    }
    return open ? <div className="attachments-rename" role="group" aria-label="Zmiana nazwy dokumentu">
        <input aria-label={`Nazwa wyświetlana: ${item.originalFilename}`} value={name} maxLength={255} disabled={disabled}
            onChange={event => { setName(event.target.value); setError(''); }} onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); save(); } }} />
        <button type="button" className="main-form-button" disabled={disabled} onClick={save}>Zapisz nazwę</button>
        <button type="button" className="main-form-button" disabled={disabled} onClick={() => setOpen(false)}>Anuluj zmianę nazwy</button>
        {error && <p className="error-message" role="alert">{error}</p>}
    </div> : <button type="button" className="main-form-button" disabled={disabled} onClick={() => setOpen(true)}>Zmień nazwę</button>;
}
