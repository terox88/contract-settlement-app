package pl.company.settlements.contract.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.RemunerationType;
import pl.company.settlements.contractor.model.ContractorCreateRequest;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class MainContractCreateRequest {

    @NotNull
    private Long investmentTaskId;

    @Positive
    private Long mainContractorId;

    @Valid
    private ContractorCreateRequest newMainContractor;

    @NotBlank
    @Size(max = 255)
    private String contractNumber;

    @NotNull
    private LocalDate contractDate;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 13, fraction = 2)
    private BigDecimal initialValue;

    @NotNull
    @PositiveOrZero
    @DecimalMax("100.00")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal vatRate;

    private LocalDate initialStartDate;

    private LocalDate initialEndDate;

    @NotNull
    private ContractType contractType;

    @NotNull
    private RemunerationType remunerationType;

    @Size(max = 500)
    private String shortDescription;

    @AssertTrue(message = "Wskaż istniejącego wykonawcę albo podaj dane nowego wykonawcy.")
    public boolean isMainContractorSelectionValid() {
        return (mainContractorId == null) != (newMainContractor == null);
    }
}