package pl.company.settlements.invoice.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class MainInvoiceSettlementCreateRequest {

    @Positive
    private Long mainContractorInvoiceId;

    @Positive
    private Long parentSubcontractorInvoiceId;

    @AssertTrue(message = "Wskaż dokładnie jedną fakturę zleceniodawcy.")
    public boolean isParentSelectionValid() {
        return (mainContractorInvoiceId == null) != (parentSubcontractorInvoiceId == null);
    }

    @NotNull
    private Long subcontractorInvoiceId;

    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 2)
    private BigDecimal settledAmount;

}