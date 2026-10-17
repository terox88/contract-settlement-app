package pl.company.settlements.invoice.service;

import java.math.BigDecimal;

/** Scenariusze działają także samodzielnie, bez kontekstu Spring. */
public class InvoiceAssignmentRulesCases {
    public static void main(String[] args) {
        runAll();
        System.out.println("InvoiceAssignmentRules: 13 scenariuszy OK");
    }

    static void runAll() {
        InvoiceAssignmentRules.validateAmount(n("100"), n("60"), n("40"));
        reject(() -> InvoiceAssignmentRules.validateAmount(n("100"), n("60"), n("40.01")));
        InvoiceAssignmentRules.validateAmount(n("100"), n("40"), n("60")); // wykluczenie starego przypisania przy edycji
        reject(() -> InvoiceAssignmentRules.validateAmount(n("100"), n("0"), n("0")));
        reject(() -> InvoiceAssignmentRules.validateAmount(n("100"), n("0"), n("-1")));
        reject(() -> InvoiceAssignmentRules.validateAmount(n("100"), n("0"), n("1.001")));
        InvoiceAssignmentRules.validateContext(7L, 7L, "PLN", "pln");
        reject(() -> InvoiceAssignmentRules.validateContext(7L, 8L, "PLN", "PLN"));
        reject(() -> InvoiceAssignmentRules.validateContext(7L, 7L, "PLN", "EUR"));
        reject(() -> InvoiceAssignmentRules.validateInvoiceChange(n("99.99"), n("100"), "PLN", "PLN"));
        reject(() -> InvoiceAssignmentRules.validateInvoiceChange(n("100"), n("60"), "PLN", "EUR"));
        InvoiceAssignmentRules.validateInvoiceChange(n("100"), n("100"), "PLN", "pln");
        InvoiceAssignmentRules.validateInvoiceChange(n("100"), n("0"), "PLN", "EUR");
    }

    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Oczekiwano odrzucenia nieprawidłowych danych");
    }
}
