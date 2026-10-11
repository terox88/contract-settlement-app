package pl.company.settlements.importing.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pl.company.settlements.importing.model.ParsedInvoice;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class DocumentImportService {

    private final List<InvoiceParser> parsers;

    public DocumentImportService(
            List<InvoiceParser> parsers
    ) {
        this.parsers = parsers;
    }

    public ParsedInvoice parseInvoice(
            MultipartFile file
    ) {

        if (file == null
                || file.isEmpty()) {

            throw new IllegalArgumentException(
                    "Invoice file cannot be empty"
            );
        }

        InvoiceParser parser =
                parsers.stream()
                        .filter(candidate ->
                                candidate.supports(
                                        file.getContentType(),
                                        file.getOriginalFilename()
                                )
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Unsupported invoice format"
                                )
                        );

        try (
                InputStream inputStream =
                        file.getInputStream()
        ) {

            return parser.parse(
                    inputStream
            );

        } catch (IOException exception) {

            throw new IllegalArgumentException(
                    "Could not read uploaded invoice file",
                    exception
            );
        }
    }
}