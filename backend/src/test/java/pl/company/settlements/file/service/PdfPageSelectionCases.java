package pl.company.settlements.file.service;

import pl.company.settlements.file.domain.AttachmentOwner;
import pl.company.settlements.file.domain.AttachmentType;

import java.util.List;

public final class PdfPageSelectionCases {
    private PdfPageSelectionCases() { }
    public record Scenario(String name, Runnable assertion) { }

    public static List<Scenario> scenarios() {
        return List.of(
                new Scenario("Wybór pojedynczej strony", () -> equal(List.of(1), PdfPageSelection.parse("1", 5))),
                new Scenario("Ostatnia strona jest dozwolona", () -> equal(List.of(5), PdfPageSelection.parse("5", 5))),
                new Scenario("Listy i zakresy zachowują kolejność dokumentu", () -> equal(List.of(1, 2, 3, 7, 10, 11, 12), PdfPageSelection.parse("1-3,7,10-12", 12))),
                new Scenario("Powtórzenia nie kopiują tej samej strony dwa razy", () -> equal(List.of(1, 2, 3), PdfPageSelection.parse("3,1-2,2", 5))),
                new Scenario("Odstępy w zapisie są dozwolone", () -> equal(List.of(1, 2, 3, 5), PdfPageSelection.parse(" 1 - 3 , 5 ", 5))),
                new Scenario("Cały zakres dokumentu jest dozwolony", () -> equal(List.of(1, 2, 3, 4, 5), PdfPageSelection.parse("1-5", 5))),
                new Scenario("Zakres jednej strony", () -> equal(List.of(2), PdfPageSelection.parse("2-2", 5))),
                new Scenario("Brak stron w źródle jest odrzucany", () -> rejects(() -> PdfPageSelection.parse("1", 0))),
                new Scenario("Brak wyboru jest obsługiwany przed parserem", () -> rejects(() -> PdfPageSelection.parse(null, 5))),
                new Scenario("Pusty wybór nie jest częścią podziału", () -> rejects(() -> PdfPageSelection.parse(" ", 5))),
                new Scenario("Strona zerowa jest odrzucana", () -> rejects(() -> PdfPageSelection.parse("0", 5))),
                new Scenario("Ujemny numer jest odrzucany", () -> rejects(() -> PdfPageSelection.parse("-1", 5))),
                new Scenario("Strona poza dokumentem jest odrzucana", () -> rejects(() -> PdfPageSelection.parse("6", 5))),
                new Scenario("Koniec zakresu poza dokumentem jest odrzucany", () -> rejects(() -> PdfPageSelection.parse("1-6", 5))),
                new Scenario("Odwrócony zakres jest odrzucany", () -> rejects(() -> PdfPageSelection.parse("4-2", 5))),
                new Scenario("Pusty element listy jest odrzucany", () -> rejects(() -> PdfPageSelection.parse("1,,3", 5))),
                new Scenario("Końcowy przecinek jest odrzucany", () -> rejects(() -> PdfPageSelection.parse("1,", 5))),
                new Scenario("Nieprawidłowy zakres jest odrzucany", () -> rejects(() -> PdfPageSelection.parse("1-2-3", 5))),
                new Scenario("Ułamkowy numer strony jest odrzucany", () -> rejects(() -> PdfPageSelection.parse("1.5", 5))),
                new Scenario("Zbyt duży numer nie powoduje przepełnienia", () -> rejects(() -> PdfPageSelection.parse("2147483648", 5))),
                new Scenario("Graniczny numer całkowity nie zapętla parsera", () -> equal(List.of(Integer.MAX_VALUE), PdfPageSelection.parse("2147483647", Integer.MAX_VALUE))),
                new Scenario("Potwierdzenie przelewu należy do przelewu", () -> {
                    AttachmentOwner.PAYMENT.validate(AttachmentType.PAYMENT_CONFIRMATION);
                    rejects(() -> AttachmentOwner.SUB_INVOICE.validate(AttachmentType.PAYMENT_CONFIRMATION));
                }),
                new Scenario("Oświadczenie może być powiązane z fakturą lub rekordem oświadczenia", () -> {
                    AttachmentOwner.SUB_INVOICE.validate(AttachmentType.SUBCONTRACTOR_STATEMENT);
                    AttachmentOwner.STATEMENT.validate(AttachmentType.SUBCONTRACTOR_STATEMENT);
                    rejects(() -> AttachmentOwner.MAIN_INVOICE.validate(AttachmentType.SUBCONTRACTOR_STATEMENT));
                }),
                new Scenario("Umowa i aneks mają odrębne typy dokumentu", () -> {
                    AttachmentOwner.CONTRACT.validate(AttachmentType.CONTRACT);
                    AttachmentOwner.AMENDMENT.validate(AttachmentType.AMENDMENT);
                    rejects(() -> AttachmentOwner.CONTRACT.validate(AttachmentType.AMENDMENT));
                }),
                new Scenario("Dokumenty polis i gwarancji należą do umów oraz aneksów", () -> {
                    for (AttachmentType type : new AttachmentType[]{AttachmentType.INSURANCE_DOCUMENT, AttachmentType.GUARANTEE_DOCUMENT}) {
                        AttachmentOwner.CONTRACT.validate(type);
                        AttachmentOwner.AMENDMENT.validate(type);
                        for (AttachmentOwner owner : new AttachmentOwner[]{AttachmentOwner.MAIN_INVOICE, AttachmentOwner.SUB_INVOICE, AttachmentOwner.PAYMENT, AttachmentOwner.STATEMENT}) {
                            rejects(() -> owner.validate(type));
                        }
                    }
                }),
                new Scenario("Każdy rodzaj ma etykietę i obsługującego właściciela", () -> {
                    for (AttachmentType type : AttachmentType.values()) {
                        if (type.getLabel().isBlank()) throw new AssertionError(type);
                        boolean supported = false;
                        for (AttachmentOwner owner : AttachmentOwner.values()) supported |= owner.supports(type);
                        if (!supported) throw new AssertionError(type);
                    }
                })
        );
    }

    public static void main(String[] args) {
        for (Scenario scenario : scenarios()) {
            try { scenario.assertion().run(); }
            catch (Throwable error) { throw new AssertionError(scenario.name(), error); }
        }
        System.out.println("Passed PDF selection and attachment owner scenarios: " + scenarios().size());
    }
    private static void rejects(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Brak oczekiwanego błędu.");
    }
    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
