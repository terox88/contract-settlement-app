package pl.company.settlements.budget.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.budget.domain.Budget;
import pl.company.settlements.budget.domain.BudgetYear;
import pl.company.settlements.budget.model.*;
import pl.company.settlements.budget.repository.BudgetRepository;
import pl.company.settlements.task.domain.InvestmentTask;
import pl.company.settlements.task.repository.InvestmentTaskRepository;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final InvestmentTaskRepository investmentTaskRepository;
    private final PaymentAllocationRepository
            paymentAllocationRepository;
    private final BudgetCommitmentService budgetCommitmentService;

    public BudgetResponse getByInvestmentTaskId(Long investmentTaskId) {
        return toResponse(
                findEntityByInvestmentTaskId(investmentTaskId)
        );
    }

    @Transactional
    public BudgetResponse create(BudgetCreateRequest request) {

        Long investmentTaskId = request.getInvestmentTaskId();

        if (budgetRepository.existsByInvestmentTask_Id(investmentTaskId)) {
            throw new IllegalArgumentException(
                    "Budget for investment task with id "
                            + investmentTaskId
                            + " already exists"
            );
        }

        InvestmentTask investmentTask = investmentTaskRepository
                .findById(investmentTaskId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Investment task with id "
                                + investmentTaskId
                                + " does not exist"
                ));

        Budget budget = new Budget(investmentTask);

        for (BudgetYearRequest year : request.getYears()) {
            budget.addYear(
                    year.getYear(),
                    year.getAmount()
            );
        }

        investmentTask.setBudget(budget);

        Budget savedBudget = budgetRepository.save(budget);

        return toResponse(savedBudget);
    }

    @Transactional
    public BudgetResponse addYear(
            Long investmentTaskId,
            BudgetYearRequest request
    ) {
        Budget budget =
                findEntityByInvestmentTaskId(investmentTaskId);

        budget.addYear(
                request.getYear(),
                request.getAmount()
        );

        return toResponse(budget);
    }

    @Transactional
    public BudgetResponse changeAmountForYear(
            Long investmentTaskId,
            int year,
            BudgetAmountRequest request
    ) {
        Budget budget =
                findEntityByInvestmentTaskId(investmentTaskId);

        budget.changeAmountForYear(
                year,
                request.getAmount()
        );

        return toResponse(budget);
    }

    private Budget findEntityByInvestmentTaskId(
            Long investmentTaskId
    ) {
        return budgetRepository
                .findByInvestmentTask_Id(investmentTaskId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Budget for investment task with id "
                                + investmentTaskId
                                + " does not exist"
                ));
    }

    private BudgetResponse toResponse(
            Budget budget
    ) {

        Long investmentTaskId =
                budget.getInvestmentTask()
                        .getId();

        int currentYear =
                Year.now().getValue();

        LocalDate yearStart =
                LocalDate.of(
                        currentYear,
                        1,
                        1
                );

        LocalDate yearEnd =
                LocalDate.of(
                        currentYear,
                        12,
                        31
                );

        /*
         * =========================================================
         * BUDŻET CAŁKOWITY
         * =========================================================
         */

        BigDecimal totalAmount =
                budget.getTotalAmount();

        BigDecimal paidAmount =
                paymentAllocationRepository
                        .sumMainContractPaymentsByInvestmentTaskId(
                                investmentTaskId
                        );

        BigDecimal remainingAmount =
                totalAmount.subtract(
                        paidAmount
                );

        /*
         * =========================================================
         * BUDŻET BIEŻĄCEGO ROKU
         * =========================================================
         */

        BigDecimal currentYearAmount =
                budget.getAmountForYear(
                        currentYear
                );

        BigDecimal currentYearPaidAmount =
                paymentAllocationRepository
                        .sumMainContractPaymentsByInvestmentTaskIdAndDateBetween(
                                investmentTaskId,
                                yearStart,
                                yearEnd
                        );

        BigDecimal currentYearRemainingAmount =
                currentYearAmount.subtract(
                        currentYearPaidAmount
                );

        /*
         * =========================================================
         * ZAANGAŻOWANIE FINANSOWE
         * =========================================================
         *
         * Dla umów bez limitów:
         * cała aktualna wartość umowy przypisana
         * jest do roku zawarcia umowy.
         *
         * Dla umów z limitami:
         * zaangażowanie wynika z limitów
         * przypisanych do poszczególnych lat.
         */

        Map<Integer, BigDecimal> commitments =
                budgetCommitmentService
                        .calculateByYear(
                                investmentTaskId
                        );

        BigDecimal currentYearCommittedAmount =
                commitments.getOrDefault(
                        currentYear,
                        BigDecimal.ZERO
                );

        /*
         * Wolne środki to budżet pomniejszony
         * o zaangażowanie finansowe.
         *
         * Nie odejmujemy tutaj ponownie płatności,
         * ponieważ są objęte zaangażowaniem.
         */

        BigDecimal currentYearAvailableAmount =
                currentYearAmount.subtract(
                        currentYearCommittedAmount
                );

        /*
         * =========================================================
         * BUDŻET W POSZCZEGÓLNYCH LATACH
         * =========================================================
         */

        List<BudgetYearResponse> years =
                budget.getYears()
                        .stream()
                        .map(year -> {

                            BigDecimal committedAmount =
                                    commitments.getOrDefault(
                                            year.getYear(),
                                            BigDecimal.ZERO
                                    );

                            BigDecimal availableAmount =
                                    year.getAmount()
                                            .subtract(
                                                    committedAmount
                                            );

                            return BudgetYearResponse.builder()
                                    .id(
                                            year.getId()
                                    )
                                    .year(
                                            year.getYear()
                                    )
                                    .amount(
                                            year.getAmount()
                                    )
                                    .committedAmount(
                                            committedAmount
                                    )
                                    .availableAmount(
                                            availableAmount
                                    )
                                    .build();
                        })
                        .toList();

        /*
         * =========================================================
         * RESPONSE
         * =========================================================
         */

        return BudgetResponse.builder()
                .id(
                        budget.getId()
                )
                .investmentTaskId(
                        investmentTaskId
                )

                .years(
                        years
                )

                /*
                 * Budżet całkowity
                 */
                .totalAmount(
                        totalAmount
                )
                .paidAmount(
                        paidAmount
                )
                .remainingAmount(
                        remainingAmount
                )

                /*
                 * Budżet bieżącego roku
                 */
                .currentYear(
                        currentYear
                )
                .currentYearAmount(
                        currentYearAmount
                )
                .currentYearPaidAmount(
                        currentYearPaidAmount
                )
                .currentYearRemainingAmount(
                        currentYearRemainingAmount
                )

                /*
                 * Zaangażowanie i wolne środki
                 */
                .currentYearCommittedAmount(
                        currentYearCommittedAmount
                )
                .currentYearAvailableAmount(
                        currentYearAvailableAmount
                )

                .build();
    }

    private BudgetYearResponse toYearResponse(
            BudgetYear budgetYear
    ) {
        return BudgetYearResponse.builder()
                .id(budgetYear.getId())
                .year(budgetYear.getYear())
                .amount(budgetYear.getAmount())
                .build();
    }
}