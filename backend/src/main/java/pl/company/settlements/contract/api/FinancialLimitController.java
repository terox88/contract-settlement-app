package pl.company.settlements.contract.api;

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
import pl.company.settlements.contract.model.FinancialLimitAmountRequest;
import pl.company.settlements.contract.model.FinancialLimitCreateRequest;
import pl.company.settlements.contract.model.FinancialLimitResponse;
import pl.company.settlements.contract.model.FinancialLimitYearRequest;
import pl.company.settlements.contract.service.FinancialLimitService;

@RestController
@RequestMapping("/api/financial-limits")
@RequiredArgsConstructor
public class FinancialLimitController {

    private final FinancialLimitService service;

    @GetMapping("/main-contract/{mainContractId}")
    public FinancialLimitResponse getByMainContractId(
            @PathVariable Long mainContractId
    ) {
        return service.getByMainContractId(
                mainContractId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FinancialLimitResponse create(
            @Valid
            @RequestBody FinancialLimitCreateRequest request
    ) {
        return service.create(request);
    }

    @PostMapping("/main-contract/{mainContractId}/years")
    public FinancialLimitResponse addYear(
            @PathVariable Long mainContractId,
            @Valid
            @RequestBody FinancialLimitYearRequest request
    ) {
        return service.addYear(
                mainContractId,
                request
        );
    }

    @PutMapping("/main-contract/{mainContractId}/years/{year}")
    public FinancialLimitResponse changeAmountForYear(
            @PathVariable Long mainContractId,
            @PathVariable int year,
            @Valid
            @RequestBody FinancialLimitAmountRequest request
    ) {
        return service.changeAmountForYear(
                mainContractId,
                year,
                request
        );
    }
}