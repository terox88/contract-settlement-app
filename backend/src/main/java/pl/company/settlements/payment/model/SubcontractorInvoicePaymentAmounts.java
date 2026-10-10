package pl.company.settlements.payment.model;

import java.math.BigDecimal;

public interface SubcontractorInvoicePaymentAmounts {

    Long getSubcontractorInvoiceId();

    BigDecimal getPaidAmount();

    BigDecimal getDeductedAmount();
}