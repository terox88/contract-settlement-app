package pl.company.settlements.invoice.service;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import pl.company.settlements.invoice.model.KsefInvoicePreview;
import pl.company.settlements.invoice.model.KsefInvoicePreview.Party;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lokalny odczyt pól FA(2)/FA(3), bez pobierania schematów ani komunikacji z KSeF. */
public final class KsefXmlParser {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final String FA2 = "http://crd.gov.pl/wzor/2023/06/29/12648/";
    private static final String FA3 = "http://crd.gov.pl/wzor/2025/06/25/13775/";
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");
    private static final List<String> NET_FIELDS = List.of("P_13_1", "P_13_2", "P_13_3", "P_13_4",
            "P_13_5", "P_13_6_1", "P_13_6_2", "P_13_6_3", "P_13_7", "P_13_8", "P_13_9", "P_13_10");

    public KsefInvoicePreview parse(byte[] xml) {
        if (xml == null || xml.length == 0) throw invalid("Wybierz niepusty plik XML KSeF.");
        if (xml.length > MAX_BYTES) throw invalid("Plik XML KSeF może mieć maksymalnie 5 MB.");
        Element root = read(xml);
        String namespace = root.getNamespaceURI();
        if (!"Faktura".equals(root.getLocalName()) || (!FA2.equals(namespace) && !FA3.equals(namespace))) {
            throw invalid("Nie rozpoznano faktury KSeF FA(2) lub FA(3). Wybierz oryginalny plik XML faktury.");
        }
        String version = FA3.equals(namespace) ? "3" : "2";
        Element header = required(root, "Naglowek");
        Element code = required(header, "KodFormularza");
        if (!"FA".equals(value(code)) || !("FA (" + version + ")").equals(code.getAttribute("kodSystemowy"))
                || !"1-0E".equals(code.getAttribute("wersjaSchemy"))
                || !version.equals(text(header, "WariantFormularza", true))) {
            throw invalid("Nagłówek faktury nie odpowiada jej strukturze FA(" + version + ").");
        }
        Element fa = required(root, "Fa");
        String kind = text(fa, "RodzajFaktury", true);
        if (!Set.of("VAT", "ZAL", "UPR").contains(kind)) {
            throw invalid("Import typu „" + kind + "” nie jest jeszcze obsługiwany. Korekty i faktury końcowe wymagają osobnej obsługi rozliczeń.");
        }
        if (!"PLN".equals(text(fa, "KodWaluty", true))) {
            throw invalid("Import obsługuje faktury w PLN. Aplikacja nie przelicza walut na wartość umowy.");
        }
        Element margin = child(child(fa, "Adnotacje"), "PMarzy");
        if (child(fa, "P_13_11") != null || (margin != null && !"1".equals(text(margin, "P_PMarzyN", false)))) {
            throw invalid("Faktura w procedurze marży wymaga osobnej obsługi; nie można ustalić kwot netto i VAT do formularza.");
        }
        List<String> warnings = new ArrayList<>();
        BigDecimal net = BigDecimal.ZERO;
        boolean hasSummary = false;
        for (String name : NET_FIELDS) {
            if (child(fa, name) != null) { net = net.add(amount(fa, name)); hasSummary = true; }
        }
        if (!hasSummary) throw invalid("Brak podsumowania netto (P_13_*). Nie wyliczamy go z kwoty brutto ani z zaokrąglonych pozycji.");
        BigDecimal vat = BigDecimal.ZERO;
        for (int index = 1; index <= 5; index++) {
            String vatName = "P_14_" + index;
            if (child(fa, vatName) != null) {
                if (child(fa, "P_13_" + index) == null) throw invalid("Kwota " + vatName + " nie ma odpowiadającej podstawy netto.");
                vat = vat.add(amount(fa, vatName));
            } else if (child(fa, "P_13_" + index) != null) {
                throw invalid("Brak kwoty podatku " + vatName + " dla podanej podstawy netto.");
            }
        }
        BigDecimal gross = amount(fa, "P_15");
        if (net.compareTo(MAX_AMOUNT) > 0 || vat.compareTo(MAX_AMOUNT) > 0 || gross.signum() <= 0
                || gross.compareTo(MAX_AMOUNT) > 0) throw invalid("Kwoty faktury przekraczają zakres formularza lub brutto nie jest dodatnie.");
        if (net.add(vat).compareTo(gross) != 0) {
            throw invalid("Suma netto i VAT w XML nie jest równa kwocie brutto P_15. Wymagana jest ręczna weryfikacja dokumentu.");
        }
        LocalDate saleDate = date(fa, "P_6", false);
        if (child(fa, "OkresFa") != null) {
            warnings.add("Faktura dotyczy okresu: " + text(child(fa, "OkresFa"), "P_6_Od", true)
                    + " – " + text(child(fa, "OkresFa"), "P_6_Do", true) + ". Datę sprzedaży uzupełnij po sprawdzeniu dokumentu.");
        }
        if (saleDate == null) warnings.add("Brak pojedynczej daty sprzedaży. Pole pozostanie puste.");
        Element payment = child(fa, "Platnosc");
        List<Element> terms = children(payment, "TerminPlatnosci");
        LocalDate dueDate = null;
        if (terms.size() == 1) dueDate = date(terms.get(0), "Termin", false);
        if (terms.size() > 1) warnings.add("XML zawiera kilka terminów płatności. Wybierz właściwy termin w formularzu.");
        else if (dueDate == null) warnings.add("Brak jednoznacznej daty płatności. Termin opisowy nie jest przeliczany automatycznie.");
        if (payment != null) warnings.add("Informacje o zapłacie z XML nie tworzą płatności w aplikacji. Przelewy uzupełnij na podstawie potwierdzeń.");
        if (child(fa, "Rozliczenie") != null) warnings.add("XML zawiera dodatkowe obciążenia lub odliczenia. Nie są importowane jako płatności ani potrącenia.");
        if ("ZAL".equals(kind)) warnings.add("Faktura zaliczkowa: importowana jest kwota tej zaliczki, bez wartości całego zamówienia. Nie rozlicza to późniejszej faktury końcowej.");
        Set<String> rates = new LinkedHashSet<>();
        boolean missingRate = false;
        for (Element row : children(fa, "FaWiersz")) {
            String rate = text(row, "P_12", false);
            if (rate == null) missingRate = true;
            else {
                if (rate.length() > 20) throw invalid("Stawka VAT przekracza długość obsługiwaną przez formularz.");
                rates.add(rate.toUpperCase(Locale.ROOT));
            }
        }
        if (rates.isEmpty() || missingRate) warnings.add("Nie odczytano wszystkich stawek VAT z pozycji faktury. Uzupełnij stawki w formularzu; nie są zgadywane z podsumowania.");
        String number = text(fa, "P_2", true);
        if (number.length() > 255) throw invalid("Numer faktury ma więcej niż 255 znaków.");
        return new KsefInvoicePreview("FA(" + version + ")", kind, number,
                date(fa, "P_1", true), saleDate, dueDate, money(net), money(vat), money(gross), "PLN",
                List.copyOf(rates), party(required(root, "Podmiot1")), party(required(root, "Podmiot2")), List.copyOf(warnings));
    }

    private Element read(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "100");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            builder.setEntityResolver((publicId, systemId) -> { throw new SAXException("External entities disabled"); });
            return builder.parse(new ByteArrayInputStream(xml)).getDocumentElement();
        } catch (ParserConfigurationException exception) {
            throw new IllegalStateException("Nie można bezpiecznie uruchomić parsera XML.", exception);
        } catch (SAXException | IOException exception) {
            throw invalid("Nieprawidłowy XML. Plik musi zawierać jedną fakturę KSeF, bez DTD i zewnętrznych encji.");
        }
    }

    private static Party party(Element element) {
        Element data = required(element, "DaneIdentyfikacyjne");
        String name = text(data, "Nazwa", false);
        String nip = text(data, "NIP", false);
        if (name != null && name.length() > 512) throw invalid("Nazwa podmiotu w XML jest zbyt długa.");
        if (nip != null && !nip.matches("[0-9]{10}")) throw invalid("NIP podmiotu w XML musi zawierać 10 cyfr.");
        return new Party(name, nip);
    }

    private static BigDecimal amount(Element parent, String name) {
        String text = text(parent, name, true);
        if (!text.matches("[0-9]{1,16}(\\.[0-9]{1,2})?")) throw invalid("Nieprawidłowa lub ujemna kwota " + name + ". Oczekiwano maksymalnie dwóch miejsc po kropce.");
        return new BigDecimal(text);
    }

    private static String money(BigDecimal amount) { return amount.setScale(2).toPlainString(); }

    private static LocalDate date(Element parent, String name, boolean mandatory) {
        String text = text(parent, name, mandatory);
        if (text == null) return null;
        try {
            if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeParseException("format", text, 0);
            return LocalDate.parse(text);
        } catch (DateTimeParseException exception) { throw invalid("Nieprawidłowa data " + name + "."); }
    }

    private static String text(Element parent, String name, boolean mandatory) {
        Element element = child(parent, name);
        if (element == null) {
            if (mandatory) throw invalid("Brak pola " + name + " w XML faktury.");
            return null;
        }
        String result = value(element);
        if (result.isBlank()) throw invalid("Puste pole " + name + " w XML faktury.");
        return result;
    }

    private static String value(Element element) {
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element) throw invalid("Pole " + element.getLocalName() + " zawiera zagnieżdżone elementy zamiast tekstu.");
        }
        return element.getTextContent().strip();
    }

    private static Element required(Element parent, String name) {
        Element element = child(parent, name);
        if (element == null) throw invalid("Brak elementu " + name + " w XML faktury.");
        return element;
    }

    private static Element child(Element parent, String name) {
        List<Element> elements = children(parent, name);
        if (elements.size() > 1) throw invalid("Pole " + name + " występuje wielokrotnie w XML faktury.");
        return elements.isEmpty() ? null : elements.get(0);
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        if (parent == null) return result;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getLocalName())) {
                if (!parent.getNamespaceURI().equals(element.getNamespaceURI())) throw invalid("Nieprawidłowa przestrzeń nazw pola " + name + ".");
                result.add(element);
            }
        }
        return result;
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
