package pl.company.settlements.contract.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.RemunerationType;
import pl.company.settlements.contract.model.MainContractAdministrationRequest;
import pl.company.settlements.contract.model.MainContractUpdateRequest;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.contractor.repository.ContractorRepository;
import pl.company.settlements.contractor.service.ContractorService;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;
import pl.company.settlements.task.domain.InvestmentTask;
import pl.company.settlements.task.repository.InvestmentTaskRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MainContractAdministrationServiceTest {
    @Mock private MainContractRepository repository;
    @Mock private InvestmentTaskRepository tasks;
    @Mock private ContractorRepository contractors;
    @Mock private MainContractorInvoiceRepository invoices;
    @Mock private PaymentAllocationRepository payments;
    @Mock private ContractorService contractorService;
    @Mock private ContractHierarchyValidationService hierarchy;
    @Mock private FinancialLimitService limits;
    private MainContractService service;
    private MainContract contract;

    @BeforeEach
    void setUp() {
        service = new MainContractService(repository, tasks, contractors, invoices, payments, contractorService, hierarchy, limits);
        InvestmentTask task = new InvestmentTask("Z/1", "Inwestycja"); task.setId(1L);
        Contractor contractor = new Contractor("Wykonawca", "1234563218"); contractor.setId(2L);
        contract = new MainContract(task, contractor); contract.setId(7L);
        contract.setContractNumber("OLD"); contract.setInitialValue(new BigDecimal("1000"));
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(contract));
    }

    @Test
    void updatesAndReturnsActualCompletionAndComputedWarrantyEnd() {
        enableResponse();
        MainContractUpdateRequest request = update(); request.setAdministration(administration(60));
        var response = service.update(7L, request);
        assertEquals(LocalDate.parse("2031-10-20"), response.getAdministration().warrantyEndDate());
        assertEquals(LocalDate.parse("2026-10-20"), contract.getAdministration().getActualCompletionDate());
    }

    @Test
    void oldClientOmittingNewObjectPreservesExistingAdministration() {
        enableResponse();
        var stored = MainContractAdministrationRules.validatedValue(administration(60));
        contract.setAdministration(stored);
        service.update(7L, update());
        assertSame(stored, contract.getAdministration());
    }

    @Test
    void invalidAdministrationIsRejectedBeforeChangingContract() {
        MainContractUpdateRequest request = update(); request.setAdministration(administration(-1));
        assertThrows(IllegalArgumentException.class, () -> service.update(7L, request));
        assertEquals("OLD", contract.getContractNumber());
    }

    private void enableResponse() {
        when(invoices.sumGrossAmountByMainContractId(7L)).thenReturn(BigDecimal.ZERO);
        when(payments.sumPaidAmountByMainContractId(7L)).thenReturn(BigDecimal.ZERO);
    }
    private MainContractUpdateRequest update() {
        MainContractUpdateRequest request = new MainContractUpdateRequest();
        request.setContractNumber("NEW"); request.setContractDate(LocalDate.parse("2026-01-01"));
        request.setInitialValue(new BigDecimal("1000")); request.setVatRate(new BigDecimal("23"));
        request.setContractType(ContractType.SERVICE); request.setRemunerationType(RemunerationType.LUMP_SUM);
        request.setInitialEndDate(LocalDate.parse("2027-01-01"));
        return request;
    }
    private MainContractAdministrationRequest administration(int months) {
        return new MainContractAdministrationRequest(LocalDate.parse("2026-10-20"), months,
                null, null, null, null, null, null, null, null);
    }
}
