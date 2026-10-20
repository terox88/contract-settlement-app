package pl.company.settlements.file.api;

import org.junit.jupiter.api.Test;
import pl.company.settlements.file.domain.AttachmentOwner;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.file.service.DocumentAttachmentService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AttachmentControllerTest {
    @Test
    void paymentTypesComeFromBackendWithPolishLabels() {
        var service = mock(DocumentAttachmentService.class);
        var controller = new AttachmentController(service);
        var options = controller.types(AttachmentOwner.PAYMENT);
        assertEquals(1, options.size());
        assertEquals(AttachmentType.PAYMENT_CONFIRMATION, options.get(0).value());
        assertEquals("Potwierdzenie przelewu", options.get(0).label());
        verifyNoInteractions(service);
    }

    @Test
    void invoiceOptionsDoNotAllowUnassignedTransferProof() {
        var controller = new AttachmentController(mock(DocumentAttachmentService.class));
        assertFalse(controller.types(AttachmentOwner.SUB_INVOICE).stream()
                .anyMatch(option -> option.value() == AttachmentType.PAYMENT_CONFIRMATION));
    }
    @Test
    void contractsAndAmendmentsOfferInsuranceAndGuaranteeDocuments() {
        var controller = new AttachmentController(mock(DocumentAttachmentService.class));
        for (AttachmentOwner owner : new AttachmentOwner[]{AttachmentOwner.CONTRACT, AttachmentOwner.AMENDMENT}) {
            var options = controller.types(owner);
            assertTrue(options.stream().anyMatch(option -> option.value() == AttachmentType.INSURANCE_DOCUMENT
                    && option.label().equals("Dokument ubezpieczenia")));
            assertTrue(options.stream().anyMatch(option -> option.value() == AttachmentType.GUARANTEE_DOCUMENT
                    && option.label().equals("Dokument gwarancji bankowej/ubezpieczeniowej")));
        }
    }
}
