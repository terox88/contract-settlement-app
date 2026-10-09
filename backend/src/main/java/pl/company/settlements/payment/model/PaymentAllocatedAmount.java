package pl.company.settlements.payment.model;

import java.math.BigDecimal;

public interface PaymentAllocatedAmount {

    Long getPaymentId();

    BigDecimal getAllocatedAmount();
}