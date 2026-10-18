package pl.company.settlements.file.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import pl.company.settlements.contract.repository.ContractAmendmentRepository;
import pl.company.settlements.contract.repository.ContractRepository;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.file.domain.DocumentAttachment;
import pl.company.settlements.file.domain.StoredFile;
import pl.company.settlements.file.repository.DocumentAttachmentRepository;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.invoice.repository.SubcontractorStatementRepository;
import pl.company.settlements.payment.domain.Payment;
import pl.company.settlements.payment.repository.PaymentRepository;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentAttachmentServiceTest {
    @Mock private DocumentAttachmentRepository repository;
    @Mock private FileStorageService fileStorageService;
    @Mock private ContractRepository contractRepository;
    @Mock private ContractAmendmentRepository contractAmendmentRepository;
    @Mock private MainContractorInvoiceRepository mainContractorInvoiceRepository;
    @Mock private SubcontractorInvoiceRepository subcontractorInvoiceRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SubcontractorStatementRepository subcontractorStatementRepository;
    @Mock private PdfPageSelectionService pdfPageSelectionService;
    @Mock private PdfSplitService pdfSplitService;
    @InjectMocks private DocumentAttachmentService service;

    @Test
    void transferProofCannotBeUploadedDirectlyToInvoice() {
        var file = new MockMultipartFile("file", "bank.pdf", "application/pdf", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> service.uploadToSubcontractorInvoice(40L, file, AttachmentType.PAYMENT_CONFIRMATION, null, null));
        verifyNoInteractions(repository, fileStorageService, subcontractorInvoiceRepository);
    }

    @Test
    void invoiceFileCannotBeAttachedToPaymentEvenThroughSplitEndpoint() {
        var file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> service.splitToPayment(8L, file, AttachmentType.SUBCONTRACTOR_INVOICE, null, "1;2"));
        verifyNoInteractions(repository, fileStorageService, paymentRepository, pdfSplitService);
    }

    @Test
    void paymentProofUsesStoredFileFieldAndIsOwnedByPayment() {
        var file = new MockMultipartFile("file", "bank.pdf", "application/pdf", new byte[]{1, 2, 3});
        var payment = new Payment(LocalDate.of(2026, 10, 17), new BigDecimal("100")); payment.setId(8L);
        var stored = new StoredFile("bank.pdf", "key", "application/pdf", 3L, LocalDateTime.now()); stored.setId(12L);
        when(paymentRepository.findById(8L)).thenReturn(Optional.of(payment));
        when(fileStorageService.save(any(InputStream.class), eq("bank.pdf"), eq("application/pdf"))).thenReturn(stored);
        when(repository.saveAndFlush(any(DocumentAttachment.class))).thenAnswer(call -> {
            DocumentAttachment attachment = call.getArgument(0); attachment.setId(9L); return attachment;
        });

        var result = service.uploadToPayment(8L, file, AttachmentType.PAYMENT_CONFIRMATION, " Potwierdzenie ", " ");

        assertEquals(9L, result.getId()); assertEquals(12L, result.getStoredFileId());
        assertEquals("Potwierdzenie przelewu", result.getTypeLabel());
        var captor = ArgumentCaptor.forClass(DocumentAttachment.class); verify(repository).saveAndFlush(captor.capture());
        assertSame(stored, captor.getValue().getStoredFile()); assertSame(payment, captor.getValue().getPayment());
        assertEquals("Potwierdzenie", captor.getValue().getDescription());
        verifyNoInteractions(pdfPageSelectionService, pdfSplitService);
    }
}
