package pl.company.settlements.contract.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.contract.domain.Contract;
import pl.company.settlements.contract.domain.ContractAmendment;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.contract.repository.SubcontractRepository;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.contractor.repository.ContractorRepository;
import pl.company.settlements.contractor.service.ContractorService;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;
import pl.company.settlements.task.domain.InvestmentTask;
import pl.company.settlements.task.repository.InvestmentTaskRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractHierarchyResponseTest {
    @Mock private MainContractRepository mainContractRepository;
    @Mock private SubcontractRepository subcontractRepository;
    @Mock private ContractorRepository contractorRepository;
    @Mock private ContractorService contractorService;
    @Mock private ContractHierarchyValidationService hierarchyValidationService;
    @Mock private MainContractorInvoiceRepository mainContractorInvoiceRepository;
    @Mock private SubcontractorInvoiceRepository subcontractorInvoiceRepository;
    @Mock private PaymentAllocationRepository paymentAllocationRepository;
    @Mock private InvestmentTaskRepository investmentTaskRepository;
    @InjectMocks private MainContractService mainService;
    @InjectMocks private SubcontractService subService;

    @Test
    void mainResponseIncludesSumOfCurrentGrossValuesAndRemainingAmount() {
        MainContract main = mainContract();
        main.getAmendments().add(amendment(main, "100"));
        Subcontract first = subcontract(main, 21L, "200");
        first.getAmendments().add(amendment(first, "100"));
        main.getSubcontracts().add(first);
        main.getSubcontracts().add(subcontract(main, 22L, "600"));
        when(mainContractRepository.findById(7L)).thenReturn(Optional.of(main));
        when(mainContractorInvoiceRepository.sumGrossAmountByMainContractId(7L)).thenReturn(BigDecimal.ZERO);
        when(paymentAllocationRepository.sumPaidAmountByMainContractId(7L)).thenReturn(BigDecimal.ZERO);

        var response = mainService.getById(7L);

        assertEquals(new BigDecimal("1100.00"), response.getCurrentValue());
        assertEquals(new BigDecimal("900.00"), response.getSubcontractsTotalValue());
        assertEquals(new BigDecimal("200.00"), response.getRemainingForSubcontracts());
    }

    @Test
    void subcontractResponseSignalsLateAmendedEndEvenWhenItEqualsOriginalEnd() {
        MainContract main = mainContract();
        Subcontract sub = subcontract(main, 21L, "300");
        sub.setInitialEndDate(LocalDate.of(2027, 1, 15));
        ContractAmendment amendment = amendment(sub, "0");
        amendment.setNewEndDate(sub.getInitialEndDate());
        sub.getAmendments().add(amendment);
        prepareSubcontractRead(sub);

        var response = subService.getById(21L);

        assertEquals(LocalDate.of(2026, 12, 31), response.getMainContractEndDate());
        assertEquals(LocalDate.of(2027, 1, 15), response.getCurrentEndDate());
        assertTrue(response.isExceedsMainContractEndDate());
        assertTrue(response.isEndDateOverriddenByAmendment());
    }

    @Test
    void equalEndDoesNotWarnAndValueOnlyAmendmentDoesNotOverrideEndDate() {
        Subcontract sub = subcontract(mainContract(), 21L, "300");
        sub.getAmendments().add(amendment(sub, "100"));
        prepareSubcontractRead(sub);

        var response = subService.getById(21L);

        assertFalse(response.isExceedsMainContractEndDate());
        assertFalse(response.isEndDateOverriddenByAmendment());
    }

    private MainContract mainContract() {
        InvestmentTask task = mock(InvestmentTask.class);
        MainContract main = new MainContract(task, new Contractor("Generalny wykonawca", "1111111111"));
        main.setId(7L);
        main.setInitialValue(new BigDecimal("1000.00"));
        main.setContractDate(LocalDate.of(2026, 10, 1));
        main.setInitialEndDate(LocalDate.of(2026, 12, 31));
        return main;
    }

    private Subcontract subcontract(MainContract main, Long id, String value) {
        Subcontract sub = new Subcontract(main, new Contractor("Podwykonawca", "2222222222"));
        sub.setId(id);
        sub.setInitialValue(new BigDecimal(value).setScale(2));
        sub.setVatRate(new BigDecimal("23.00"));
        sub.setContractDate(LocalDate.of(2026, 11, 1));
        sub.setInitialEndDate(LocalDate.of(2026, 12, 31));
        return sub;
    }

    private ContractAmendment amendment(Contract contract, String value) {
        ContractAmendment amendment = new ContractAmendment(contract);
        amendment.setEffectiveDate(LocalDate.of(2026, 11, 15));
        amendment.setValueChange(new BigDecimal(value).setScale(2));
        return amendment;
    }

    private void prepareSubcontractRead(Subcontract sub) {
        when(subcontractRepository.findById(sub.getId())).thenReturn(Optional.of(sub));
        when(subcontractorInvoiceRepository.sumGrossAmountBySubcontractId(sub.getId())).thenReturn(BigDecimal.ZERO);
        when(paymentAllocationRepository.sumPaidAmountBySubcontractId(sub.getId())).thenReturn(BigDecimal.ZERO);
        when(paymentAllocationRepository.sumDeductedAmountBySubcontractId(sub.getId())).thenReturn(BigDecimal.ZERO);
    }
}
