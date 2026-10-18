package pl.company.settlements.contract.service;

import java.math.BigDecimal;
import java.util.List;

public final class ContractSettlementRuleCases {
    private ContractSettlementRuleCases() { }
    public record Scenario(String name, Runnable assertion) { }

    public static List<Scenario> scenarios() {
        return List.of(
                new Scenario("Faktura równa wartości umowy jest dozwolona", () -> valid("120000", "0", "0", "120000")),
                new Scenario("Przekroczenie limitu o grosz jest odrzucane", () -> invalid("120000", "0", "0", "120000.01")),
                new Scenario("Raportowana wielomilionowa faktura jest odrzucana", () -> invalid("120000", "0", "0", "5112572")),
                new Scenario("Poprzednie faktury pomniejszają limit", () -> invalid("120000", "100000", "0", "20000.01")),
                new Scenario("Pozostała dokładna kwota może być wykorzystana", () -> valid("120000", "100000", "0", "20000")),
                new Scenario("Edycja zastępuje poprzednią kwotę", () -> valid("120000", "110000", "100000", "110000")),
                new Scenario("Edycja nie pozwala przekroczyć limitu", () -> invalid("120000", "110000", "100000", "110000.01")),
                new Scenario("Grosze nie są zaokrąglane zmiennoprzecinkowo", () -> valid("0.30", "0.10", "0", "0.20")),
                new Scenario("Skala liczby nie zmienia granicy limitu", () -> valid("100.00", "80", "0", "20.0")),
                new Scenario("Przy pełnym limicie nie można dodać zapłaty", () -> invalid("120000", "120000", "0", "0.01")),
                new Scenario("Spłata kolejnej faktury sumuje wcześniejsze wpłaty", () -> invalid("120000", "119000", "0", "1000.01")),
                new Scenario("Poprawienie historycznego przekroczenia jest dozwolone", () -> valid("120000", "5112572", "5000000", "4000000")),
                new Scenario("Historyczne przekroczenie nie pozwala na nową kwotę", () -> invalid("120000", "5112572", "0", "0.01")),
                new Scenario("Zmiana opisu bez zmiany kwoty nie pogarsza przekroczenia", () -> valid("120000", "5112572", "5000000", "5000000")),
                new Scenario("Po aneksie limit może wystarczyć na kolejną fakturę", () -> valid("150000", "120000", "0", "30000")),
                new Scenario("Nie można obniżyć umowy poniżej faktur", () -> rejects(() -> reduction("120000", "99999.99", "100000", "90000"))),
                new Scenario("Nie można obniżyć umowy poniżej zapłat i potrąceń", () -> rejects(() -> reduction("120000", "109999.99", "100000", "110000"))),
                new Scenario("Obniżenie do dokładnej sumy faktur jest dozwolone", () -> reduction("120000", "100000", "100000", "90000")),
                new Scenario("Zwiększenie umowy z historycznym przekroczeniem jest dozwolone", () -> reduction("120000", "130000", "5112572", "5062572")),
                new Scenario("Zerowa umowa bez faktur jest dozwolona", () -> reduction("120000", "0", "0", "0")),
                new Scenario("Nie można obniżyć faktury poniżej zapłaty", () -> rejects(() -> ContractSettlementRules.validateInvoiceReduction(n("100"), n("89.99"), n("90")))),
                new Scenario("Fakturę można obniżyć do rozliczonej kwoty", () -> ContractSettlementRules.validateInvoiceReduction(n("100"), n("90"), n("90"))),
                new Scenario("Można zwiększyć fakturę z historyczną nadpłatą", () -> ContractSettlementRules.validateInvoiceReduction(n("100"), n("110"), n("120"))),
                new Scenario("Brak płatności jest traktowany jak zero", () -> ContractSettlementRules.validateInvoiceReduction(n("100"), n("90"), null))
        );
    }

    public static void main(String[] args) {
        for (Scenario scenario : scenarios()) {
            try { scenario.assertion().run(); }
            catch (Throwable error) { throw new AssertionError(scenario.name(), error); }
        }
        System.out.println("Passed contract settlement rule scenarios: " + scenarios().size());
    }

    private static void valid(String limit, String total, String old, String proposed) {
        ContractSettlementRules.validateIncrease("Limit testowy.", n(limit), n(total), n(old), n(proposed));
    }
    private static void invalid(String limit, String total, String old, String proposed) {
        rejects(() -> valid(limit, total, old, proposed));
    }
    private static void reduction(String current, String proposed, String invoiced, String settled) {
        ContractSettlementRules.validateValueReduction(n(current), n(proposed), n(invoiced), n(settled));
    }
    private static void rejects(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Brak oczekiwanego odrzucenia przekroczenia.");
    }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
}
