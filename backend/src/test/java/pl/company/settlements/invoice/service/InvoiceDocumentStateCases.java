package pl.company.settlements.invoice.service;

import java.math.BigDecimal;
import java.util.List;

public final class InvoiceDocumentStateCases {
    public record Scenario(String name, Runnable assertion) { }

    public static List<Scenario> scenarios() {
        return List.of(
                new Scenario("Pusta faktura nie ma plików ani płatności", () -> {
                    var state = new InvoiceDocumentState();
                    equal(0, state.summary().invoiceFileCount());
                    equal(0, state.summary().statementFileCount());
                    equal(0, state.summary().paymentCount());
                    equal(false, state.hasPaymentAllocations());
                }),
                new Scenario("PDF i XML są dwoma plikami faktury", () -> {
                    var state = new InvoiceDocumentState();
                    state.addDocument(InvoiceDocumentState.Kind.INVOICE, 10L);
                    state.addDocument(InvoiceDocumentState.Kind.INVOICE, 11L);
                    equal(2, state.summary().invoiceFileCount());
                }),
                new Scenario("Ten sam plik w dwóch powiązaniach nie jest liczony podwójnie", () -> {
                    var state = new InvoiceDocumentState();
                    state.addDocument(InvoiceDocumentState.Kind.STATEMENT, 10L);
                    state.addDocument(InvoiceDocumentState.Kind.STATEMENT, 10L);
                    equal(1, state.summary().statementFileCount());
                }),
                new Scenario("Powiązanie bez pliku nie potwierdza dokumentu", () -> {
                    var state = new InvoiceDocumentState();
                    state.addDocument(InvoiceDocumentState.Kind.INVOICE, null);
                    equal(0, state.summary().invoiceFileCount());
                }),
                new Scenario("Plik podpięty do faktury nie potwierdza konkretnych przelewów", () -> {
                    var state = new InvoiceDocumentState();
                    state.addAllocation(20L, new BigDecimal("100"));
                    state.addDocument(InvoiceDocumentState.Kind.PAYMENT_CONFIRMATION, 10L);
                    equal(1, state.summary().paymentConfirmationFileCount());
                    equal(0, state.summary().paymentsWithConfirmationCount());
                    equal(1, state.summary().unassignedPaymentConfirmationFileCount());
                }),
                new Scenario("Potwierdzenie jednej z trzech płatności nie daje kompletu", () -> {
                    var state = new InvoiceDocumentState();
                    for (long id = 1; id <= 3; id++) state.addAllocation(id, BigDecimal.ONE);
                    state.addPaymentConfirmation(1L, 10L);
                    equal(3, state.summary().paymentCount());
                    equal(1, state.summary().paymentsWithConfirmationCount());
                }),
                new Scenario("Kilka plików jednego przelewu potwierdza jeden przelew", () -> {
                    var state = new InvoiceDocumentState();
                    state.addAllocation(1L, BigDecimal.ONE);
                    state.addAllocation(1L, BigDecimal.ONE);
                    state.addPaymentConfirmation(1L, 10L);
                    state.addPaymentConfirmation(1L, 11L);
                    equal(1, state.summary().paymentCount());
                    equal(1, state.summary().paymentsWithConfirmationCount());
                    equal(2, state.summary().paymentConfirmationFileCount());
                }),
                new Scenario("Samo potrącenie nie wymaga potwierdzenia przelewu, ale blokuje edycję faktury", () -> {
                    var state = new InvoiceDocumentState();
                    state.addAllocation(1L, BigDecimal.ZERO);
                    state.addPaymentConfirmation(1L, 10L);
                    equal(0, state.summary().paymentCount());
                    equal(0, state.summary().paymentsWithConfirmationCount());
                    equal(true, state.hasPaymentAllocations());
                }),
                new Scenario("Obcy przelew nie uzupełnia dokumentów faktury", () -> {
                    var state = new InvoiceDocumentState();
                    state.addAllocation(1L, BigDecimal.ONE);
                    state.addPaymentConfirmation(99L, 10L);
                    equal(0, state.summary().paymentConfirmationFileCount());
                }),
                new Scenario("Plik wspólny dla dwóch faktur jest widoczny przy obu", () -> {
                    var first = new InvoiceDocumentState();
                    var second = new InvoiceDocumentState();
                    for (var state : List.of(first, second)) {
                        state.addAllocation(1L, BigDecimal.ONE);
                        state.addPaymentConfirmation(1L, 10L);
                        equal(1, state.summary().paymentsWithConfirmationCount());
                    }
                }),
                new Scenario("Plik powiązany bezpośrednio i przez przelew jest liczony raz", () -> {
                    var state = new InvoiceDocumentState();
                    state.addAllocation(1L, BigDecimal.ONE);
                    state.addDocument(InvoiceDocumentState.Kind.PAYMENT_CONFIRMATION, 10L);
                    state.addPaymentConfirmation(1L, 10L);
                    equal(1, state.summary().paymentConfirmationFileCount());
                    equal(0, state.summary().unassignedPaymentConfirmationFileCount());
                }),
                new Scenario("Dokumenty różnych kategorii są liczone osobno", () -> {
                    var state = new InvoiceDocumentState();
                    state.addDocument(InvoiceDocumentState.Kind.STATEMENT, 10L);
                    equal(0, state.summary().invoiceFileCount());
                    equal(1, state.summary().statementFileCount());
                })
        );
    }

    public static void main(String[] args) {
        for (Scenario scenario : scenarios()) {
            try { scenario.assertion().run(); }
            catch (Throwable error) { throw new AssertionError(scenario.name(), error); }
        }
        System.out.println("Passed invoice document scenarios: " + scenarios().size());
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
