package pl.company.settlements.importing.service;

import pl.company.settlements.importing.model.ParsedInvoice;
import java.io.InputStream;

public interface InvoiceParser {
    boolean supports(String contentType, String filename);
    ParsedInvoice parse(InputStream inputStream);
}
