package pl.company.settlements.invoice.service;

import pl.company.settlements.invoice.model.DeductionReview;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SettlementReviewCases {
    public record Case(String name, Runnable check) { }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static DeductionReviewRules.Input input(String paid, String deducted, boolean payments) {
        return new DeductionReviewRules.Input(42L, n("800"), n("1000"), "PLN", n(paid), n(deducted), payments,
                new DeductionReviewRules.Rule(n("5"), "GROSS"), null);
    }
    private static DeductionReview review(DeductionReviewRules.Input input) { return DeductionReviewRules.describe(input, null, null); }
    public static List<Case> cases() {
        return List.of(
            new Case("1000 zł brutto i 5% brutto daje 50 zł", () -> eq(n("50.00"), review(input("950", "50", true)).expectedAmount())),
            new Case("Potrącenie zgodne z umową", () -> eq("MATCHED", review(input("950", "50", true)).status())),
            new Case("Niezgodność o grosz jest ostrzeżeniem", () -> { var r = review(input("950", "49.99", true)); eq("MISMATCH", r.status()); eq(n("-0.01"), r.difference()); }),
            new Case("Potrącenie ponad regułę jest ostrzeżeniem", () -> eq("MISMATCH", review(input("900", "100", true)).status())),
            new Case("Brak przelewów nie jest zakończonym sprawdzeniem", () -> eq("NOT_STARTED", review(input("0", "0", false)).status())),
            new Case("Częściowy przelew porównuje z docelowym potrąceniem całej faktury", () -> eq("MISMATCH", review(input("400", "25", true)).status())),
            new Case("Kilka przelewów: sumarycznie 50 zł, jedna reguła", () -> { BigDecimal sum = List.of(n("20"), n("30")).stream().reduce(BigDecimal.ZERO, BigDecimal::add); eq("MATCHED", review(input("950", sum.toPlainString(), true)).status()); }),
            new Case("Potwierdzenie rozbieżności", () -> { var i = input("950", "49", true); var r = review(i); var c = DeductionReviewRules.describe(i, r.fingerprint(), Instant.EPOCH); eq("CONFIRMED", c.status()); eq(Instant.EPOCH, c.confirmedAt()); }),
            new Case("Zmiana kwoty unieważnia potwierdzenie", () -> { var old = review(input("950", "49", true)); var c = DeductionReviewRules.describe(input("951", "48", true), old.fingerprint(), Instant.EPOCH); eq("MISMATCH", c.status()); eq(true, c.confirmationOutdated()); eq(null, c.confirmedAt()); }),
            new Case("Zmiana zapłaty wymaga ponownej kontroli", () -> { var old = review(input("940", "49", true)); eq(true, DeductionReviewRules.describe(input("941", "49", true), old.fingerprint(), Instant.EPOCH).confirmationOutdated()); }),
            new Case("Zmiana reguły przy tej samej kwocie wyniku unieważnia potwierdzenie", () -> {
                var old = review(input("950", "49", true)); var i = new DeductionReviewRules.Input(42L,n("800"),n("1000"),"PLN",n("950"),n("49"),true,new DeductionReviewRules.Rule(n("6.25"),"NET"),null);
                eq(true, DeductionReviewRules.describe(i, old.fingerprint(), Instant.EPOCH).confirmationOutdated());
            }),
            new Case("NET i GROSS liczone osobno", () -> {
                var i = new DeductionReviewRules.Input(42L,n("800"),n("1000"),"PLN",n("900"),n("66"),true,
                        new DeductionReviewRules.Rule(n("5"),"GROSS"),new DeductionReviewRules.Rule(n("2"),"NET"));
                var r=review(i); eq(n("50.00"), r.securityAmount()); eq(n("16.00"), r.otherAmount()); eq(n("66.00"),r.expectedAmount());
            }),
            new Case("Zaokrąglenie każdego składnika HALF_UP", () -> eq(n("0.01"), DeductionReviewRules.expected(new DeductionReviewRules.Rule(n("5"),"GROSS"),n("0.1"),n("0.1")))),
            new Case("Brak reguły daje zero", () -> eq(n("0.00"),DeductionReviewRules.expected(null,n("100"),n("123")))),
            new Case("Reguła zero bez podstawy daje zero", () -> eq(n("0.00"),DeductionReviewRules.expected(new DeductionReviewRules.Rule(n("0"),null),n("100"),n("123")))),
            new Case("Nieprawidłowa podstawa nie jest zgadywana", () -> rejects(() -> DeductionReviewRules.expected(new DeductionReviewRules.Rule(n("5"),"UNKNOWN"),n("100"),n("123")))),
            new Case("Procent powyżej 100 nie jest akceptowany", () -> rejects(() -> DeductionReviewRules.expected(new DeductionReviewRules.Rule(n("101"),"NET"),n("100"),n("123")))),
            new Case("Fingerprint niezależny od kolejności odczytu", () -> eq(new ReviewFingerprint().add("a",1).add("b",2).value(),new ReviewFingerprint().add("b",2).add("a",1).value())),
            new Case("Fingerprint odporny na separator w nazwie", () -> different(new ReviewFingerprint().add("a","x:y","z").value(),new ReviewFingerprint().add("a","x","y:z").value())),
            new Case("Fingerprint odróżnia null i pusty tekst", () -> different(new ReviewFingerprint().add("a",(Object)null).value(),new ReviewFingerprint().add("a","").value())),
            new Case("Skala BigDecimal nie zmienia fingerprintu", () -> eq(new ReviewFingerprint().add("a",n("5.00")).value(),new ReviewFingerprint().add("a",n("5")).value())),
            new Case("Skala procentu nie unieważnia potwierdzenia", () -> {
                var old = review(input("950","49",true)); var i = new DeductionReviewRules.Input(42L,n("800.00"),n("1000.00"),"PLN",n("950.00"),n("49.00"),true,new DeductionReviewRules.Rule(n("5.00"),"GROSS"),null);
                eq("CONFIRMED", DeductionReviewRules.describe(i,old.fingerprint(),Instant.EPOCH).status());
            })
        );
    }
    private static void rejects(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Expected rejection"); }
    private static void eq(Object expected,Object actual) { if (!java.util.Objects.equals(expected,actual)) throw new AssertionError("Expected "+expected+" got "+actual); }
    private static void different(Object left,Object right) { if (java.util.Objects.equals(left,right)) throw new AssertionError("Different inputs have same fingerprint"); }
    public static void main(String[] args) { for(var c:cases())try { c.check().run(); }catch(Throwable e){throw new AssertionError(c.name(),e);} System.out.println("Settlement review: "+cases().size()+" checks passed"); }
}
