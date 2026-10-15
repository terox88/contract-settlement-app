package pl.company.settlements.invoice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.invoice.repository.InvoiceDocumentRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceDocumentServiceTest {
    @Mock private InvoiceDocumentRepository repository;
    private InvoiceDocumentService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceDocumentService(repository);
    }

    @Test
    void emptyListDoesNotIssueQueries() {
        assertTrue(service.read(List.of()).isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void distinguishesInvoiceAndStatementFilesAndIgnoresOtherTypesAndInvoices() {
        var ids = List.of(1L, 2L);
        when(repository.findDirectDocuments(ids)).thenReturn(List.of(
                new Document(1L, 10L, AttachmentType.SUBCONTRACTOR_INVOICE),
                new Document(1L, 11L, AttachmentType.SUBCONTRACTOR_STATEMENT),
                new Document(2L, 12L, AttachmentType.ACCEPTANCE_PROTOCOL),
                new Document(99L, 99L, AttachmentType.SUBCONTRACTOR_INVOICE)));
        when(repository.findStatementDocuments(ids, AttachmentType.SUBCONTRACTOR_STATEMENT)).thenReturn(List.of(
                new Document(1L, 11L, AttachmentType.SUBCONTRACTOR_STATEMENT)));

        var result = service.read(ids);

        assertEquals(1, result.get(1L).summary().invoiceFileCount());
        assertEquals(1, result.get(1L).summary().statementFileCount());
        assertEquals(0, result.get(2L).summary().invoiceFileCount());
        assertFalse(result.containsKey(99L));
    }

    @Test
    void countsOnlyTransfersWithPositiveAllocationAndKeepsPartialCoverage() {
        var ids = List.of(1L);
        when(repository.findPayments(ids)).thenReturn(List.of(
                new Payment(1L, 20L, BigDecimal.ONE), new Payment(1L, 21L, BigDecimal.ONE),
                new Payment(1L, 22L, BigDecimal.ZERO)));
        when(repository.findPaymentDocuments(ids, AttachmentType.PAYMENT_CONFIRMATION)).thenReturn(List.of(
                new PaymentDocument(1L, 20L, 30L), new PaymentDocument(1L, 20L, 31L),
                new PaymentDocument(1L, 22L, 32L)));

        var state = service.read(ids).get(1L);

        assertTrue(state.hasPaymentAllocations());
        assertEquals(2, state.summary().paymentCount());
        assertEquals(1, state.summary().paymentsWithConfirmationCount());
        assertEquals(2, state.summary().paymentConfirmationFileCount());
    }

    @Test
    void sharedPaymentIsConfirmedForBothInvoices() {
        var ids = List.of(1L, 2L);
        when(repository.findPayments(ids)).thenReturn(List.of(
                new Payment(1L, 20L, BigDecimal.ONE), new Payment(2L, 20L, BigDecimal.ONE)));
        when(repository.findPaymentDocuments(ids, AttachmentType.PAYMENT_CONFIRMATION)).thenReturn(List.of(
                new PaymentDocument(1L, 20L, 30L), new PaymentDocument(2L, 20L, 30L)));
        var result = service.read(ids);
        assertEquals(1, result.get(1L).summary().paymentsWithConfirmationCount());
        assertEquals(1, result.get(2L).summary().paymentsWithConfirmationCount());
    }

    private record Document(Long invoiceId, Long fileId, AttachmentType attachmentType)
            implements InvoiceDocumentRepository.DocumentRow {
        public Long getInvoiceId() { return invoiceId; }
        public Long getFileId() { return fileId; }
        public AttachmentType getAttachmentType() { return attachmentType; }
    }

    private record Payment(Long invoiceId, Long paymentId, BigDecimal allocatedAmount)
            implements InvoiceDocumentRepository.PaymentRow {
        public Long getInvoiceId() { return invoiceId; }
        public Long getPaymentId() { return paymentId; }
        public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    }

    private record PaymentDocument(Long invoiceId, Long paymentId, Long fileId)
            implements InvoiceDocumentRepository.PaymentDocumentRow {
        public Long getInvoiceId() { return invoiceId; }
        public Long getPaymentId() { return paymentId; }
        public Long getFileId() { return fileId; }
    }
}
