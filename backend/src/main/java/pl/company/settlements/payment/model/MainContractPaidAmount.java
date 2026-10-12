package pl.company.settlements.payment.model;

import java.math.BigDecimal;

public interface MainContractPaidAmount {

    Long getMainContractId();

    BigDecimal getPaidAmount();
}