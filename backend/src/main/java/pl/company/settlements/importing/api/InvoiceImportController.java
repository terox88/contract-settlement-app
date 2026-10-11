package pl.company.settlements.importing.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.company.settlements.importing.model.ParsedInvoice;
import pl.company.settlements.importing.service.DocumentImportService;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceImportController {

    private final DocumentImportService importService;

    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ParsedInvoice importInvoice(
            @RequestPart("file")
            MultipartFile file
    ) {

        return importService.parseInvoice(
                file
        );
    }
}