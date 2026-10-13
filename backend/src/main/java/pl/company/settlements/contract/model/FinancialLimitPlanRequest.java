package pl.company.settlements.contract.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Complete replacement of the financial plan, not a single-year patch. */
public record FinancialLimitPlanRequest(
        @NotEmpty List<@Valid FinancialLimitYearRequest> years
) {
}
