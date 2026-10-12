package pl.company.settlements.payment.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PaymentAllocationResponse {

    private Long id;

    private Long paymentId;
    private BigDecimal paymentAmount;
    private String paymentCurrency;

    private BigDecimal allocatedAmount;
    private BigDecimal deductedAmount;

    private BigDecimal totalAllocatedFromPayment;
    private BigDecimal remainingPaymentAmount;

    private Long subcontractorInvoiceId;
    private String subcontractorInvoiceNumber;

    private Long subcontractId;

    private Long subcontractorId;
    private String subcontractorName;

    private BigDecimal invoiceGrossAmount;

    private BigDecimal invoicePaidAmount;
    private BigDecimal invoiceDeductedAmount;
    private BigDecimal invoiceRemainingAmount;

    private boolean invoiceSettled;

    private boolean invoiceAmountExceeded;

    private boolean hasStatement;

    private Long mainContractorInvoiceId;
    private String mainContractorInvoiceNumber;

    private Long mainContractId;
    private String mainContractNumber;

    private Long investmentTaskId;

    private Long mainContractorId;
    private String mainContractorName;
}