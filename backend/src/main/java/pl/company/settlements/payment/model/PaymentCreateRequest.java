package pl.company.settlements.payment.model;

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
public class PaymentCreateRequest {

    @NotNull
    private LocalDate paymentDate;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3)
    private String currency = "PLN";

    @Size(max = 255)
    private String bankReference;

    @Size(max = 1000)
    private String description;
}