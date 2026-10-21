package pl.company.settlements.contract.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import pl.company.settlements.payment.model.MainContractPaymentByDate;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinancialLimitServiceTest {

    @Mock private FinancialLimitRepository financialLimitRepository;
    @Mock private MainContractRepository mainContractRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private BudgetCommitmentService budgetCommitmentService;
    @Mock private PaymentAllocationRepository paymentAllocationRepository;
    @Mock private MainContract contract;
    @Mock private InvestmentTask task;
    @Mock private Budget budget;

    @InjectMocks private FinancialLimitService service;

    private void setupCommon(FinancialLimit existing) {
        when(mainContractRepository.findByIdForUpdate(38L)).thenReturn(Optional.of(contract));
        when(contract.getId()).thenReturn(38L);
        when(contract.getInvestmentTask()).thenReturn(task);
        when(task.getId()).thenReturn(36L);
        when(contract.getContractDate()).thenReturn(LocalDate.of(2026, 1, 10));
        when(contract.getCurrentValue()).thenReturn(new BigDecimal("600000.00"));
        when(budgetRepository.findByInvestmentTaskIdForUpdate(36L)).thenReturn(Optional.of(budget));
        when(financialLimitRepository.findByMainContract_Id(38L)).thenReturn(Optional.ofNullable(existing));
        when(paymentAllocationRepository.sumMainContractPaymentsByDate(38L)).thenReturn(List.of());
        when(budgetCommitmentService.calculateByYear(36L))
                .thenReturn(Map.of(2026, new BigDecimal("600000.00")));
    }

    private static BudgetYear budgetYear(int year, String amount) {
        BudgetYear result = mock(BudgetYear.class);
        when(result.getYear()).thenReturn(year);
        when(result.getAmount()).thenReturn(new BigDecimal(amount));
        return result;
    }

    private static FinancialLimitYearRequest year(int year, String amount) {
        FinancialLimitYearRequest result = mock(FinancialLimitYearRequest.class);
        when(result.getYear()).thenReturn(year);
        when(result.getAmount()).thenReturn(new BigDecimal(amount));
        return result;
    }

    private static FinancialLimitPlanRequest plan(
            FinancialLimitYearRequest... years
    ) {
        return new FinancialLimitPlanRequest(List.of(years));
    }

    @Test
    void createsPlanAndReleasesImplicitCommitmentFromContractYear() {
        setupCommon(null);
        List<BudgetYear> budgetYears = List.of(
                budgetYear(2026, "500000.00"),
                budgetYear(2027, "400000.00")
        );
        when(budget.getYears()).thenReturn(budgetYears);
        FinancialLimit created = new FinancialLimit(contract);
        when(contract.getOrCreateFinancialLimit()).thenReturn(created);
        when(financialLimitRepository.saveAndFlush(any(FinancialLimit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.replacePlan(38L, plan(
                year(2026, "400000.00"),
                year(2027, "200000.00")
        ));

        assertEquals(0, created.getAmountForYear(2026)
                .compareTo(new BigDecimal("400000.00")));
        assertEquals(0, created.getAmountForYear(2027)
                .compareTo(new BigDecimal("200000.00")));
        verify(financialLimitRepository).saveAndFlush(created);
    }

    @Test
    void refusesPlanThatExceedsAnnualBudget() {
        setupCommon(null);
        List<BudgetYear> budgetYears = List.of(
                budgetYear(2026, "500000.00"),
                budgetYear(2027, "100000.00")
        );
        when(budget.getYears()).thenReturn(budgetYears);

        assertThrows(ConflictException.class, () -> service.replacePlan(38L,
                plan(year(2026, "400000.00"), year(2027, "200000.00"))));
        verify(financialLimitRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesToSetAnnualLimitBelowAlreadyPaidAmount() {
        FinancialLimit existing = new FinancialLimit(contract);
        existing.addYear(2026, new BigDecimal("300000.00"));
        existing.addYear(2027, new BigDecimal("300000.00"));
        setupCommon(existing);
        List<BudgetYear> budgetYears = List.of(
                budgetYear(2026, "600000.00"),
                budgetYear(2027, "600000.00")
        );
        when(budget.getYears()).thenReturn(budgetYears);
        MainContractPaymentByDate payment = mock(MainContractPaymentByDate.class);
        when(payment.getCurrency()).thenReturn("PLN");
        when(payment.getPaymentDate()).thenReturn(LocalDate.of(2026, 8, 20));
        when(payment.getPaidAmount()).thenReturn(new BigDecimal("250000.00"));
        when(paymentAllocationRepository.sumMainContractPaymentsByDate(38L))
                .thenReturn(List.of(payment));

        assertThrows(ConflictException.class, () -> service.replacePlan(38L,
                plan(year(2026, "200000.00"), year(2027, "400000.00"))));
        verify(financialLimitRepository, never()).saveAndFlush(any());
        assertEquals(0, existing.getAmountForYear(2026)
                .compareTo(new BigDecimal("300000.00")));
    }

    @Test
    void refusesPlanWithWrongTotal() {
        setupCommon(null);
        assertThrows(IllegalArgumentException.class, () -> service.replacePlan(38L,
                plan(year(2026, "200000.00"))));
        verify(financialLimitRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatesExistingYearsWithoutRecreatingTheirEntities() {
        FinancialLimit existing = new FinancialLimit(contract);
        existing.addYear(2026, new BigDecimal("300000.00"));
        existing.addYear(2027, new BigDecimal("300000.00"));
        var originalYear = existing.getYears().get(0);
        setupCommon(existing);
        when(budgetCommitmentService.calculateByYear(36L))
                .thenReturn(Map.of(2026, new BigDecimal("300000.00"),
                        2027, new BigDecimal("300000.00")));
        List<BudgetYear> budgetYears = List.of(
                budgetYear(2026, "500000.00"),
                budgetYear(2027, "500000.00")
        );
        when(budget.getYears()).thenReturn(budgetYears);
        when(financialLimitRepository.saveAndFlush(any(FinancialLimit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.replacePlan(38L, plan(
                year(2026, "250000.00"), year(2027, "350000.00")
        ));

        assertEquals(0, existing.getAmountForYear(2026)
                .compareTo(new BigDecimal("250000.00")));
        org.junit.jupiter.api.Assertions.assertSame(originalYear, existing.getYears().get(0));
    }
}
