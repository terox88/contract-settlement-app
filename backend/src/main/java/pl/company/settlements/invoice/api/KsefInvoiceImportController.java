package pl.company.settlements.invoice.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.company.settlements.invoice.model.KsefInvoicePreview;
import pl.company.settlements.invoice.model.MainContractorInvoiceCreateRequest;
import pl.company.settlements.invoice.model.MainContractorInvoiceResponse;
import pl.company.settlements.invoice.model.MainContractorInvoiceUpdateRequest;
import pl.company.settlements.invoice.model.MainInvoiceSettlementResponse;
import pl.company.settlements.invoice.model.MainInvoiceWithSubcontractorInvoiceRequest;
import pl.company.settlements.invoice.model.SubcontractorInvoiceCreateRequest;
import pl.company.settlements.invoice.model.SubcontractorInvoiceResponse;
import pl.company.settlements.invoice.model.SubcontractorInvoiceUpdateRequest;
import pl.company.settlements.invoice.service.KsefInvoiceImportService;

@RestController
@RequestMapping("/api/invoice-imports/ksef")
public class KsefInvoiceImportController {
    private final KsefInvoiceImportService service;

    public KsefInvoiceImportController(KsefInvoiceImportService service) { this.service = service; }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KsefInvoicePreview preview(@RequestPart("file") MultipartFile file) { return service.preview(file); }

    @PostMapping(value = "/main-invoices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MainContractorInvoiceResponse createMain(@Valid @RequestPart("invoice") MainContractorInvoiceCreateRequest invoice,
                                                   @RequestPart("file") MultipartFile file) {
        return service.createMain(invoice, file);
    }

    @PutMapping(value = "/main-invoices/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MainContractorInvoiceResponse updateMain(@PathVariable Long id,
                                                   @Valid @RequestPart("invoice") MainContractorInvoiceUpdateRequest invoice,
                                                   @RequestPart("file") MultipartFile file) {
        return service.updateMain(id, invoice, file);
    }

    @PostMapping(value = "/sub-invoices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SubcontractorInvoiceResponse createSub(@Valid @RequestPart("invoice") SubcontractorInvoiceCreateRequest invoice,
                                                 @RequestPart("file") MultipartFile file) {
        return service.createSub(invoice, file);
    }

    @PutMapping(value = "/sub-invoices/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SubcontractorInvoiceResponse updateSub(@PathVariable Long id,
                                                 @Valid @RequestPart("invoice") SubcontractorInvoiceUpdateRequest invoice,
                                                 @RequestPart("file") MultipartFile file) {
        return service.updateSub(id, invoice, file);
    }

    @PostMapping(value = "/settlements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MainInvoiceSettlementResponse createWithAssignment(
            @Valid @RequestPart("invoice") MainInvoiceWithSubcontractorInvoiceRequest invoice,
            @RequestPart("file") MultipartFile file) {
        return service.createWithAssignment(invoice, file);
    }
}
