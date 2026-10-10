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

import java.time.Year;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final InvestmentTaskRepository investmentTaskRepository;

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

    private BudgetResponse toResponse(Budget budget) {

        int currentYear =
                Year.now().getValue();

        return BudgetResponse.builder()
                .id(
                        budget.getId()
                )
                .investmentTaskId(
                        budget.getInvestmentTask()
                                .getId()
                )
                .totalAmount(
                        budget.getTotalAmount()
                )
                .currentYear(
                        currentYear
                )
                .currentYearAmount(
                        budget.getAmountForYear(
                                currentYear
                        )
                )
                .years(
                        budget.getYears()
                                .stream()
                                .map(this::toYearResponse)
                                .toList()
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