package pl.company.settlements.contract.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class FinancialLimitCreateRequest {

    @NotNull
    private Long mainContractId;

    @Valid
    @NotEmpty
    private List<FinancialLimitYearRequest> years =
            new ArrayList<>();
}