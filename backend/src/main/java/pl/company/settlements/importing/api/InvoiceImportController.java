package pl.company.settlements.importing.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.company.settlements.importing.model.ParsedInvoice;
import pl.company.settlements.importing.service.DocumentImportService;

import java.io.IOException;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceImportController {
    private final DocumentImportService importService;

    public InvoiceImportController(DocumentImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/import")
    public ResponseEntity<ParsedInvoice> importInvoice(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(importService.parseInvoice(file));
    }
}
