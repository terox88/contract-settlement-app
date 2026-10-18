package pl.company.settlements.invoice.service;

import pl.company.settlements.invoice.model.DeductionReview;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public final class DeductionReviewRules {
    public record Rule(BigDecimal percentage, String basis) { }
    public record Input(Long invoiceId, BigDecimal net, BigDecimal gross, String currency,
                        BigDecimal paid, BigDecimal deducted, boolean hasPayments, Rule security, Rule other) { }
    private DeductionReviewRules() { }
    public static DeductionReview describe(Input input, String confirmedFingerprint, Instant confirmedAt) {
        BigDecimal security = expected(input.security(), input.net(), input.gross());
        BigDecimal other = expected(input.other(), input.net(), input.gross());
        BigDecimal expected = security.add(other), actual = input.deducted();
        ReviewFingerprint hash = new ReviewFingerprint().add("invoice", input.invoiceId(), input.net(), input.gross(), input.currency(),
                input.paid(), actual, input.hasPayments()).add("security", input.security() == null ? null : input.security().percentage(), input.security() == null ? null : input.security().basis())
                .add("other", input.other() == null ? null : input.other().percentage(), input.other() == null ? null : input.other().basis());
        String fingerprint = hash.value();
        boolean currentConfirmation = fingerprint.equals(confirmedFingerprint);
        String status = !input.hasPayments() ? "NOT_STARTED" : expected.compareTo(actual) == 0 ? "MATCHED"
                : currentConfirmation ? "CONFIRMED" : "MISMATCH";
        String label = switch (status) {
            case "NOT_STARTED" -> "Brak zapisanych rozliczeń";
            case "MATCHED" -> "Potrącenia zgodne z umową";
            case "CONFIRMED" -> "Potwierdzono poprawność potrąceń";
            default -> "Potrącenia różnią się od warunków umowy";
        };
        return new DeductionReview(status, label, expected, actual, actual.subtract(expected), security, other, fingerprint,
                currentConfirmation ? confirmedAt : null, confirmedFingerprint != null && !currentConfirmation);
    }
    public static BigDecimal expected(Rule rule, BigDecimal net, BigDecimal gross) {
        if (rule == null || rule.percentage() == null || rule.percentage().signum() == 0) return new BigDecimal("0.00");
        if (rule.percentage().signum() < 0 || rule.percentage().compareTo(new BigDecimal("100")) > 0
                || !("NET".equals(rule.basis()) || "GROSS".equals(rule.basis()))) {
            throw new IllegalArgumentException("Nieprawidłowa reguła potrąceń umowy.");
        }
        return ("NET".equals(rule.basis()) ? net : gross).multiply(rule.percentage()).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }
}
