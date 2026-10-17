package pl.company.settlements.contract.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.RemunerationType;
import pl.company.settlements.contract.model.MainContractCreateRequest;
import pl.company.settlements.contract.model.FinancialLimitPlanRequest;
import pl.company.settlements.contract.model.FinancialLimitYearRequest;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.contractor.model.ContractorCreateRequest;
import pl.company.settlements.contractor.repository.ContractorRepository;
import pl.company.settlements.contractor.service.ContractorService;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;
import pl.company.settlements.task.domain.InvestmentTask;
import pl.company.settlements.task.repository.InvestmentTaskRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MainContractCreationServiceTest {
    @Mock private MainContractRepository repository;
    @Mock private InvestmentTaskRepository investmentTaskRepository;
    @Mock private ContractorRepository contractorRepository;
    @Mock private MainContractorInvoiceRepository mainContractorInvoiceRepository;
    @Mock private PaymentAllocationRepository paymentAllocationRepository;
    @Mock private ContractorService contractorService;
    @Mock private ContractHierarchyValidationService hierarchyValidationService;
    @Mock private FinancialLimitService financialLimitService;
    @InjectMocks private MainContractService service;

    private InvestmentTask task;
    private MainContractCreateRequest request;

    @BeforeEach
    void setUp() {
        task = mock(InvestmentTask.class);
        when(investmentTaskRepository.findById(1L)).thenReturn(Optional.of(task));
        request = new MainContractCreateRequest();
        request.setInvestmentTaskId(1L);
        request.setContractNumber(" UM/2026/01 ");
        request.setContractDate(LocalDate.of(2026, 10, 14));
        request.setInitialValue(new BigDecimal("100.00"));
        request.setVatRate(new BigDecimal("23.00"));
        request.setContractType(ContractType.CONSTRUCTION_WORKS);
        request.setRemunerationType(RemunerationType.LUMP_SUM);
    }

    @Test
    void linksExistingContractorWithoutCreatingOrUpdatingIt() {
        Contractor existing = new Contractor("Istniejąca firma", "1234567890");
        existing.setId(3L);
        request.setMainContractorId(3L);
        when(contractorRepository.findById(3L)).thenReturn(Optional.of(existing));
        prepareSuccessfulSave();

        var response = service.create(request);

        ArgumentCaptor<MainContract> captor = ArgumentCaptor.forClass(MainContract.class);
        verify(repository).save(captor.capture());
        assertSame(existing, captor.getValue().getMainContractor());
        assertEquals("UM/2026/01", captor.getValue().getContractNumber());
        assertEquals(3L, response.getMainContractorId());
        assertEquals(BigDecimal.ZERO, response.getSubcontractsTotalValue());
        assertEquals(new BigDecimal("100.00"), response.getRemainingForSubcontracts());
        verifyNoInteractions(contractorService);
        verify(contractorRepository, never()).save(any());
        verifyNoInteractions(financialLimitService);
    }

    @Test
    void createsContractorThroughExistingServiceAndLinksReturnedEntity() {
        ContractorCreateRequest newData = newContractorData();
        request.setNewMainContractor(newData);
        Contractor created = new Contractor("Nowa firma", "1234567890");
        created.setId(4L);
        when(contractorService.createEntity(newData)).thenReturn(created);
        prepareSuccessfulSave();

        var response = service.create(request);

        verify(contractorService).createEntity(newData);
        verifyNoInteractions(contractorRepository);
        assertEquals(4L, response.getMainContractorId());
        assertEquals(new BigDecimal("100.00"), response.getCurrentValue());
    }

    @Test
    void rejectsRequestContainingBothContractorVariants() {
        request.setMainContractorId(3L);
        request.setNewMainContractor(newContractorData());

        assertThrows(IllegalArgumentException.class, () -> service.create(request));

        verifyNoInteractions(contractorService, contractorRepository, repository);
    }

    @Test
    void rejectsRequestWithoutContractor() {
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verifyNoInteractions(contractorService, contractorRepository, repository);
    }

    @Test
    void validatesDatesBeforeCreatingContractor() {
        request.setNewMainContractor(newContractorData());
        request.setInitialStartDate(LocalDate.of(2026, 11, 1));
        request.setInitialEndDate(LocalDate.of(2026, 10, 1));

        assertThrows(IllegalArgumentException.class, () -> service.create(request));

        verifyNoInteractions(contractorService, contractorRepository, repository);
    }

    @Test
    void propagatesContractSaveFailureSoTransactionCanRollBack() {
        ContractorCreateRequest newData = newContractorData();
        request.setNewMainContractor(newData);
        Contractor created = new Contractor("Nowa firma", "1234567890");
        when(contractorService.createEntity(newData)).thenReturn(created);
        IllegalStateException failure = new IllegalStateException("Contract insert failed");
        when(repository.save(any(MainContract.class))).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.create(request)));
    }

    @Test
    void createsExplicitLimitsAfterSavingContract() {
        request.setNewMainContractor(newContractorData());
        when(contractorService.createEntity(request.getNewMainContractor()))
                .thenReturn(new Contractor("Nowa firma", "1234567890"));
        request.setFinancialLimitYears(List.of(year(2026, "40.00"), year(2027, "60.00")));
        prepareSuccessfulSave();

        var response = service.create(request);

        ArgumentCaptor<FinancialLimitPlanRequest> plan = ArgumentCaptor.forClass(FinancialLimitPlanRequest.class);
        var order = inOrder(repository, financialLimitService);
        order.verify(repository).save(any(MainContract.class));
        order.verify(financialLimitService).createInitialPlan(eq(10L), plan.capture());
        assertEquals(10L, response.getId());
        assertEquals(request.getFinancialLimitYears(), plan.getValue().years());
    }

    @Test
    void propagatesLimitFailureSoWholeCreationTransactionCanRollBack() {
        request.setNewMainContractor(newContractorData());
        when(contractorService.createEntity(request.getNewMainContractor()))
                .thenReturn(new Contractor("Nowa firma", "1234567890"));
        request.setFinancialLimitYears(List.of(year(2026, "100.00")));
        when(repository.save(any(MainContract.class))).thenAnswer(invocation -> {
            MainContract contract = invocation.getArgument(0);
            contract.setId(10L);
            return contract;
        });
        IllegalArgumentException failure = new IllegalArgumentException("Insufficient budget");
        when(financialLimitService.createInitialPlan(eq(10L), any(FinancialLimitPlanRequest.class)))
                .thenThrow(failure);

        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> service.create(request)));
    }

    private FinancialLimitYearRequest year(int year, String amount) {
        FinancialLimitYearRequest row = new FinancialLimitYearRequest();
        row.setYear(year);
        row.setAmount(new BigDecimal(amount));
        return row;
    }

    private void prepareSuccessfulSave() {
        when(task.getId()).thenReturn(1L);
        when(task.getTaskNumber()).thenReturn("Z/2026/01");
        when(repository.save(any(MainContract.class))).thenAnswer(invocation -> {
            MainContract contract = invocation.getArgument(0);
            contract.setId(10L);
            return contract;
        });
    }

    private ContractorCreateRequest newContractorData() {
        ContractorCreateRequest data = new ContractorCreateRequest();
        data.setName("Nowa firma");
        data.setTaxId("1234567890");
        return data;
    }
}
