package pl.company.settlements.payment.model;

import java.math.BigDecimal;

public interface SubcontractPaymentAmounts {

    Long getSubcontractId();

    BigDecimal getPaidAmount();

    BigDecimal getDeductedAmount();
}