package pl.company.settlements.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PaymentRules {
    private static final BigDecimal MAX = new BigDecimal("9999999999999.99");
    private PaymentRules() { }

    public static void money(BigDecimal value, boolean positive) {
        if (value == null || value.signum() < 0 || (positive && value.signum() == 0) || value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException(positive ? "Podaj dodatnią kwotę w dopuszczalnym zakresie." : "Potrącenie musi być nieujemne i mieścić się w dopuszczalnym zakresie.");
        }
        try { value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Kwota może mieć najwyżej dwa miejsca po przecinku."); }
    }

    public static void capacity(BigDecimal payment, BigDecimal alreadyAllocated, BigDecimal amount) {
        money(amount, true);
        if (alreadyAllocated.add(amount).compareTo(payment) > 0) {
            throw new IllegalArgumentException("Suma kwot przypisanych do faktur przekracza wartość przelewu. Odśwież dane i sprawdź dostępną kwotę.");
        }
    }

    public static void allocation(BigDecimal paid, BigDecimal deducted, boolean mainInvoice) {
        money(paid, true); money(deducted, false);
        if (mainInvoice && deducted.signum() != 0) {
            throw new IllegalArgumentException("Potrącenia w tym module dotyczą wyłącznie faktur podwykonawców.");
        }
    }
}
