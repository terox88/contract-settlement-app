import { lazy, Suspense, useState } from 'react';

const AttachmentsPanel = lazy(() => import('./AttachmentsPanel'));

export default function AttachmentDisclosure({ title = 'Dokumenty — dodaj lub otwórz', children, ...props }) {
    const [open, setOpen] = useState(false);
    return <details className="attachments-disclosure" onToggle={(event) => setOpen(event.currentTarget.open)}>
        <summary>{title}</summary>
        {open && <Suspense fallback={<p role="status">Ładowanie panelu dokumentów…</p>}>
            {children ?? <AttachmentsPanel title={title} {...props} />}
        </Suspense>}
    </details>;
}
