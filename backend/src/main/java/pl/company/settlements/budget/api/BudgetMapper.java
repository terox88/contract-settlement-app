package pl.company.settlements.budget.api;

import pl.company.settlements.budget.domain.Budget;

import java.time.Year;

public final class BudgetMapper {

    private BudgetMapper() {
    }

    public static BudgetResponse toResponse(Budget budget) {
        int currentYear = Year.now().getValue();

        var years = budget.getYears().stream()
                .map(year -> new BudgetYearResponse(year.getYear(), year.getAmount()))
                .toList();

        return new BudgetResponse(
                budget.getTotalAmount(),
                currentYear,
                budget.getAmountForYear(currentYear),
                years
        );
    }
}
