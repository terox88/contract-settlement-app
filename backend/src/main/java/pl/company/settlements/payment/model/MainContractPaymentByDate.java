package pl.company.settlements.payment.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Interface-based projection used to validate limits against actual payments. */
public interface MainContractPaymentByDate {
    LocalDate getPaymentDate();
    String getCurrency();
    BigDecimal getPaidAmount();
}
