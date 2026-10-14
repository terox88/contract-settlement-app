package pl.company.settlements.contract.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.ContractValueBasis;
import pl.company.settlements.contract.domain.DeductionRule;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.RemunerationType;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.model.DeductionRuleRequest;
import pl.company.settlements.contract.model.SubcontractCreateRequest;
import pl.company.settlements.contract.model.SubcontractUpdateRequest;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.contract.repository.SubcontractRepository;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.contractor.model.ContractorCreateRequest;
import pl.company.settlements.contractor.repository.ContractorRepository;
import pl.company.settlements.contractor.service.ContractorService;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;
import pl.company.settlements.task.domain.InvestmentTask;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubcontractCreationServiceTest {

    @Mock private SubcontractRepository repository;
    @Mock private MainContractRepository mainContractRepository;
    @Mock private ContractorRepository contractorRepository;
    @Mock private SubcontractorInvoiceRepository subcontractorInvoiceRepository;
    @Mock private PaymentAllocationRepository paymentAllocationRepository;
    @Mock private ContractorService contractorService;
    @Mock private ContractHierarchyValidationService hierarchyValidationService;
    @InjectMocks private SubcontractService service;

    private MainContract main;
    private SubcontractCreateRequest request;

    @BeforeEach
    void setUp() {
        main = mock(MainContract.class);
        request = new SubcontractCreateRequest();
        request.setMainContractId(7L);
        request.setContractNumber(" PW/1 ");
        request.setContractDate(LocalDate.of(2026, 10, 14));
        request.setInitialValue(new BigDecimal("1230.00"));
        request.setVatRate(new BigDecimal("23.00"));
        request.setContractType(ContractType.SERVICE);
        request.setRemunerationType(RemunerationType.LUMP_SUM);
    }

    @Test
    void linksExistingSubcontractorWithoutCreatingOrUpdatingCompany() {
        prepareParentLookup();
        prepareSuccessfulSave();
        Contractor existing = company(3L);
        request.setSubcontractorId(3L);
        when(contractorRepository.findById(3L)).thenReturn(Optional.of(existing));

        var response = service.create(request);

        assertEquals(3L, response.getSubcontractorId());
        assertEquals("PW/1", response.getContractNumber());
        verifyNoInteractions(contractorService);
        verify(contractorRepository, never()).save(any());
    }

    @Test
    void createsSubcontractorThroughExistingServiceAndCalculatesBothDeductions() {
        prepareParentLookup();
        prepareSuccessfulSave();
        ContractorCreateRequest newData = newCompanyData();
        request.setNewSubcontractor(newData);
        request.setPerformanceSecurityDeduction(rule("5.00", ContractValueBasis.NET));
        request.setOtherDeduction(rule("2.00", ContractValueBasis.GROSS));
        when(contractorService.createEntity(newData)).thenReturn(company(4L));

        var response = service.create(request);

        verify(contractorService).createEntity(newData);
        verifyNoInteractions(contractorRepository);
        assertEquals(4L, response.getSubcontractorId());
        assertEquals(new BigDecimal("50.00"), response.getPerformanceSecurityDeductionAmount());
        assertEquals(new BigDecimal("24.60"), response.getOtherDeductionAmount());
        assertEquals(new BigDecimal("74.60"), response.getTotalContractualDeductionAmount());
    }

    @Test
    void rejectsBothSubcontractorVariants() {
        prepareParentLookup();
        request.setSubcontractorId(3L);
        request.setNewSubcontractor(newCompanyData());

        assertThrows(IllegalArgumentException.class, () -> service.create(request));

        verifyNoInteractions(repository, contractorRepository, contractorService);
    }

    @Test
    void rejectsMissingSubcontractor() {
        prepareParentLookup();

        assertThrows(IllegalArgumentException.class, () -> service.create(request));

        verifyNoInteractions(repository, contractorRepository, contractorService);
    }

    @Test
    void checksDatesBeforeCreatingCompany() {
        prepareParentLookup();
        request.setNewSubcontractor(newCompanyData());
        request.setInitialStartDate(LocalDate.of(2026, 12, 2));
        request.setInitialEndDate(LocalDate.of(2026, 12, 1));

        assertThrows(IllegalArgumentException.class, () -> service.create(request));

        verifyNoInteractions(repository, contractorRepository, contractorService);
    }

    @Test
    void hierarchyFailureStopsBeforeNewCompanyIsCreated() {
        prepareParentLookup();
        request.setNewSubcontractor(newCompanyData());
        doThrow(new IllegalArgumentException("Kwota przekracza wartość umowy głównej"))
                .when(hierarchyValidationService).validateSubcontractCreate(main, request);

        assertThrows(IllegalArgumentException.class, () -> service.create(request));

        verifyNoInteractions(repository, contractorRepository, contractorService);
    }

    @Test
    void propagatesSubcontractSaveFailureForTransactionalRollback() {
        prepareParentLookup();
        ContractorCreateRequest newData = newCompanyData();
        request.setNewSubcontractor(newData);
        when(contractorService.createEntity(newData)).thenReturn(company(4L));
        IllegalStateException failure = new IllegalStateException("Subcontract insert failed");
        when(repository.save(any(Subcontract.class))).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.create(request)));
    }

    @Test
    void updateCanClearDeductionRulesWithoutChangingPaymentTotals() {
        prepareResponseContext();
        Subcontract subcontract = new Subcontract(main, company(3L));
        subcontract.setId(21L);
        DeductionRule oldRule = new DeductionRule();
        oldRule.setPercentage(new BigDecimal("5.00"));
        oldRule.setBasis(ContractValueBasis.NET);
        subcontract.setPerformanceSecurityDeduction(oldRule);
        subcontract.setOtherDeduction(oldRule);
        when(hierarchyValidationService.lockSubcontract(21L)).thenReturn(subcontract);
        when(subcontractorInvoiceRepository.sumGrossAmountBySubcontractId(21L)).thenReturn(new BigDecimal("70.00"));
        when(paymentAllocationRepository.sumPaidAmountBySubcontractId(21L)).thenReturn(new BigDecimal("20.00"));
        when(paymentAllocationRepository.sumDeductedAmountBySubcontractId(21L)).thenReturn(new BigDecimal("5.00"));
        SubcontractUpdateRequest update = new SubcontractUpdateRequest();
        update.setContractNumber(request.getContractNumber());
        update.setContractDate(request.getContractDate());
        update.setInitialValue(request.getInitialValue());
        update.setVatRate(request.getVatRate());
        update.setContractType(request.getContractType());
        update.setRemunerationType(request.getRemunerationType());

        var response = service.update(21L, update);

        assertNull(subcontract.getPerformanceSecurityDeduction());
        assertNull(subcontract.getOtherDeduction());
        assertEquals(BigDecimal.ZERO, response.getTotalContractualDeductionAmount());
        assertEquals(new BigDecimal("20.00"), response.getPaidAmount());
        assertEquals(new BigDecimal("5.00"), response.getDeductedAmount());
        assertEquals(new BigDecimal("45.00"), response.getOutstandingInvoiceAmount());
    }

    private void prepareParentLookup() {
        when(mainContractRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(main));
    }

    private void prepareResponseContext() {
        InvestmentTask task = mock(InvestmentTask.class);
        when(main.getId()).thenReturn(7L);
        when(main.getContractNumber()).thenReturn("UG/7");
        when(main.getInvestmentTask()).thenReturn(task);
        when(task.getId()).thenReturn(1L);
        when(task.getTaskNumber()).thenReturn("Z/1");
    }

    private void prepareSuccessfulSave() {
        prepareResponseContext();
        when(repository.save(any(Subcontract.class))).thenAnswer(invocation -> {
            Subcontract saved = invocation.getArgument(0);
            saved.setId(21L);
            return saved;
        });
    }

    private Contractor company(Long id) {
        Contractor result = new Contractor("Firma", "1234567890");
        result.setId(id);
        return result;
    }

    private ContractorCreateRequest newCompanyData() {
        ContractorCreateRequest result = new ContractorCreateRequest();
        result.setName("Firma");
        result.setTaxId("1234567890");
        return result;
    }

    private DeductionRuleRequest rule(String percentage, ContractValueBasis basis) {
        DeductionRuleRequest result = new DeductionRuleRequest();
        result.setPercentage(new BigDecimal(percentage));
        result.setBasis(basis);
        return result;
    }
}
