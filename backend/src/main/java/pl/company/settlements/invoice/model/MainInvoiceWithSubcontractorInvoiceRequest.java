package pl.company.settlements.invoice.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class MainInvoiceWithSubcontractorInvoiceRequest {
    @NotNull
    @Positive
    private Long mainContractorInvoiceId;

    @NotNull
    @Valid
    private SubcontractorInvoiceCreateRequest invoice;

    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 2)
    private BigDecimal settledAmount;
}
