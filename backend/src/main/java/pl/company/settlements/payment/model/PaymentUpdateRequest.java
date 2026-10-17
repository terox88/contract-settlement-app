package pl.company.settlements.payment.model;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class PaymentUpdateRequest {

    @NotNull
    private LocalDate paymentDate;

    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 2)
    private BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3)
    @Pattern(regexp = "[A-Za-z]{3}")
    private String currency;

    @Size(max = 255)
    private String bankReference;

    @Size(max = 1000)
    private String description;
}