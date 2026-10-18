import { useState } from 'react';
import AttachmentsPanel from './AttachmentsPanel';

export default function AttachmentDisclosure({ title = 'Dokumenty — dodaj lub otwórz', children, ...props }) {
    const [open, setOpen] = useState(false);
    return <details className="attachments-disclosure" onToggle={(event) => setOpen(event.currentTarget.open)}>
        <summary>{title}</summary>
        {open && (children ?? <AttachmentsPanel title={title} {...props} />)}
    </details>;
}
