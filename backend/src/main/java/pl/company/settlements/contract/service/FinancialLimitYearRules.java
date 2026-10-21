package pl.company.settlements.contract.service;

import pl.company.settlements.contract.model.FinancialLimitYearRequest;

import java.time.LocalDate;
import java.util.List;

public final class FinancialLimitYearRules {
    private FinancialLimitYearRules() {}

    public static void validateYear(LocalDate contractDate, int year) {
        if (contractDate == null) {
            throw new IllegalArgumentException("Uzupełnij datę zawarcia umowy przed ustaleniem limitów finansowych.");
        }
        if (year < contractDate.getYear()) {
            throw new IllegalArgumentException("Limit na rok " + year
                    + " nie może być wcześniejszy niż rok zawarcia umowy (" + contractDate.getYear() + ").");
        }
    }

    public static void validateInitialYears(LocalDate contractDate, List<FinancialLimitYearRequest> years) {
        if (years == null) return;
        for (FinancialLimitYearRequest row : years) {
            if (row == null) throw new IllegalArgumentException("Rok limitu finansowego nie może być pusty.");
            validateYear(contractDate, row.getYear());
        }
    }
}
