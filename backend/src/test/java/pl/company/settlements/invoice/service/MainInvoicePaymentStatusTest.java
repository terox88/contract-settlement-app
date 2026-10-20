package pl.company.settlements.invoice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.contract.service.ContractHierarchyValidationService;
import pl.company.settlements.contract.service.ContractSettlementValidationService;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.model.MainContractorInvoiceResponse;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.invoice.repository.MainInvoiceSettlementRepository;
import pl.company.settlements.payment.domain.PaymentAllocation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MainInvoicePaymentStatusTest {
    @Mock private MainContractorInvoiceRepository invoices;
    @Mock private MainContractRepository contracts;
    @Mock private MainInvoiceSettlementRepository settlements;
    @Mock private ContractHierarchyValidationService hierarchy;
    @Mock private ContractSettlementValidationService limits;
    private MainContractorInvoiceService service;

    @BeforeEach
    void setUp() {
        service = new MainContractorInvoiceService(invoices, contracts, settlements, hierarchy, limits);
    }

    @ParameterizedTest
    @CsvSource({
            "0.00, 100.00, false",
            "99.99, 0.01, false",
            "100.00, 0.00, true",
            "100.01, -0.01, true"
    })
    void responseReportsActualFinancialBalance(String paid, String remaining, boolean settled) {
        MainContractorInvoice invoice = invoice(10L);
        if (new BigDecimal(paid).signum() > 0) {
            addAllocation(invoice, "20.00");
            addAllocation(invoice, new BigDecimal(paid).subtract(new BigDecimal("20.00")).toPlainString());
        }
        when(invoices.findById(10L)).thenReturn(Optional.of(invoice));

        MainContractorInvoiceResponse response = service.getById(10L);

        assertMoney(paid, response.getPaidAmount());
        assertMoney(remaining, response.getRemainingAmount());
        assertEquals(settled, response.isSettled());
    }

    @Test
    void listingReturnsSeparateBalancesForEveryInvoice() {
        MainContractorInvoice first = invoice(10L);
        MainContractorInvoice second = invoice(11L);
        addAllocation(first, "40.00");
        addAllocation(first, "55.00");
        when(contracts.existsById(7L)).thenReturn(true);
        when(invoices.findAllByMainContract_IdOrderByIssueDateDescIdDesc(7L)).thenReturn(List.of(first, second));

        List<MainContractorInvoiceResponse> responses = service.findAllByMainContractId(7L);

        assertMoney("5.00", responses.get(0).getRemainingAmount());
        assertMoney("100.00", responses.get(1).getRemainingAmount());
        assertFalse(responses.get(0).isSettled());
        assertFalse(responses.get(1).isSettled());
    }

    @Test
    void documentReviewDoesNotCloseUnpaidInvoiceAndRemovingAllocationReopensItsBalance() {
        MainContractorInvoice invoice = invoice(10L);
        invoice.setReviewFingerprint("reviewed-documents");
        invoice.setReviewedAt(Instant.parse("2026-10-19T12:00:00Z"));
        when(invoices.findById(10L)).thenReturn(Optional.of(invoice));
        assertFalse(service.getById(10L).isSettled());

        addAllocation(invoice, "100.00");
        assertTrue(service.getById(10L).isSettled());
        invoice.getPaymentAllocations().clear();

        MainContractorInvoiceResponse reopened = service.getById(10L);
        assertFalse(reopened.isSettled());
        assertMoney("100.00", reopened.getRemainingAmount());
    }

    private MainContractorInvoice invoice(Long id) {
        MainContract mainContract = mock(MainContract.class, RETURNS_DEEP_STUBS);
        MainContractorInvoice invoice = new MainContractorInvoice(mainContract);
        invoice.setId(id);
        invoice.setGrossAmount(new BigDecimal("100.00"));
        return invoice;
    }

    private void addAllocation(MainContractorInvoice invoice, String amount) {
        PaymentAllocation allocation = new PaymentAllocation(null, invoice);
        allocation.setAllocatedAmount(new BigDecimal(amount));
        invoice.getPaymentAllocations().add(allocation);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
