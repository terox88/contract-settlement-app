package pl.company.settlements.invoice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.invoice.repository.InvoiceDocumentRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceDocumentService {
    private final InvoiceDocumentRepository repository;

    /** Cztery odczyty dla całej listy zamiast osobnych odczytów dla każdej faktury. */
    public Map<Long, InvoiceDocumentState> read(Collection<Long> invoiceIds) {
        Map<Long, InvoiceDocumentState> result = new HashMap<>();
        for (Long id : invoiceIds) result.put(id, new InvoiceDocumentState());
        if (invoiceIds.isEmpty()) return result;

        for (var row : repository.findDirectDocuments(invoiceIds)) {
            InvoiceDocumentState state = result.get(row.getInvoiceId());
            if (state == null || row.getAttachmentType() == null) continue;
            switch (row.getAttachmentType()) {
                case SUBCONTRACTOR_INVOICE -> state.addDocument(InvoiceDocumentState.Kind.INVOICE, row.getFileId());
                case SUBCONTRACTOR_STATEMENT -> state.addDocument(InvoiceDocumentState.Kind.STATEMENT, row.getFileId());
                case PAYMENT_CONFIRMATION -> state.addDocument(InvoiceDocumentState.Kind.PAYMENT_CONFIRMATION, row.getFileId());
                default -> { }
            }
        }
        for (var row : repository.findStatementDocuments(invoiceIds, AttachmentType.SUBCONTRACTOR_STATEMENT)) {
            InvoiceDocumentState state = result.get(row.getInvoiceId());
            if (state != null) state.addDocument(InvoiceDocumentState.Kind.STATEMENT, row.getFileId());
        }
        for (var row : repository.findPayments(invoiceIds)) {
            InvoiceDocumentState state = result.get(row.getInvoiceId());
            if (state != null) state.addAllocation(row.getPaymentId(), row.getAllocatedAmount());
        }
        for (var row : repository.findPaymentDocuments(invoiceIds, AttachmentType.PAYMENT_CONFIRMATION)) {
            InvoiceDocumentState state = result.get(row.getInvoiceId());
            if (state != null) state.addPaymentConfirmation(row.getPaymentId(), row.getFileId());
        }
        return result;
    }
}
