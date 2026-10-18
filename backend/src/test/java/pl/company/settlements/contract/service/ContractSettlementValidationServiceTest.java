package pl.company.settlements.contract.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.contract.domain.ContractAmendment;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractSettlementValidationServiceTest {
    @Mock private MainContractorInvoiceRepository mainInvoiceRepository;
    @Mock private SubcontractorInvoiceRepository subInvoiceRepository;
    @Mock private PaymentAllocationRepository allocationRepository;
    @InjectMocks private ContractSettlementValidationService service;
    private MainContract main;
    private Subcontract sub;
    private SubcontractorInvoice invoice;

    @BeforeEach
    void setUp() {
        var firm = new Contractor("Firma", "2222222222");
        main = new MainContract(null, firm); main.setId(7L); main.setInitialValue(n("1000")); main.setContractNumber("UG/7");
        sub = new Subcontract(main, firm); sub.setId(21L); sub.setInitialValue(n("120000")); sub.setContractNumber("PW/21");
        invoice = new SubcontractorInvoice(sub); invoice.setId(40L); invoice.setGrossAmount(n("100000")); invoice.setInvoiceNumber("FV/1");
    }

    @Test
    void reportedHugeInvoiceIsRejectedAgainst120000Contract() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> service.validateInvoice(sub, n("0"), n("5112572"), "PLN"));
        assertTrue(error.getMessage().contains("Suma wartości brutto faktur"));
        assertTrue(error.getMessage().contains("120000 PLN"));
    }

    @Test
    void earlierInvoicesLeaveOnlyTheirUnusedContractCapacity() {
        when(subInvoiceRepository.sumGrossAmountBySubcontractId(21L)).thenReturn(n("100000"));
        assertDoesNotThrow(() -> service.validateInvoice(sub, n("0"), n("20000"), "pln"));
        assertThrows(IllegalArgumentException.class, () -> service.validateInvoice(sub, n("0"), n("20000.01"), "PLN"));
    }

    @Test
    void editingReplacesInvoiceAmountInsteadOfCountingItTwice() {
        when(subInvoiceRepository.sumGrossAmountBySubcontractId(21L)).thenReturn(n("110000"));
        assertDoesNotThrow(() -> service.validateInvoice(sub, n("100000"), n("110000"), "PLN"));
        assertThrows(IllegalArgumentException.class, () -> service.validateInvoice(sub, n("100000"), n("110000.01"), "PLN"));
    }

    @Test
    void positiveAndNegativeAmendmentsChangeInvoiceCapacity() {
        var amendment = new ContractAmendment(sub); amendment.setValueChange(n("30000")); sub.getAmendments().add(amendment);
        assertDoesNotThrow(() -> service.validateInvoice(sub, n("0"), n("150000"), "PLN"));
        amendment.setValueChange(n("-30000"));
        assertThrows(IllegalArgumentException.class, () -> service.validateInvoice(sub, n("0"), n("90000.01"), "PLN"));
    }

    @Test
    void mainContractUsesOnlyItsOwnInvoices() {
        when(mainInvoiceRepository.sumGrossAmountByMainContractId(7L)).thenReturn(n("700"));
        assertDoesNotThrow(() -> service.validateInvoice(main, n("0"), n("300"), "PLN"));
        assertThrows(IllegalArgumentException.class, () -> service.validateInvoice(main, n("0"), n("300.01"), "PLN"));
        verifyNoInteractions(subInvoiceRepository, allocationRepository);
    }

    @Test
    void lowerTierUsesItsOwnValueAndDoesNotCountDescendantsTwice() {
        var child = new Subcontract(main, sub.getSubcontractor()); child.setId(30L); child.setInitialValue(n("500")); child.setParentSubcontract(sub);
        when(subInvoiceRepository.sumGrossAmountBySubcontractId(30L)).thenReturn(n("450"));
        assertDoesNotThrow(() -> service.validateInvoice(child, n("0"), n("50"), "PLN"));
        assertThrows(IllegalArgumentException.class, () -> service.validateInvoice(child, n("0"), n("50.01"), "PLN"));
    }

    @Test
    void paymentsAndDeductionsToOtherInvoicesConsumeContractCapacity() {
        when(allocationRepository.sumPaidAmountBySubcontractId(21L)).thenReturn(n("110000"));
        when(allocationRepository.sumDeductedAmountBySubcontractId(21L)).thenReturn(n("5000"));
        assertDoesNotThrow(() -> service.validateAllocation(invoice, n("0"), n("0"), n("4000"), n("1000")));
        assertThrows(IllegalArgumentException.class, () -> service.validateAllocation(invoice, n("0"), n("0"), n("4000.01"), n("1000")));
    }

    @Test
    void repeatedPaymentsCannotOverpayOneInvoiceEvenWithFreeContractCapacity() {
        when(allocationRepository.sumPaidAmountBySubcontractorInvoiceId(40L)).thenReturn(n("80000"));
        when(allocationRepository.sumDeductedAmountBySubcontractorInvoiceId(40L)).thenReturn(n("5000"));
        assertDoesNotThrow(() -> service.validateAllocation(invoice, n("0"), n("0"), n("14000"), n("1000")));
        var error = assertThrows(IllegalArgumentException.class, () -> service.validateAllocation(invoice, n("0"), n("0"), n("14000.01"), n("1000")));
        assertTrue(error.getMessage().contains("wartość brutto faktury"));
    }

    @Test
    void editingPaymentReplacesPreviousPaidAndDeductedAmounts() {
        when(allocationRepository.sumPaidAmountBySubcontractId(21L)).thenReturn(n("110000"));
        when(allocationRepository.sumDeductedAmountBySubcontractId(21L)).thenReturn(n("5000"));
        when(allocationRepository.sumPaidAmountBySubcontractorInvoiceId(40L)).thenReturn(n("19000"));
        when(allocationRepository.sumDeductedAmountBySubcontractorInvoiceId(40L)).thenReturn(n("1000"));
        assertDoesNotThrow(() -> service.validateAllocation(invoice, n("19000"), n("1000"), n("24000"), n("1000")));
        assertThrows(IllegalArgumentException.class, () -> service.validateAllocation(invoice, n("19000"), n("1000"), n("24000.01"), n("1000")));
    }

    @Test
    void replacingDeductionsWithCashCannotIncreaseAnAlreadyExceededPaymentLimit() {
        when(allocationRepository.sumPaidAmountBySubcontractId(21L)).thenReturn(n("110000"));
        when(allocationRepository.sumDeductedAmountBySubcontractId(21L)).thenReturn(n("20000"));
        assertThrows(IllegalArgumentException.class, () -> service.validateAllocation(invoice,
                n("10000"), n("20000"), n("30000"), n("0")));
    }

    @Test
    void cannotReduceContractOrAmendmentBelowPreviouslyInvoicedAmount() {
        when(subInvoiceRepository.sumGrossAmountBySubcontractId(21L)).thenReturn(n("100000"));
        assertDoesNotThrow(() -> service.validateContractValue(sub, n("100000")));
        assertThrows(IllegalArgumentException.class, () -> service.validateContractValue(sub, n("99999.99")));
    }

    @Test
    void cannotReduceContractBelowLegacyPaymentsEvenIfInvoicesAreSmaller() {
        when(subInvoiceRepository.sumGrossAmountBySubcontractId(21L)).thenReturn(n("100000"));
        when(allocationRepository.sumPaidAmountBySubcontractId(21L)).thenReturn(n("110000"));
        assertThrows(IllegalArgumentException.class, () -> service.validateContractValue(sub, n("109999.99")));
    }

    @Test
    void mainInvoicePaymentAlsoIncludesPaymentsToOtherInvoicesOnItsContract() {
        var mainInvoice = new MainContractorInvoice(main); mainInvoice.setId(10L); mainInvoice.setGrossAmount(n("800"));
        when(allocationRepository.sumPaidAmountByMainContractId(7L)).thenReturn(n("900"));
        when(allocationRepository.sumPaidAmountByMainContractorInvoiceId(10L)).thenReturn(n("300"));
        assertDoesNotThrow(() -> service.validateAllocation(mainInvoice, n("0"), n("0"), n("100"), n("0")));
        assertThrows(IllegalArgumentException.class, () -> service.validateAllocation(mainInvoice, n("0"), n("0"), n("100.01"), n("0")));
    }

    @Test
    void mainInvoiceCannotBeReducedBelowPaidAmount() {
        var mainInvoice = new MainContractorInvoice(main); mainInvoice.setId(10L); mainInvoice.setGrossAmount(n("800"));
        when(allocationRepository.sumPaidAmountByMainContractorInvoiceId(10L)).thenReturn(n("700"));
        assertDoesNotThrow(() -> service.validateInvoiceReduction(mainInvoice, n("700")));
        assertThrows(IllegalArgumentException.class, () -> service.validateInvoiceReduction(mainInvoice, n("699.99")));
    }

    @Test
    void legacyOverrunsMayBeReducedButCannotBeIncreased() {
        when(subInvoiceRepository.sumGrossAmountBySubcontractId(21L)).thenReturn(n("5112572"));
        assertDoesNotThrow(() -> service.validateInvoice(sub, n("5000000"), n("4000000"), "PLN"));
        assertThrows(IllegalArgumentException.class, () -> service.validateInvoice(sub, n("0"), n("0.01"), "PLN"));
    }

    @Test
    void foreignCurrencyCannotBypassContractLimitWithoutExchangeRate() {
        assertThrows(IllegalArgumentException.class, () -> service.validateInvoice(sub, n("0"), n("100"), "EUR"));
        verifyNoInteractions(mainInvoiceRepository, subInvoiceRepository, allocationRepository);
    }

    private static BigDecimal n(String value) { return new BigDecimal(value); }
}
