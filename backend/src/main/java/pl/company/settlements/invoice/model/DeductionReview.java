package pl.company.settlements.invoice.model;

import java.math.BigDecimal;
import java.time.Instant;

public record DeductionReview(String status, String label, BigDecimal expectedAmount,
                              BigDecimal actualAmount, BigDecimal difference,
                              BigDecimal securityAmount, BigDecimal otherAmount,
                              String fingerprint, Instant confirmedAt, boolean confirmationOutdated) { }
