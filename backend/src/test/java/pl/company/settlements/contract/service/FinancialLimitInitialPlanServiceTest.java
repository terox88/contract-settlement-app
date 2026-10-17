package pl.company.settlements.contract.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.budget.domain.Budget;
import pl.company.settlements.budget.domain.BudgetYear;
import pl.company.settlements.budget.repository.BudgetRepository;
import pl.company.settlements.budget.service.BudgetCommitmentService;
import pl.company.settlements.common.error.ConflictException;
import pl.company.settlements.contract.domain.FinancialLimit;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.model.FinancialLimitPlanRequest;
import pl.company.settlements.contract.model.FinancialLimitYearRequest;
import pl.company.settlements.contract.repository.FinancialLimitRepository;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;
import pl.company.settlements.task.domain.InvestmentTask;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialLimitInitialPlanServiceTest {
    @Mock private FinancialLimitRepository financialLimitRepository;
    @Mock private MainContractRepository mainContractRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private BudgetCommitmentService budgetCommitmentService;
    @Mock private PaymentAllocationRepository paymentAllocationRepository;
    @InjectMocks private FinancialLimitService service;

    @BeforeEach
    void setUp() {
        InvestmentTask task = mock(InvestmentTask.class);
        when(task.getId()).thenReturn(1L);
        MainContract contract = new MainContract(task, new Contractor("Firma", "1234567890"));
        contract.setId(10L);
        contract.setContractNumber("UM/2026/01");
        contract.setContractDate(LocalDate.of(2026, 10, 14));
        contract.setInitialValue(new BigDecimal("100.00"));
        when(mainContractRepository.findById(10L)).thenReturn(Optional.of(contract));
        when(financialLimitRepository.findByMainContract_Id(10L)).thenReturn(Optional.empty());
        Budget budget = mock(Budget.class);
        // Najpierw zakończ konfigurację mocków lat, dopiero potem rozpocznij stubbing budżetu.
        List<BudgetYear> years = List.of(budgetYear(2026, "100.00"), budgetYear(2027, "60.00"));
        when(budget.getYears()).thenReturn(years);
        when(budgetRepository.findByInvestmentTaskIdForUpdate(1L)).thenReturn(Optional.of(budget));
        when(paymentAllocationRepository.sumMainContractPaymentsByDate(10L)).thenReturn(List.of());
        // Zapytanie widzi 60 innych umów i 100 domyślnego zaangażowania właśnie zapisanej umowy.
        when(budgetCommitmentService.calculateByYear(1L)).thenReturn(Map.of(2026, new BigDecimal("160.00")));
    }

    @Test
    void rejectsNewContractExceedingFreeBudgetEvenInSigningYear() {
        assertThrows(ConflictException.class, () -> service.createInitialPlan(10L, plan("100.00", "0.00")));
        verify(financialLimitRepository, never()).saveAndFlush(any(FinancialLimit.class));
    }

    @Test
    void replacesOwnImplicitCommitmentWithInitialMultiYearPlan() {
        when(financialLimitRepository.saveAndFlush(any(FinancialLimit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createInitialPlan(10L, plan("40.00", "60.00"));

        assertEquals(new BigDecimal("100.00"), response.getTotalAmount());
        assertEquals(new BigDecimal("40.00"), response.getYears().get(0).getAmount());
        assertEquals(new BigDecimal("60.00"), response.getYears().get(1).getAmount());
        verify(budgetRepository).findByInvestmentTaskIdForUpdate(1L);
    }

    @Test
    void preservesExistingEditRuleAllowingReductionOfOvercommitment() {
        when(financialLimitRepository.saveAndFlush(any(FinancialLimit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Istniejąca umowa: 160 -> 140 w roku 2026 nadal przekracza 100, ale poprawia sytuację.
        var response = service.replacePlan(10L, plan("80.00", "20.00"));

        assertEquals(new BigDecimal("80.00"), response.getYears().get(0).getAmount());
        assertEquals(new BigDecimal("20.00"), response.getYears().get(1).getAmount());
    }

    private BudgetYear budgetYear(int year, String amount) {
        BudgetYear row = mock(BudgetYear.class);
        when(row.getYear()).thenReturn(year);
        when(row.getAmount()).thenReturn(new BigDecimal(amount));
        return row;
    }

    private FinancialLimitPlanRequest plan(String first, String second) {
        FinancialLimitYearRequest a = new FinancialLimitYearRequest();
        a.setYear(2026);
        a.setAmount(new BigDecimal(first));
        FinancialLimitYearRequest b = new FinancialLimitYearRequest();
        b.setYear(2027);
        b.setAmount(new BigDecimal(second));
        return new FinancialLimitPlanRequest(List.of(a, b));
    }
}
