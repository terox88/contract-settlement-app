package pl.company.settlements.contract.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ContractPenaltyUpdateRequest {

    @NotNull
    private LocalDate imposedDate;

    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 2)
    private BigDecimal amount;

    @Size(max = 1000)
    private String description;
}