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
import pl.company.settlements.contract.domain.FinancialLimit;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.model.FinancialLimitAmountRequest;
import pl.company.settlements.contract.model.FinancialLimitCreateRequest;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialLimitSigningYearServiceTest {
    @Mock private FinancialLimitRepository financialLimitRepository;
    @Mock private MainContractRepository mainContractRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private BudgetCommitmentService budgetCommitmentService;
    @Mock private PaymentAllocationRepository paymentAllocationRepository;
    @Mock private Budget budget;
    @InjectMocks private FinancialLimitService service;
    private MainContract contract;

    @BeforeEach
    void setUp() {
        InvestmentTask task = new InvestmentTask("Z/1", "Zadanie"); task.setId(1L);
        contract = new MainContract(task, new Contractor("Firma", "1234567890"));
        contract.setId(10L); contract.setContractDate(LocalDate.of(2026, 10, 10));
        contract.setInitialValue(new BigDecimal("100.00"));
    }

    @Test
    void initialPlanRejectsHistoricalYearBeforeCheckingItsFreeBudget() {
        lockContext(null);
        rejectsHistorical(() -> service.createInitialPlan(10L, plan(year(2025, "100"))));
        verifyNoInteractions(paymentAllocationRepository, budgetCommitmentService, budget);
    }

    @Test
    void initialPlanAlsoRejectsZeroRowBeforeSigningYear() {
        lockContext(null);
        rejectsHistorical(() -> service.createInitialPlan(10L, plan(year(2025, "0"), year(2026, "100"))));
    }

    @Test
    void separateCreationEndpointCannotIntroduceHistoricalYear() {
        lockContext(null);
        FinancialLimitCreateRequest request = new FinancialLimitCreateRequest();
        request.setMainContractId(10L); request.setYears(List.of(year(2025, "100")));
        rejectsHistorical(() -> service.create(request));
    }

    @Test
    void replacingAnExistingPlanCannotUseHistoricalFunds() {
        lockContext(null);
        rejectsHistorical(() -> service.replacePlan(10L, plan(year(2025, "100"))));
    }

    @Test
    void addingAYearCannotIntroduceEvenAnEmptyHistoricalRow() {
        FinancialLimit existing = new FinancialLimit(contract); existing.addYear(2026, new BigDecimal("100"));
        lockContext(existing);
        rejectsHistorical(() -> service.addYear(10L, year(2025, "0")));
        assertEquals(1, existing.getYears().size());
    }

    @Test
    void changingAnExistingHistoricalAmountCannotPreservePositiveFinancing() {
        FinancialLimit existing = new FinancialLimit(contract); existing.addYear(2025, new BigDecimal("100"));
        lockContext(existing);
        FinancialLimitAmountRequest request = new FinancialLimitAmountRequest(); request.setAmount(new BigDecimal("100"));
        rejectsHistorical(() -> service.changeAmountForYear(10L, 2025, request));
        assertEquals(new BigDecimal("100"), existing.getAmountForYear(2025));
    }

    @Test
    void signingYearAndFutureYearsRemainAvailable() {
        lockContext(null); allowSave(Map.of(2026, new BigDecimal("100")));
        var response = service.createInitialPlan(10L, plan(year(2026, "40"), year(2027, "60")));
        assertEquals(0, response.getTotalAmount().compareTo(new BigDecimal("100")));
        assertEquals(2026, response.getYears().get(0).getYear());
        assertEquals(2027, response.getYears().get(1).getYear());
    }

    @Test
    void historicalMistakeCanBeClearedAndMovedToSigningYear() {
        FinancialLimit existing = new FinancialLimit(contract); existing.addYear(2025, new BigDecimal("100"));
        lockContext(existing); allowSave(Map.of(2025, new BigDecimal("100")));
        var response = service.replacePlan(10L, plan(year(2025, "0"), year(2026, "100")));
        assertEquals(0, response.getTotalAmount().compareTo(new BigDecimal("100")));
        assertEquals(0, existing.getAmountForYear(2025).signum());
        assertEquals(0, existing.getAmountForYear(2026).compareTo(new BigDecimal("100")));
    }

    private void lockContext(FinancialLimit existing) {
        when(mainContractRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(contract));
        when(budgetRepository.findByInvestmentTaskIdForUpdate(1L)).thenReturn(Optional.of(budget));
        when(financialLimitRepository.findByMainContract_Id(10L)).thenReturn(Optional.ofNullable(existing));
    }

    private void allowSave(Map<Integer, BigDecimal> commitments) {
        BudgetYear first = mock(BudgetYear.class), second = mock(BudgetYear.class);
        when(first.getYear()).thenReturn(2026); when(first.getAmount()).thenReturn(new BigDecimal("100"));
        when(second.getYear()).thenReturn(2027); when(second.getAmount()).thenReturn(new BigDecimal("100"));
        List<BudgetYear> years = List.of(first, second);
        when(budget.getYears()).thenReturn(years);
        when(paymentAllocationRepository.sumMainContractPaymentsByDate(10L)).thenReturn(List.of());
        when(budgetCommitmentService.calculateByYear(1L)).thenReturn(commitments);
        when(financialLimitRepository.saveAndFlush(any(FinancialLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void rejectsHistorical(Runnable action) {
        var error = assertThrows(IllegalArgumentException.class, action::run);
        assertEquals("Limit na rok 2025 nie może być wcześniejszy niż rok zawarcia umowy (2026).", error.getMessage());
        verify(financialLimitRepository, never()).saveAndFlush(any(FinancialLimit.class));
    }

    private static FinancialLimitYearRequest year(int year, String amount) {
        FinancialLimitYearRequest request = new FinancialLimitYearRequest();
        request.setYear(year); request.setAmount(new BigDecimal(amount)); return request;
    }

    private static FinancialLimitPlanRequest plan(FinancialLimitYearRequest... years) {
        return new FinancialLimitPlanRequest(List.of(years));
    }
}
