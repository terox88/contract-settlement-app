package pl.company.settlements.budget.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.company.settlements.budget.model.BudgetAmountRequest;
import pl.company.settlements.budget.model.BudgetCreateRequest;
import pl.company.settlements.budget.model.BudgetResponse;
import pl.company.settlements.budget.model.BudgetYearRequest;
import pl.company.settlements.budget.service.BudgetService;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService service;

    @GetMapping("/investment-task/{investmentTaskId}")
    public BudgetResponse getByInvestmentTaskId(
            @PathVariable Long investmentTaskId
    ) {
        return service.getByInvestmentTaskId(
                investmentTaskId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetResponse create(
            @Valid
            @RequestBody BudgetCreateRequest request
    ) {
        return service.create(request);
    }

    @PostMapping("/investment-task/{investmentTaskId}/years")
    public BudgetResponse addYear(
            @PathVariable Long investmentTaskId,
            @Valid
            @RequestBody BudgetYearRequest request
    ) {
        return service.addYear(
                investmentTaskId,
                request
        );
    }

    @PutMapping(
            "/investment-task/{investmentTaskId}/years/{year}"
    )
    public BudgetResponse changeAmountForYear(
            @PathVariable Long investmentTaskId,
            @PathVariable int year,
            @Valid
            @RequestBody BudgetAmountRequest request
    ) {
        return service.changeAmountForYear(
                investmentTaskId,
                year,
                request
        );
    }
}