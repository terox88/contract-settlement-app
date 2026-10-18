package pl.company.settlements.invoice.service;

import org.junit.jupiter.api.Test;
import pl.company.settlements.contract.domain.ContractValueBasis;
import pl.company.settlements.contract.domain.DeductionRule;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.service.ContractHierarchyValidationService;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.payment.domain.Payment;
import pl.company.settlements.payment.domain.PaymentAllocation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeductionReviewServiceTest {
    private final SubcontractorInvoiceRepository repository = mock(SubcontractorInvoiceRepository.class);
    private final ContractHierarchyValidationService hierarchy = mock(ContractHierarchyValidationService.class);
    private final DeductionReviewService service = new DeductionReviewService(repository,hierarchy);
    private SubcontractorInvoice invoice() {
        Contractor firm = new Contractor("Firma", "1234563218");
        MainContract main = new MainContract(null,firm); main.setId(7L);
        Subcontract contract = new Subcontract(main,firm); contract.setId(21L);
        DeductionRule rule = new DeductionRule(); rule.setPercentage(new BigDecimal("5")); rule.setBasis(ContractValueBasis.GROSS); contract.setPerformanceSecurityDeduction(rule);
        SubcontractorInvoice invoice = new SubcontractorInvoice(contract); invoice.setId(41L); invoice.setNetAmount(new BigDecimal("1000")); invoice.setGrossAmount(new BigDecimal("1000"));
        Payment payment = new Payment(LocalDate.of(2026,10,18),new BigDecimal("951"));
        PaymentAllocation allocation = new PaymentAllocation(payment,invoice); allocation.setAllocatedAmount(new BigDecimal("951")); allocation.setDeductedAmount(new BigDecimal("49")); invoice.getPaymentAllocations().add(allocation);
        when(repository.findById(41L)).thenReturn(Optional.of(invoice));
        when(repository.findByIdForUpdate(41L)).thenReturn(Optional.of(invoice));
        when(repository.findMainContractIdById(41L)).thenReturn(Optional.of(7L));
        return invoice;
    }
    @Test void confirmationIsStoredOnInvoiceUnderRootLock() {
        var invoice = invoice(); var review = service.get(41L);
        assertEquals("CONFIRMED",service.confirm(41L,review.fingerprint()).status());
        assertEquals(review.fingerprint(),invoice.getDeductionConfirmedFingerprint()); assertNotNull(invoice.getDeductionConfirmedAt()); verify(hierarchy).lockMainContract(7L);
    }
    @Test void staleConfirmationCannotApproveChangedAmounts() {
        var invoice=invoice();String fingerprint=service.get(41L).fingerprint();invoice.getPaymentAllocations().get(0).setDeductedAmount(new BigDecimal("48"));
        assertThrows(IllegalArgumentException.class,()->service.confirm(41L,fingerprint)); assertNull(invoice.getDeductionConfirmedFingerprint());
    }
    @Test void subsequentChangeRestoresWarning() {
        var invoice=invoice();service.confirm(41L,service.get(41L).fingerprint());invoice.getPaymentAllocations().get(0).setDeductedAmount(new BigDecimal("48"));
        var review=service.get(41L);assertEquals("MISMATCH",review.status());assertTrue(review.confirmationOutdated());
    }
    @Test void confirmationCanBeRevoked() {
        var invoice=invoice();service.confirm(41L,service.get(41L).fingerprint());assertEquals("MISMATCH",service.revoke(41L).status());
        assertNull(invoice.getDeductionConfirmedFingerprint());assertNull(invoice.getDeductionConfirmedAt());
    }
}
