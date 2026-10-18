import { getInvoiceDocumentItems } from './utils/subcontractorInvoiceForm';
import './InvoiceDocuments.css';
import { DeductionReviewSummary } from './DeductionReviewPanel';

export default function InvoiceDocuments({ invoice, hideDeductions = false }) {
    return <section className="invoice-documents" aria-label="Załączniki powiązane z fakturą">
        <h3>Dokumenty</h3>
        <dl className="invoice-documents-grid">
            {getInvoiceDocumentItems(invoice).map((item) => <div key={item.title} className={`invoice-document invoice-document-${item.tone}`}>
                <dt>{item.title}</dt>
                <dd><strong>{item.label}</strong><span>{item.detail}</span></dd>
            </div>)}
        </dl>
        {!hideDeductions && invoice.deductionReview && <DeductionReviewSummary review={invoice.deductionReview} currency={invoice.currency} />}
    </section>;
}
