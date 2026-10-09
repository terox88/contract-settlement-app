package pl.company.settlements.contract.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ContractAmendmentCreateRequest {

    @NotNull
    private Long contractId;

    @NotBlank
    @Size(max = 255)
    private String amendmentNumber;

    @NotNull
    private LocalDate amendmentDate;

    @NotNull
    private LocalDate effectiveDate;

    private BigDecimal valueChange;

    private LocalDate newEndDate;

    @Size(max = 2000)
    private String description;
}