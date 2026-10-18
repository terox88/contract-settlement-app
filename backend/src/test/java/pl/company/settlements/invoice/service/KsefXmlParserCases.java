package pl.company.settlements.invoice.service;

import pl.company.settlements.invoice.model.KsefInvoicePreview;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class KsefXmlParserCases {
    private static final KsefXmlParser PARSER = new KsefXmlParser();
    public record Case(String name, Runnable check) { }

    public static String xml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Faktura xmlns="http://crd.gov.pl/wzor/2025/06/25/13775/">
                  <Naglowek><KodFormularza kodSystemowy="FA (3)" wersjaSchemy="1-0E">FA</KodFormularza><WariantFormularza>3</WariantFormularza></Naglowek>
                  <Podmiot1><DaneIdentyfikacyjne><NIP>1234563218</NIP><Nazwa>Żółty Wykonawca &amp; Syn</Nazwa></DaneIdentyfikacyjne></Podmiot1>
                  <Podmiot2><DaneIdentyfikacyjne><NIP>1111111111</NIP><Nazwa>Nabywca</Nazwa></DaneIdentyfikacyjne></Podmiot2>
                  <Fa><KodWaluty>PLN</KodWaluty><P_1>2026-10-18</P_1><P_2>FV/10/2026</P_2><P_6>2026-10-17</P_6>
                    <P_13_1>1000.00</P_13_1><P_14_1>230.00</P_14_1><P_15>1230.00</P_15>
                    <RodzajFaktury>VAT</RodzajFaktury>
                    <FaWiersz><NrWierszaFa>1</NrWierszaFa><P_7>Prace</P_7><P_11>1000.00</P_11><P_12>23</P_12></FaWiersz>
                    <Platnosc><TerminPlatnosci><Termin>2026-11-17</Termin></TerminPlatnosci></Platnosc>
                  </Fa>
                </Faktura>
                """;
    }

    public static List<Case> cases() {
        return List.of(
                test("FA3: numer, daty, kwoty i sprzedawca", () -> {
                    var p = parse(xml()); equal("FA(3)", p.schema()); equal("FV/10/2026", p.invoiceNumber());
                    equal("1000.00", p.netAmount()); equal("230.00", p.vatAmount()); equal("1230.00", p.grossAmount());
                    equal(LocalDate.of(2026, 10, 18), p.issueDate()); equal(LocalDate.of(2026, 10, 17), p.saleDate());
                    equal(LocalDate.of(2026, 11, 17), p.dueDate()); equal(List.of("23"), p.vatRates());
                    equal("Żółty Wykonawca & Syn", p.seller().name()); equal("1234563218", p.seller().taxId());
                }),
                test("FA2: rozpoznanie starszego formatu", () -> equal("FA(2)", parse(xml().replace("2025/06/25/13775", "2023/06/29/12648")
                        .replace("FA (3)", "FA (2)").replace("<WariantFormularza>3", "<WariantFormularza>2")).schema())),
                test("Prefiksy przestrzeni nazw", () -> equal("1230.00", parse(xml().replace("xmlns=", "xmlns:k=")
                        .replaceAll("<(\\/?)([A-Za-z][A-Za-z0-9_]*)", "<$1k:$2")).grossAmount())),
                test("UTF-8 BOM", () -> equal("FA(3)", parse("\ufeff" + xml()).schema())),
                test("Wiele stawek, bez podwójnego liczenia pozycji", () -> {
                    var p = parse(xml().replace("<P_15>1230.00", "<P_13_2>100</P_13_2><P_14_2>8</P_14_2><P_13_7>20</P_13_7><P_15>1358.00")
                            .replace("</FaWiersz>", "</FaWiersz><FaWiersz><P_12>8</P_12></FaWiersz><FaWiersz><P_12>zw</P_12></FaWiersz><FaWiersz><P_12>23</P_12></FaWiersz>"));
                    equal("1120.00", p.netAmount()); equal("238.00", p.vatAmount()); equal(List.of("23", "8", "ZW"), p.vatRates());
                }),
                test("Sprzedaż zwolniona: VAT zero", () -> {
                    var p = parse(xml().replace("<P_13_1>1000.00</P_13_1><P_14_1>230.00</P_14_1>", "<P_13_7>1230.00</P_13_7>")
                            .replace("<P_12>23", "<P_12>zw")); equal("0.00", p.vatAmount()); equal("1230.00", p.netAmount());
                }),
                test("0%, NP i odwrotne obciążenie sumowane", () -> {
                    var p = parse(xml().replace("<P_13_1>1000.00</P_13_1><P_14_1>230.00</P_14_1>",
                            "<P_13_6_1>100</P_13_6_1><P_13_6_2>100</P_13_6_2><P_13_6_3>100</P_13_6_3><P_13_8>100</P_13_8><P_13_9>100</P_13_9><P_13_10>730</P_13_10>"));
                    equal("1230.00", p.netAmount()); equal("0.00", p.vatAmount());
                }),
                test("Zaliczka: podsumowanie zaliczki, bez zamówienia", () -> {
                    var p = parse(xml().replace(">VAT<", ">ZAL<").replace("</Fa>", "<Zamowienie><WartoscZamowienia>999999</WartoscZamowienia><ZamowienieWiersz><P_11NettoZ>999999</P_11NettoZ></ZamowienieWiersz></Zamowienie></Fa>"));
                    equal("1230.00", p.grossAmount()); warning(p, "zaliczkowa");
                }),
                test("Uproszczona z podsumowaniem", () -> equal("UPR", parse(xml().replace(">VAT<", ">UPR<")).invoiceKind())),
                test("Brak stawek nie zgaduje 23%", () -> {
                    var p = parse(xml().replace("<P_12>23</P_12>", "")); equal(List.of(), p.vatRates()); warning(p, "stawek VAT");
                }),
                test("Brak jednej ze stawek zgłasza ostrzeżenie", () -> warning(parse(xml().replace("</FaWiersz>", "</FaWiersz><FaWiersz><P_7>Inne</P_7></FaWiersz>")), "stawek VAT")),
                test("Wiele terminów pozostawia pustą datę", () -> {
                    var p = parse(xml().replace("</Platnosc>", "<TerminPlatnosci><Termin>2026-12-17</Termin></TerminPlatnosci></Platnosc>"));
                    equal(null, p.dueDate()); warning(p, "kilka terminów");
                }),
                test("Termin opisowy FA3 nie jest przeliczany", () -> {
                    var p = parse(xml().replace("<Termin>2026-11-17</Termin>", "<TerminOpis><Ilosc>14</Ilosc><Jednostka>dni</Jednostka><ZdarzeniePoczatkowe>od odbioru</ZdarzeniePoczatkowe></TerminOpis>"));
                    equal(null, p.dueDate()); warning(p, "Termin opisowy");
                }),
                test("Okres sprzedaży nie zmienia daty sprzedaży na datę końca", () -> {
                    var p = parse(xml().replace("<P_6>2026-10-17</P_6>", "<OkresFa><P_6_Od>2026-10-01</P_6_Od><P_6_Do>2026-10-17</P_6_Do></OkresFa>"));
                    equal(null, p.saleDate()); warning(p, "2026-10-01");
                }),
                test("Odliczenia nie zastępują brutto kwotą do zapłaty", () -> {
                    var p = parse(xml().replace("</Fa>", "<Rozliczenie><SumaOdliczen>50</SumaOdliczen><DoZaplaty>1180</DoZaplaty></Rozliczenie></Fa>"));
                    equal("1230.00", p.grossAmount()); warning(p, "potrącenia");
                }),
                test("Flaga zapłaty nie tworzy płatności", () -> warning(parse(xml().replace("<Platnosc>", "<Platnosc><Zaplacono>1</Zaplacono><DataZaplaty>2026-10-18</DataZaplaty>")), "nie tworzą płatności")),
                test("Zagnieżdżone referencje nie zastępują numeru ani kwot", () -> equal("FV/10/2026", parse(xml().replace("</Fa>", "<Referencja><P_2>INNA</P_2><P_15>999999</P_15></Referencja></Fa>")).invoiceNumber())),
                test("Dozwolone zero VAT i precyzja grosza", () -> equal("0.01", parse(xml().replace("1000.00", "0.01").replace(">230.00<", ">0.00<").replace("1230.00", "0.01")).grossAmount())),
                test("Maksymalna kwota zachowuje wszystkie cyfry", () -> equal("9999999999999.99", parse(xml().replace("1000.00", "9999999999999.99").replace(">230.00<", ">0.00<").replace("1230.00", "9999999999999.99")).grossAmount())),
                rejected("Błędna suma o grosz", xml().replace("1230.00", "1230.01"), "Suma netto"),
                rejected("Ujemna kwota", xml().replace("1000.00", "-1000.00"), "kwota P_13_1"),
                rejected("Zbyt duża kwota", xml().replace("1000.00", "10000000000000.00"), "zakres"),
                rejected("Trzy miejsca po kropce", xml().replace("1000.00", "1000.001"), "kwota"),
                rejected("Przecinek w XML", xml().replace("1000.00", "1000,00"), "kwota"),
                rejected("Notacja wykładnicza", xml().replace("1000.00", "1E3"), "kwota"),
                rejected("Brak podsumowania netto", xml().replace("<P_13_1>1000.00</P_13_1>", ""), "Brak podsumowania"),
                rejected("Brak VAT dla podstawy", xml().replace("<P_14_1>230.00</P_14_1>", ""), "Brak kwoty podatku"),
                rejected("Brak P15", xml().replace("<P_15>1230.00</P_15>", ""), "P_15"),
                rejected("Błędna data", xml().replace("2026-10-18", "2026-02-30"), "data P_1"),
                rejected("Błędny NIP", xml().replace("1234563218", "ABC"), "NIP"),
                rejected("Zbyt długi numer", xml().replace("FV/10/2026", "x".repeat(256)), "255"),
                rejected("Korekta", xml().replace(">VAT<", ">KOR<"), "osobnej obsługi"),
                rejected("Korekta zaliczki", xml().replace(">VAT<", ">KOR_ZAL<"), "osobnej obsługi"),
                rejected("Faktura końcowa", xml().replace(">VAT<", ">ROZ<"), "osobnej obsługi"),
                rejected("Marża", xml().replace("</Fa>", "<P_13_11>100</P_13_11></Fa>"), "marży"),
                rejected("Marża ze znacznika", xml().replace("<RodzajFaktury>", "<Adnotacje><PMarzy><P_PMarzy>1</P_PMarzy></PMarzy></Adnotacje><RodzajFaktury>"), "marży"),
                rejected("Waluta obca", xml().replace(">PLN<", ">EUR<"), "PLN"),
                rejected("Inna przestrzeń nazw", xml().replace("13775", "11111"), "Nie rozpoznano"),
                rejected("Niepasujący nagłówek", xml().replace("FA (3)", "FA (2)"), "Nagłówek"),
                rejected("Nieznana wersja schemy", xml().replace("1-0E", "9-0E"), "Nagłówek"),
                rejected("Powielony numer", xml().replace("</P_2>", "</P_2><P_2>INNY</P_2>"), "wielokrotnie"),
                rejected("Obca przestrzeń nazw pola", xml().replace("<P_2>", "<P_2 xmlns=\"urn:evil\">"), "przestrzeń nazw"),
                rejected("Element zamiast tekstu", xml().replace("FV/10/2026", "<Numer>FV</Numer>"), "zagnieżdżone"),
                rejected("Niepoprawny XML", "<Faktura>", "Nieprawidłowy XML"),
                rejected("DTD i XXE", xml().replace("<Faktura xmlns", "<!DOCTYPE Faktura [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><Faktura xmlns").replace("FV/10/2026", "&xxe;"), "bez DTD"),
                rejected("Zewnętrzny DTD", xml().replace("<Faktura xmlns", "<!DOCTYPE Faktura SYSTEM 'https://127.0.0.1/schema'><Faktura xmlns"), "bez DTD"),
                rejected("Nadmierne zagnieżdżenie", xml().replace("</Fa>", "<x>".repeat(110) + "</x>".repeat(110) + "</Fa>"), "Nieprawidłowy XML"),
                test("Limit rozmiaru", () -> rejects(() -> PARSER.parse(new byte[KsefXmlParser.MAX_BYTES + 1]), "5 MB")),
                test("Pusty plik", () -> rejects(() -> PARSER.parse(new byte[0]), "niepusty")),
                test("Brak pliku", () -> rejects(() -> PARSER.parse(null), "niepusty"))
        );
    }

    public static void main(String[] args) {
        for (Case c : cases()) {
            try { c.check().run(); }
            catch (Throwable error) { throw new AssertionError(c.name(), error); }
        }
        System.out.println("KsefXmlParser: " + cases().size() + " checks passed");
    }

    private static Case test(String name, Runnable check) { return new Case(name, check); }
    private static Case rejected(String name, String xml, String fragment) { return test(name, () -> rejects(() -> parse(xml), fragment)); }
    private static KsefInvoicePreview parse(String xml) { return PARSER.parse(xml.getBytes(StandardCharsets.UTF_8)); }
    private static void warning(KsefInvoicePreview preview, String fragment) {
        if (preview.warnings().stream().noneMatch(value -> value.contains(fragment))) throw new AssertionError("Missing warning: " + fragment);
    }
    private static void rejects(Runnable check, String fragment) {
        try { check.run(); }
        catch (IllegalArgumentException error) {
            if (!error.getMessage().contains(fragment)) throw new AssertionError(error.getMessage(), error);
            return;
        }
        throw new AssertionError("Expected rejection: " + fragment);
    }
    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
}
