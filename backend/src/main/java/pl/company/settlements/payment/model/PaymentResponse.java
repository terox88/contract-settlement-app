package pl.company.settlements.payment.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class PaymentResponse {

    private Long id;
    private Long recipientContractorId;
    private Long payerContractorId;
    private Boolean mainInvoicePayment;
    private boolean mixedRecipients;

    private LocalDate paymentDate;

    private BigDecimal amount;

    private String currency;

    private String bankReference;

    private String description;

    private BigDecimal allocatedAmount;

    private BigDecimal unallocatedAmount;

    private boolean fullyAllocated;

    private int allocationCount;
}