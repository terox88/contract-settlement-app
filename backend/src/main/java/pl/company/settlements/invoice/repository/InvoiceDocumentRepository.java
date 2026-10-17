package pl.company.settlements.invoice.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface InvoiceDocumentRepository extends Repository<SubcontractorInvoice, Long> {
    interface DocumentRow {
        Long getInvoiceId();
        Long getFileId();
        AttachmentType getAttachmentType();
    }

    interface PaymentRow {
        Long getInvoiceId();
        Long getPaymentId();
        BigDecimal getAllocatedAmount();
    }

    interface PaymentDocumentRow {
        Long getInvoiceId();
        Long getPaymentId();
        Long getFileId();
    }

    @Query("""
            select a.subcontractorInvoice.id as invoiceId, a.storedFile.id as fileId, a.type as attachmentType
            from DocumentAttachment a
            where a.subcontractorInvoice.id in :invoiceIds and a.storedFile is not null
            """)
    List<DocumentRow> findDirectDocuments(@Param("invoiceIds") Collection<Long> invoiceIds);

    @Query("""
            select a.subcontractorStatement.subcontractorInvoice.id as invoiceId,
                   a.storedFile.id as fileId, a.type as attachmentType
            from DocumentAttachment a
            where a.subcontractorStatement.subcontractorInvoice.id in :invoiceIds
              and a.type = :type and a.storedFile is not null
            """)
    List<DocumentRow> findStatementDocuments(@Param("invoiceIds") Collection<Long> invoiceIds,
                                             @Param("type") AttachmentType type);

    @Query("""
            select pa.subcontractorInvoice.id as invoiceId, pa.payment.id as paymentId,
                   pa.allocatedAmount as allocatedAmount
            from PaymentAllocation pa
            where pa.subcontractorInvoice.id in :invoiceIds
            """)
    List<PaymentRow> findPayments(@Param("invoiceIds") Collection<Long> invoiceIds);

    @Query("""
            select pa.subcontractorInvoice.id as invoiceId, pa.payment.id as paymentId,
                   a.storedFile.id as fileId
            from PaymentAllocation pa, DocumentAttachment a
            where pa.subcontractorInvoice.id in :invoiceIds
              and pa.allocatedAmount > 0 and a.payment = pa.payment
              and a.type = :type and a.storedFile is not null
            """)
    List<PaymentDocumentRow> findPaymentDocuments(@Param("invoiceIds") Collection<Long> invoiceIds,
                                                 @Param("type") AttachmentType type);
}
