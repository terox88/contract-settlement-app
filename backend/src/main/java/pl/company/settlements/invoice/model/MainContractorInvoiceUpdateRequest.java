package pl.company.settlements.invoice.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class MainContractorInvoiceUpdateRequest {

    @NotBlank
    @Size(max = 255)
    private String invoiceNumber;

    @NotNull
    private LocalDate issueDate;

    private LocalDate saleDate;

    private LocalDate dueDate;

    @NotNull
    @PositiveOrZero
    private BigDecimal netAmount;

    @NotNull
    @PositiveOrZero
    private BigDecimal vatAmount;

    @NotNull
    @Positive
    private BigDecimal grossAmount;

    @PositiveOrZero
    @DecimalMax("100.00")
    private BigDecimal vatRate;

    @NotBlank
    @Size(min = 3, max = 3)
    private String currency = "PLN";

    @Size(max = 1000)
    private String description;
}