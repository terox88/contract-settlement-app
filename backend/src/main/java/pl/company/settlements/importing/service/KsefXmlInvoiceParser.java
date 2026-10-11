package pl.company.settlements.importing.service;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import pl.company.settlements.importing.model.ParsedInvoice;
import pl.company.settlements.importing.model.PartyData;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class KsefXmlInvoiceParser
        implements InvoiceParser {

    private static final Pattern NET_AMOUNT_FIELD =
            Pattern.compile(
                    "P_13_\\d+(?:_\\d+)*"
            );

    private static final Pattern VAT_AMOUNT_FIELD =
            Pattern.compile(
                    "P_14_\\d+"
            );

    @Override
    public boolean supports(
            String contentType,
            String filename
    ) {

        boolean xmlFilename =
                filename != null
                        && filename
                        .toLowerCase(Locale.ROOT)
                        .endsWith(".xml");

        boolean xmlContentType =
                contentType != null
                        && (
                        contentType.equalsIgnoreCase(
                                "application/xml"
                        )
                                || contentType.equalsIgnoreCase(
                                "text/xml"
                        )
                                || contentType
                                .toLowerCase(Locale.ROOT)
                                .contains("+xml")
                );

        return xmlFilename || xmlContentType;
    }

    @Override
    public ParsedInvoice parse(
            InputStream inputStream
    ) {

        Document document =
                parseDocument(
                        inputStream
                );

        Element root =
                document.getDocumentElement();

        if (!"Faktura".equals(
                localName(root)
        )) {

            throw new IllegalArgumentException(
                    "XML is not a KSeF invoice"
            );
        }

        Element header =
                requiredChild(
                        root,
                        "Naglowek"
                );

        validateFa3(
                header
        );

        List<String> warnings =
                new ArrayList<>();

        PartyData seller =
                parseSeller(
                        requiredChild(
                                root,
                                "Podmiot1"
                        )
                );

        PartyData buyer =
                parseBuyer(
                        requiredChild(
                                root,
                                "Podmiot2"
                        ),
                        warnings
                );

        Element fa =
                requiredChild(
                        root,
                        "Fa"
                );

        String invoiceNumber =
                requiredText(
                        fa,
                        "P_2"
                );

        LocalDate issueDate =
                parseRequiredDate(
                        requiredText(
                                fa,
                                "P_1"
                        ),
                        "P_1"
                );

        LocalDate saleDate =
                resolveSaleDate(
                        fa,
                        warnings
                );

        LocalDate dueDate =
                resolveDueDate(
                        fa,
                        warnings
                );

        String currency =
                requiredText(
                        fa,
                        "KodWaluty"
                ).toUpperCase(Locale.ROOT);

        String invoiceType =
                requiredText(
                        fa,
                        "RodzajFaktury"
                );

        if (!"VAT".equals(
                invoiceType
        )) {

            warnings.add(
                    "Rodzaj faktury KSeF: "
                            + invoiceType
                            + ". Przed zapisem sprawdź dane ręcznie."
            );
        }

        BigDecimal netAmount =
                sumDirectAmountFields(
                        fa,
                        NET_AMOUNT_FIELD
                );

        BigDecimal vatAmount =
                sumDirectAmountFields(
                        fa,
                        VAT_AMOUNT_FIELD
                );

        BigDecimal grossAmount =
                parseRequiredAmount(
                        requiredText(
                                fa,
                                "P_15"
                        ),
                        "P_15"
                );

        List<String> vatRates =
                extractVatRates(
                        fa
                );

        BigDecimal vatRate =
                resolveSingleVatRate(
                        vatRates,
                        warnings
                );

        if (netAmount == null) {
            warnings.add(
                    "Nie udało się jednoznacznie odczytać kwoty netto."
            );
        }

        if (vatAmount == null) {

            vatAmount =
                    BigDecimal.ZERO;

            warnings.add(
                    "W XML nie znaleziono sumarycznej kwoty VAT. "
                            + "Przyjęto 0,00 - sprawdź dane przed zapisem."
            );
        }

        return new ParsedInvoice(
                "FA(3)",
                invoiceType,
                invoiceNumber,
                issueDate,
                saleDate,
                dueDate,
                seller,
                buyer,
                netAmount,
                vatAmount,
                grossAmount,
                vatRate,
                vatRates,
                vatRates.size() > 1,
                currency,
                List.copyOf(
                        warnings
                )
        );
    }

    private Document parseDocument(
            InputStream inputStream
    ) {

        if (inputStream == null) {
            throw new IllegalArgumentException(
                    "Input stream cannot be null"
            );
        }

        try {

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory
                            .newInstance();

            factory.setNamespaceAware(
                    true
            );

            factory.setFeature(
                    XMLConstants.FEATURE_SECURE_PROCESSING,
                    true
            );

            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true
            );

            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false
            );

            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false
            );

            factory.setFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false
            );

            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    ""
            );

            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    ""
            );

            DocumentBuilder builder =
                    factory.newDocumentBuilder();

            return builder.parse(
                    inputStream
            );

        } catch (
                ParserConfigurationException
                | SAXException
                | IOException exception
        ) {

            throw new IllegalArgumentException(
                    "Invalid KSeF XML file",
                    exception
            );
        }
    }

    private void validateFa3(
            Element header
    ) {

        Element code =
                requiredChild(
                        header,
                        "KodFormularza"
                );

        String systemCode =
                code.getAttribute(
                        "kodSystemowy"
                );

        String variant =
                requiredText(
                        header,
                        "WariantFormularza"
                );

        String normalizedCode =
                systemCode
                        .replace(" ", "")
                        .toUpperCase(Locale.ROOT);

        if (!"FA(3)".equals(
                normalizedCode
        )
                || !"3".equals(
                variant
        )) {

            throw new IllegalArgumentException(
                    "Unsupported KSeF schema. "
                            + "Expected FA(3), got: "
                            + systemCode
                            + ", variant "
                            + variant
            );
        }
    }

    private PartyData parseSeller(
            Element sellerElement
    ) {

        Element identification =
                requiredChild(
                        sellerElement,
                        "DaneIdentyfikacyjne"
                );

        String taxId =
                requiredText(
                        identification,
                        "NIP"
                );

        String name =
                requiredText(
                        identification,
                        "Nazwa"
                );

        return new PartyData(
                name,
                taxId
        );
    }

    private PartyData parseBuyer(
            Element buyerElement,
            List<String> warnings
    ) {

        Element identification =
                requiredChild(
                        buyerElement,
                        "DaneIdentyfikacyjne"
                );

        String name =
                optionalText(
                        identification,
                        "Nazwa"
                );

        String taxId =
                optionalText(
                        identification,
                        "NIP"
                );

        if (taxId == null) {
            warnings.add(
                    "Nabywca nie posiada NIP w polu Podmiot2."
            );
        }

        return new PartyData(
                name,
                taxId
        );
    }

    private LocalDate resolveSaleDate(
            Element fa,
            List<String> warnings
    ) {

        String commonSaleDate =
                optionalText(
                        fa,
                        "P_6"
                );

        if (commonSaleDate != null) {

            return parseRequiredDate(
                    commonSaleDate,
                    "P_6"
            );
        }

        Set<LocalDate> rowDates =
                new LinkedHashSet<>();

        for (Element row :
                directChildren(
                        fa,
                        "FaWiersz"
                )) {

            String value =
                    optionalText(
                            row,
                            "P_6A"
                    );

            if (value != null) {

                rowDates.add(
                        parseRequiredDate(
                                value,
                                "P_6A"
                        )
                );
            }
        }

        if (rowDates.size() == 1) {
            return rowDates.iterator()
                    .next();
        }

        if (rowDates.size() > 1) {

            warnings.add(
                    "Faktura zawiera różne daty sprzedaży "
                            + "dla poszczególnych pozycji. "
                            + "Pole saleDate pozostawiono puste."
            );
        }

        return null;
    }

    private LocalDate resolveDueDate(
            Element fa,
            List<String> warnings
    ) {

        Element payment =
                firstChild(
                        fa,
                        "Platnosc"
                );

        if (payment == null) {
            return null;
        }

        Set<LocalDate> dates =
                new LinkedHashSet<>();

        for (Element paymentTerm :
                directChildren(
                        payment,
                        "TerminPlatnosci"
                )) {

            String value =
                    optionalText(
                            paymentTerm,
                            "Termin"
                    );

            if (value != null) {

                dates.add(
                        parseRequiredDate(
                                value,
                                "TerminPlatnosci/Termin"
                        )
                );
            }
        }

        if (dates.size() == 1) {
            return dates.iterator()
                    .next();
        }

        if (dates.size() > 1) {

            warnings.add(
                    "Faktura zawiera więcej niż jeden termin płatności. "
                            + "Pole dueDate pozostawiono puste."
            );
        }

        return null;
    }

    private List<String> extractVatRates(
            Element fa
    ) {

        Set<String> rates =
                new LinkedHashSet<>();

        for (Element row :
                directChildren(
                        fa,
                        "FaWiersz"
                )) {

            String vatRate =
                    optionalText(
                            row,
                            "P_12"
                    );

            if (vatRate != null) {
                rates.add(
                        vatRate.trim()
                );
            }
        }

        return List.copyOf(
                rates
        );
    }

    private BigDecimal resolveSingleVatRate(
            List<String> rates,
            List<String> warnings
    ) {

        if (rates.isEmpty()) {

            warnings.add(
                    "Nie znaleziono jednoznacznej stawki VAT w pozycjach faktury."
            );

            return null;
        }

        if (rates.size() > 1) {

            warnings.add(
                    "Faktura zawiera wiele stawek VAT: "
                            + String.join(
                            ", ",
                            rates
                    )
                            + ". Obecny model faktury pozwala na jedną stawkę."
            );

            return null;
        }

        String rate =
                rates.getFirst();

        try {
            return new BigDecimal(
                    rate
            );

        } catch (NumberFormatException exception) {

            warnings.add(
                    "Stawka VAT '"
                            + rate
                            + "' nie jest stawką liczbową."
            );

            return null;
        }
    }

    private BigDecimal sumDirectAmountFields(
            Element parent,
            Pattern fieldPattern
    ) {

        BigDecimal sum =
                BigDecimal.ZERO;

        boolean found =
                false;

        NodeList children =
                parent.getChildNodes();

        for (int i = 0;
             i < children.getLength();
             i++) {

            Node node =
                    children.item(i);

            if (!(node instanceof Element element)) {
                continue;
            }

            String name =
                    localName(
                            element
                    );

            if (!fieldPattern
                    .matcher(name)
                    .matches()) {
                continue;
            }

            String value =
                    element.getTextContent();

            if (value == null
                    || value.isBlank()) {
                continue;
            }

            found =
                    true;

            sum =
                    sum.add(
                            parseRequiredAmount(
                                    value,
                                    name
                            )
                    );
        }

        return found
                ? sum
                : null;
    }

    private BigDecimal parseRequiredAmount(
            String value,
            String fieldName
    ) {

        try {

            return new BigDecimal(
                    value.trim()
            );

        } catch (NumberFormatException exception) {

            throw new IllegalArgumentException(
                    "Invalid amount in KSeF field "
                            + fieldName
                            + ": "
                            + value
            );
        }
    }

    private LocalDate parseRequiredDate(
            String value,
            String fieldName
    ) {

        try {

            return LocalDate.parse(
                    value.trim()
            );

        } catch (DateTimeParseException exception) {

            throw new IllegalArgumentException(
                    "Invalid date in KSeF field "
                            + fieldName
                            + ": "
                            + value
            );
        }
    }

    private Element requiredChild(
            Element parent,
            String name
    ) {

        Element element =
                firstChild(
                        parent,
                        name
                );

        if (element == null) {

            throw new IllegalArgumentException(
                    "Missing required KSeF element: "
                            + name
            );
        }

        return element;
    }

    private String requiredText(
            Element parent,
            String name
    ) {

        String value =
                optionalText(
                        parent,
                        name
                );

        if (value == null) {

            throw new IllegalArgumentException(
                    "Missing required KSeF field: "
                            + name
            );
        }

        return value;
    }

    private String optionalText(
            Element parent,
            String name
    ) {

        Element element =
                firstChild(
                        parent,
                        name
                );

        if (element == null) {
            return null;
        }

        String value =
                element.getTextContent();

        if (value == null) {
            return null;
        }

        String trimmed =
                value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }

    private Element firstChild(
            Element parent,
            String name
    ) {

        NodeList children =
                parent.getChildNodes();

        for (int i = 0;
             i < children.getLength();
             i++) {

            Node node =
                    children.item(i);

            if (node instanceof Element element
                    && name.equals(
                    localName(element)
            )) {

                return element;
            }
        }

        return null;
    }

    private List<Element> directChildren(
            Element parent,
            String name
    ) {

        List<Element> result =
                new ArrayList<>();

        NodeList children =
                parent.getChildNodes();

        for (int i = 0;
             i < children.getLength();
             i++) {

            Node node =
                    children.item(i);

            if (node instanceof Element element
                    && name.equals(
                    localName(element)
            )) {

                result.add(
                        element
                );
            }
        }

        return result;
    }

    private String localName(
            Element element
    ) {

        String localName =
                element.getLocalName();

        return localName != null
                ? localName
                : element.getNodeName();
    }
}