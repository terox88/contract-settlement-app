package pl.company.settlements.importing.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pl.company.settlements.importing.model.ParsedInvoice;

import java.io.IOException;
import java.util.List;

@Service
public class DocumentImportService {
    private final List<InvoiceParser> parsers;

    public DocumentImportService(List<InvoiceParser> parsers) {
        this.parsers = parsers;
    }

    public ParsedInvoice parseInvoice(MultipartFile file) throws IOException {
        InvoiceParser parser = parsers.stream()
                .filter(p -> p.supports(file.getContentType(), file.getOriginalFilename()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported invoice format"));

        try (var input = file.getInputStream()) {
            return parser.parse(input);
        }
    }
}
