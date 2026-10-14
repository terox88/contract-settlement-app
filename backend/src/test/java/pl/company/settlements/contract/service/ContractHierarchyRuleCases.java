package pl.company.settlements.contract.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class ContractHierarchyRuleCases {

    private ContractHierarchyRuleCases() {
    }

    public record Scenario(String name, Runnable assertion) {
    }

    public static List<Scenario> scenarios() {
        List<Scenario> tests = new ArrayList<>();
        tests.add(new Scenario("Równe wartości i daty na granicach są dozwolone", () ->
                valid(main("1000", "2026-10-01", "2026-12-31"), sub("1000", "2026-10-01", "2026-12-31", 2L, "2222222222"))));
        tests.add(new Scenario("Umowa zerowa podpisana w ostatnim dniu jest dozwolona", () ->
                valid(main("1000", "2026-10-01", "2026-12-31"), sub("0", "2026-12-31", "2026-12-31", 2L, "2222222222"))));
        tests.add(new Scenario("Przekroczenie wartości o grosz jest odrzucane", () ->
                invalid(mainDefault(), sub("1000.01", "2026-11-01", "2026-12-31", 2L, "2222222222"), "wartości umowy głównej")));
        tests.add(new Scenario("Podpisanie przed umową główną jest odrzucane", () ->
                invalid(mainDefault(), sub("100", "2026-09-30", "2026-12-31", 2L, "2222222222"), "Data zawarcia")));
        tests.add(new Scenario("Podpisanie po końcu głównej jest dozwolone z ostrzeżeniem", () -> {
            var child = sub("100", "2027-01-01", "2027-01-01", 2L, "2222222222");
            valid(mainDefault(), child);
            equal(true, ContractHierarchyRules.exceedsMainContractEndDate(mainDefault().endDate(), child.endDate()));
        }));
        tests.add(new Scenario("Zakończenie po końcu głównej jest dozwolone z ostrzeżeniem", () -> {
            var child = sub("100", "2026-11-01", "2027-01-01", 2L, "2222222222");
            valid(mainDefault(), child);
            equal(true, ContractHierarchyRules.exceedsMainContractEndDate(mainDefault().endDate(), child.endDate()));
        }));
        tests.add(new Scenario("Zakończenie przed początkiem umowy głównej jest odrzucane", () ->
                invalid(mainDefault(), sub("100", "2026-11-01", "2026-09-30", 2L, "2222222222"), "Data zakończenia")));
        tests.add(new Scenario("Zakończenie przed własną datą zawarcia jest odrzucane", () ->
                invalid(mainDefault(), sub("100", "2026-11-15", "2026-11-14", 2L, "2222222222"), "poprzedzać jej zawarcia")));
        tests.add(new Scenario("Brak terminu głównego nie blokuje zapisu ani nie tworzy fałszywego ostrzeżenia", () -> {
            valid(main("1000", "2026-10-01", null), subDefault());
            equal(false, ContractHierarchyRules.exceedsMainContractEndDate(null, subDefault().endDate()));
        }));
        tests.add(new Scenario("Brak terminu podwykonawcy jest odrzucany", () ->
                invalid(mainDefault(), sub("100", "2026-11-01", null, 2L, "2222222222"), "jest wymagana")));
        tests.add(new Scenario("To samo ID wykonawcy jest odrzucane", () ->
                invalid(mainDefault(), sub("100", "2026-11-01", "2026-12-31", 1L, "2222222222"), "nie może być jej podwykonawcą")));
        tests.add(new Scenario("Ten sam NIP z separatorami przy innym ID jest odrzucany", () ->
                invalid(mainDefault(), sub("100", "2026-11-01", "2026-12-31", 2L, "111-111-11-11"), "nie może być jej podwykonawcą")));
        tests.add(new Scenario("Nowa firma z NIP wykonawcy głównego jest odrzucana", () ->
                invalid(mainDefault(), sub("100", "2026-11-01", "2026-12-31", null, "1111111111"), "nie może być jej podwykonawcą")));
        tests.add(new Scenario("Inna firma jest dozwolona", () -> valid(mainDefault(), subDefault())));
        tests.add(new Scenario("Brak NIP obu firm nie tworzy fałszywej tożsamości", () ->
                valid(new ContractHierarchyRules.State(money("1000"), date("2026-10-01"), date("2026-12-31"), 1L, null),
                        sub("100", "2026-11-01", "2026-12-31", 2L, null))));
        tests.add(new Scenario("Aneks zwiększający wartość główną rozszerza dopuszczalną wartość", () -> {
            var main = ContractHierarchyRules.state(money("1000"), date("2026-10-01"), date("2026-12-31"),
                    List.of(new ContractHierarchyRules.Change(1L, date("2026-11-01"), money("1000"), null)), 1L, "1111111111");
            valid(main, sub("1500", "2026-11-01", "2026-12-31", 2L, "2222222222"));
        }));
        tests.add(new Scenario("Aneks podwykonawcy nie może przekroczyć wartości głównej", () -> {
            var sub = ContractHierarchyRules.state(money("200"), date("2026-11-01"), date("2026-12-31"),
                    List.of(new ContractHierarchyRules.Change(1L, date("2026-11-15"), money("900"), null)), 2L, "2222222222");
            invalid(mainDefault(), sub, "wartości umowy głównej");
        }));
        tests.add(new Scenario("Skrócenie głównej jest dozwolone i powoduje ostrzeżenie", () -> {
            var main = ContractHierarchyRules.state(money("1000"), date("2026-10-01"), date("2026-12-31"),
                    List.of(new ContractHierarchyRules.Change(1L, date("2026-11-01"), null, date("2026-12-15"))), 1L, "1111111111");
            valid(main, subDefault());
            equal(true, ContractHierarchyRules.exceedsMainContractEndDate(main.endDate(), subDefault().endDate()));
        }));
        tests.add(new Scenario("Kolejność obowiązywania decyduje o terminie, a nie najpóźniejszy koniec", () -> {
            var state = ContractHierarchyRules.state(money("100"), date("2026-10-01"), date("2026-12-31"), List.of(
                    new ContractHierarchyRules.Change(2L, date("2026-12-01"), money("20"), date("2027-03-01")),
                    new ContractHierarchyRules.Change(1L, date("2026-11-01"), money("10"), date("2027-09-01"))), 1L, "1111111111");
            equal(date("2027-03-01"), state.endDate());
            equal(money("130"), state.value());
        }));
        tests.add(new Scenario("Ujemna wartość pośrednia jest odrzucana mimo dodatniej sumy", () -> rejects(() ->
                ContractHierarchyRules.state(money("100"), date("2026-10-01"), date("2026-12-31"), List.of(
                        new ContractHierarchyRules.Change(1L, date("2026-11-01"), money("-200"), null),
                        new ContractHierarchyRules.Change(2L, date("2026-12-01"), money("500"), null)), 2L, "2222222222"), "ujemną wartość")));
        tests.add(new Scenario("Przesunięcie początku głównej nie może pominąć daty podwykonawcy", () ->
                invalid(main("1000", "2026-11-15", "2026-12-31"), subDefault(), "Data zawarcia")));
        tests.add(new Scenario("Ujemna wartość początkowa jest odrzucana", () -> rejects(() ->
                ContractHierarchyRules.state(money("-0.01"), date("2026-10-01"), date("2026-12-31"), List.of(), 2L, "2222222222"), "musi być nieujemna")));
        tests.add(new Scenario("Aneks bez terminu zachowuje termin pierwotny", () -> {
            var state = ContractHierarchyRules.state(money("100"), date("2026-10-01"), date("2026-12-31"),
                    List.of(new ContractHierarchyRules.Change(1L, date("2026-11-01"), money("25"), null)), 2L, "2222222222");
            equal(date("2026-12-31"), state.endDate());
        }));
        tests.add(new Scenario("Brak pierwotnego terminu może zostać uzupełniony aneksem", () -> {
            var state = ContractHierarchyRules.state(money("100"), date("2026-11-01"), null,
                    List.of(new ContractHierarchyRules.Change(1L, date("2026-11-15"), null, date("2026-12-31"))), 2L, "2222222222");
            valid(mainDefault(), state);
        }));
        tests.add(new Scenario("Suma przekracza limit mimo że każda umowa osobno jest mniejsza", () -> rejects(() ->
                ContractHierarchyRules.validateSubcontractsValue(mainDefault(), List.of(
                        sub("600", "2026-11-01", "2026-12-31", 2L, "2222222222"),
                        sub("400.01", "2026-11-01", "2026-12-31", 3L, "3333333333"))), "Przekroczenie: 0.01")));
        tests.add(new Scenario("Suma równa aktualnej wartości głównej jest dozwolona", () ->
                ContractHierarchyRules.validateSubcontractsValue(mainDefault(), List.of(
                        sub("600", "2026-11-01", "2026-12-31", 2L, "2222222222"),
                        sub("400", "2026-11-01", "2026-12-31", 3L, "3333333333")))));
        tests.add(new Scenario("Pusta lista ma zerową sumę", () -> {
            equal(BigDecimal.ZERO, ContractHierarchyRules.subcontractsTotalValue(List.of()));
            ContractHierarchyRules.validateSubcontractsValue(main("0", "2026-10-01", null), List.of());
        }));
        tests.add(new Scenario("Suma aktualnych wartości uwzględnia aneksy obu podwykonawców", () -> {
            var first = ContractHierarchyRules.state(money("200"), date("2026-11-01"), date("2026-12-31"),
                    List.of(new ContractHierarchyRules.Change(1L, date("2026-11-15"), money("400"), null)), 2L, "2222222222");
            var second = ContractHierarchyRules.state(money("300"), date("2026-11-01"), date("2026-12-31"),
                    List.of(new ContractHierarchyRules.Change(2L, date("2026-11-16"), money("100.01"), null)), 3L, "3333333333");
            rejects(() -> ContractHierarchyRules.validateSubcontractsValue(mainDefault(), List.of(first, second)), "Przekroczenie: 0.01");
        }));
        tests.add(new Scenario("Grosze są sumowane dokładnie", () -> {
            var children = List.of(sub("0.10", "2026-11-01", "2026-12-31", 2L, "2222222222"),
                    sub("0.20", "2026-11-01", "2026-12-31", 3L, "3333333333"));
            equal(money("0.30"), ContractHierarchyRules.subcontractsTotalValue(children));
            ContractHierarchyRules.validateSubcontractsValue(main("0.30", "2026-10-01", "2026-12-31"), children);
        }));
        tests.add(new Scenario("Równy termin końcowy nie wymaga ostrzeżenia", () ->
                equal(false, ContractHierarchyRules.exceedsMainContractEndDate(date("2026-12-31"), date("2026-12-31")))));
        tests.add(new Scenario("Wcześniejszy termin końcowy nie wymaga ostrzeżenia", () ->
                equal(false, ContractHierarchyRules.exceedsMainContractEndDate(date("2026-12-31"), date("2026-12-30")))));
        tests.add(new Scenario("Brak końca podwykonawcy nie tworzy fałszywego ostrzeżenia", () ->
                equal(false, ContractHierarchyRules.exceedsMainContractEndDate(date("2026-12-31"), null))));
        return tests;
    }

    public static void main(String[] args) {
        int passed = 0;
        for (Scenario scenario : scenarios()) {
            try {
                scenario.assertion().run();
                passed++;
            } catch (Throwable error) {
                throw new AssertionError(scenario.name(), error);
            }
        }
        System.out.println("Passed contract hierarchy rule scenarios: " + passed);
    }

    private static ContractHierarchyRules.State mainDefault() {
        return main("1000", "2026-10-01", "2026-12-31");
    }

    private static ContractHierarchyRules.State subDefault() {
        return sub("100", "2026-11-01", "2026-12-31", 2L, "2222222222");
    }

    private static ContractHierarchyRules.State main(String value, String start, String end) {
        return sub(value, start, end, 1L, "1111111111");
    }

    private static ContractHierarchyRules.State sub(String value, String start, String end, Long contractorId, String taxId) {
        return new ContractHierarchyRules.State(money(value), date(start), date(end), contractorId, taxId);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static LocalDate date(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    private static void valid(ContractHierarchyRules.State main, ContractHierarchyRules.State sub) {
        ContractHierarchyRules.validateSubcontract(main, sub);
        ContractHierarchyRules.validateSubcontractsValue(main, List.of(sub));
    }

    private static void invalid(ContractHierarchyRules.State main, ContractHierarchyRules.State sub, String message) {
        rejects(() -> valid(main, sub), message);
    }

    private static void rejects(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException error) {
            if (!error.getMessage().contains(message)) throw new AssertionError("Nieoczekiwany błąd: " + error.getMessage(), error);
            return;
        }
        throw new AssertionError("Brak oczekiwanego błędu: " + message);
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
