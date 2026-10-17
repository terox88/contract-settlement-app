package pl.company.settlements.payment.service;

import java.math.BigDecimal;

public class PaymentRulesCases {
    public static void main(String[] args) {
        runAll(); System.out.println("PaymentRules: 13 scenariuszy OK");
    }
    static void runAll() {
        PaymentRules.money(n("0.01"), true);
        reject(() -> PaymentRules.money(n("0"), true));
        reject(() -> PaymentRules.money(n("-1"), false));
        reject(() -> PaymentRules.money(n("0.001"), true));
        reject(() -> PaymentRules.money(n("10000000000000"), true));
        PaymentRules.money(n("9999999999999.99"), true);
        PaymentRules.capacity(n("100"), n("60"), n("40"));
        reject(() -> PaymentRules.capacity(n("100"), n("60"), n("40.01")));
        PaymentRules.allocation(n("95"), n("5"), false);
        PaymentRules.allocation(n("100"), n("10"), false); // nadpłata faktury pozostaje ostrzeżeniem w odpowiedzi
        reject(() -> PaymentRules.allocation(n("95"), n("5"), true));
        reject(() -> PaymentRules.allocation(n("0"), n("5"), false));
        PaymentRules.allocation(n("100"), BigDecimal.ZERO, true);
    }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Oczekiwano odrzucenia kwoty");
    }
}
