package pl.company.settlements.invoice.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.file.service.DocumentAttachmentService;
import pl.company.settlements.invoice.model.MainContractorInvoiceCreateRequest;
import pl.company.settlements.invoice.model.MainContractorInvoiceResponse;
import pl.company.settlements.invoice.model.MainInvoiceSettlementResponse;
import pl.company.settlements.invoice.model.MainInvoiceWithSubcontractorInvoiceRequest;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KsefInvoiceImportServiceTest {
    private final MainContractorInvoiceService main = mock(MainContractorInvoiceService.class);
    private final SubcontractorInvoiceService sub = mock(SubcontractorInvoiceService.class);
    private final MainInvoiceSettlementService settlements = mock(MainInvoiceSettlementService.class);
    private final DocumentAttachmentService attachments = mock(DocumentAttachmentService.class);
    private final KsefInvoiceImportService service = new KsefInvoiceImportService(main, sub, settlements, attachments);

    private MockMultipartFile xml() {
        return new MockMultipartFile("file", "faktura.xml", "application/xml", KsefXmlParserCases.xml().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void previewDoesNotCreateInvoicePaymentOrFile() {
        assertEquals("FV/10/2026", service.preview(xml()).invoiceNumber());
        verifyNoInteractions(main, sub, settlements, attachments);
    }

    @Test
    void invalidFileDoesNotReachInvoiceOrFileStorage() {
        var file = new MockMultipartFile("file", "scan.pdf", "application/pdf", "%PDF".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> service.createMain(new MainContractorInvoiceCreateRequest(), file));
        verifyNoInteractions(main, sub, settlements, attachments);
    }

    @Test
    void reviewedDataUsesOrdinaryInvoiceValidationAndAttachesOriginalXml() {
        var file = xml();
        var reviewed = new MainContractorInvoiceCreateRequest();
        reviewed.setInvoiceNumber("Ręcznie poprawiony numer");
        var response = MainContractorInvoiceResponse.builder().id(42L).build();
        when(main.create(reviewed)).thenReturn(response);
        assertSame(response, service.createMain(reviewed, file));
        verify(main).create(reviewed);
        verify(attachments).uploadToMainContractorInvoice(eq(42L), eq(file), eq(AttachmentType.MAIN_CONTRACTOR_INVOICE), anyString(), isNull());
    }

    @Test
    void rejectionByOrdinaryFinancialGuardDoesNotSaveXml() {
        var reviewed = new MainContractorInvoiceCreateRequest();
        when(main.create(reviewed)).thenThrow(new IllegalArgumentException("Przekroczono wartość umowy"));
        assertThrows(IllegalArgumentException.class, () -> service.createMain(reviewed, xml()));
        verifyNoInteractions(attachments);
    }

    @Test
    void assignmentAttachesFileToChildInvoiceRatherThanAssignmentId() {
        var reviewed = new MainInvoiceWithSubcontractorInvoiceRequest();
        var response = MainInvoiceSettlementResponse.builder().id(100L).subcontractorInvoiceId(43L).build();
        var file = xml();
        when(settlements.createWithInvoice(reviewed)).thenReturn(response);
        assertSame(response, service.createWithAssignment(reviewed, file));
        verify(attachments).uploadToSubcontractorInvoice(eq(43L), eq(file), eq(AttachmentType.SUBCONTRACTOR_INVOICE), anyString(), isNull());
        verifyNoInteractions(main, sub);
    }

    @Test
    void attachmentFailureIsPropagatedToRollBackWholeTransaction() {
        var reviewed = new MainContractorInvoiceCreateRequest();
        var response = MainContractorInvoiceResponse.builder().id(42L).build();
        when(main.create(reviewed)).thenReturn(response);
        // Tożsamość MultipartFile jest istotna: zapis ma zachować oryginalny plik.
        var file = xml();
        doThrow(new IllegalStateException("Brak miejsca")).when(attachments)
                .uploadToMainContractorInvoice(eq(42L), eq(file), eq(AttachmentType.MAIN_CONTRACTOR_INVOICE), anyString(), isNull());
        assertThrows(IllegalStateException.class, () -> service.createMain(reviewed, file));
    }

    @Test
    void allSaveEntryPointsHaveOuterTransaction() throws Exception {
        for (var method : KsefInvoiceImportService.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && !method.getName().equals("preview")) {
                assertNotNull(method.getAnnotation(Transactional.class), method.getName());
            }
        }
    }
}
