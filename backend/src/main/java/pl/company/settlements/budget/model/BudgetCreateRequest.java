package pl.company.settlements.budget.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class BudgetCreateRequest {

    @NotNull
    private Long investmentTaskId;

    @Valid
    @NotEmpty
    private List<BudgetYearRequest> years = new ArrayList<>();
}