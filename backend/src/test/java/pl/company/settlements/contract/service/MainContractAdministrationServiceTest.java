package pl.company.settlements.contract.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.RemunerationType;
import pl.company.settlements.contract.domain.PerformanceSecurityType;
import pl.company.settlements.contract.domain.SecurityReturnStage;
import pl.company.settlements.contract.model.SecurityReturnRequest;
import pl.company.settlements.contract.model.MainContractAdministrationRequest;
import pl.company.settlements.contract.model.MainContractUpdateRequest;
import pl.company.settlements.contract.model.MainContractCreateRequest;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

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
    }

    @Test
    void updatesAndReturnsActualCompletionAndComputedWarrantyEnd() {
        lockExisting();
        enableResponse();
        MainContractUpdateRequest request = update(); request.setAdministration(administration(60));
        var response = service.update(7L, request);
        assertEquals(LocalDate.parse("2031-10-20"), response.getAdministration().warrantyEndDate());
        assertEquals(LocalDate.parse("2026-10-20"), contract.getAdministration().getActualCompletionDate());
    }

    @Test
    void oldClientOmittingNewObjectPreservesExistingAdministration() {
        lockExisting();
        enableResponse();
        var stored = MainContractAdministrationRules.validatedValue(administration(60));
        contract.setAdministration(stored);
        service.update(7L, update());
        assertSame(stored, contract.getAdministration());
    }

    @Test
    void invalidAdministrationIsRejectedBeforeChangingContract() {
        lockExisting();
        MainContractUpdateRequest request = update(); request.setAdministration(administration(-1));
        assertThrows(IllegalArgumentException.class, () -> service.update(7L, request));
        assertEquals("OLD", contract.getContractNumber());
    }

    @Test
    void creatingBeforeSigningIsRejectedBeforeSavingContractOrCreatingContractor() {
        MainContractCreateRequest request = new MainContractCreateRequest();
        request.setContractDate(LocalDate.parse("2026-10-21"));
        request.setAdministration(administration(60));
        var error = assertThrows(IllegalArgumentException.class, () -> service.create(request));
        assertEquals("Faktyczna data zakończenia umowy nie może być wcześniejsza niż data jej zawarcia.", error.getMessage());
        verifyNoInteractions(repository, tasks, contractors, contractorService, limits);
    }

    @Test
    void editingCannotMoveSigningAfterActualCompletion() {
        lockExisting();
        MainContractUpdateRequest request = update(); request.setContractDate(LocalDate.parse("2026-10-21"));
        request.setAdministration(administration(60));
        assertThrows(IllegalArgumentException.class, () -> service.update(7L, request));
        assertEquals("OLD", contract.getContractNumber());
    }

    @Test
    void omittedAdministrationStillChecksStoredCompletionAgainstNewSigningDate() {
        lockExisting();
        contract.setAdministration(MainContractAdministrationRules.validatedValue(administration(60)));
        MainContractUpdateRequest request = update(); request.setContractDate(LocalDate.parse("2026-10-21"));
        assertThrows(IllegalArgumentException.class, () -> service.update(7L, request));
        assertEquals("OLD", contract.getContractNumber());
    }

    @Test
    void completionOnSigningDateIsAccepted() {
        lockExisting(); enableResponse();
        MainContractUpdateRequest request = update(); request.setContractDate(LocalDate.parse("2026-10-20"));
        request.setAdministration(administration(60));
        assertEquals(LocalDate.parse("2026-10-20"), service.update(7L, request).getAdministration().actualCompletionDate());
    }

    private void lockExisting() {
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(contract));
    }

    @Test
    void marksReturnWithoutRewritingTheRestOfTheContractAndCanUndoIt() {
        lockExisting();
        var administration = new MainContractAdministrationRequest(null, null, null, null,
                PerformanceSecurityType.CASH, new BigDecimal("1000"), null, null, null, null);
        contract.setAdministration(MainContractAdministrationRules.validatedValue(administration));
        assertTrue(service.updateSecurityReturn(7L, new SecurityReturnRequest(SecurityReturnStage.PERFORMANCE, true)).securityReturned());
        assertEquals("OLD", contract.getContractNumber());
        assertEquals(new BigDecimal("1000"), contract.getAdministration().getSecurityAmount());
        assertFalse(service.updateSecurityReturn(7L, new SecurityReturnRequest(SecurityReturnStage.PERFORMANCE, false)).securityReturned());
        verifyNoInteractions(tasks, contractors, contractorService, invoices, payments, hierarchy, limits);
    }

    @Test
    void normalContractEditPreservesReturnMarkedInDetails() {
        lockExisting(); enableResponse();
        var administration = new MainContractAdministrationRequest(null, null, null, null,
                PerformanceSecurityType.CASH, new BigDecimal("1000"), null, null, null, null);
        contract.setAdministration(MainContractAdministrationRules.validatedValue(administration));
        service.updateSecurityReturn(7L, new SecurityReturnRequest(SecurityReturnStage.PERFORMANCE, true));
        MainContractUpdateRequest request = update(); request.setAdministration(administration);
        assertTrue(service.update(7L, request).getAdministration().securityReturned());
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
