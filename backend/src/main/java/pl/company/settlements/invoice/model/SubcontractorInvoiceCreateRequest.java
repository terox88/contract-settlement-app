package pl.company.settlements.invoice.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SubcontractorInvoiceCreateRequest {

    @NotNull
    private Long subcontractId;

    @NotBlank
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

    @NotBlank
    private String currency = "PLN";

    @NotEmpty
    private List<String> vatRates =
            new ArrayList<>();

    private String description;
}