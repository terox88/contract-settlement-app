package pl.company.settlements.invoice.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(max = 255)
    private String invoiceNumber;

    @NotNull
    private LocalDate issueDate;

    private LocalDate saleDate;

    private LocalDate dueDate;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 13, fraction = 2)
    private BigDecimal netAmount;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 13, fraction = 2)
    private BigDecimal vatAmount;

    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 2)
    private BigDecimal grossAmount;

    @NotBlank
    @Pattern(regexp = "[A-Za-z]{3}")
    private String currency = "PLN";

    @NotEmpty
    private List<@NotBlank @Size(max = 20) String> vatRates =
            new ArrayList<>();

    @Size(max = 1000)
    private String description;
}