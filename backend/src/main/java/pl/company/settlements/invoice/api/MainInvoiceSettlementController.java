package pl.company.settlements.invoice.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.company.settlements.invoice.model.MainInvoiceSettlementCreateRequest;
import pl.company.settlements.invoice.model.MainInvoiceWithSubcontractorInvoiceRequest;
import pl.company.settlements.invoice.model.MainInvoiceSettlementResponse;
import pl.company.settlements.invoice.model.MainInvoiceSettlementUpdateRequest;
import pl.company.settlements.invoice.service.MainInvoiceSettlementService;

import java.util.List;

@RestController
@RequestMapping("/api/main-invoice-settlements")
@RequiredArgsConstructor
public class MainInvoiceSettlementController {

    private final MainInvoiceSettlementService service;

    @GetMapping("/{id}")
    public MainInvoiceSettlementResponse getById(
            @PathVariable Long id
    ) {
        return service.getById(id);
    }

    @GetMapping("/main-invoice/{mainInvoiceId}")
    public List<MainInvoiceSettlementResponse> findByMainInvoice(
            @PathVariable Long mainInvoiceId
    ) {
        return service.findAllByMainContractorInvoiceId(
                mainInvoiceId
        );
    }

    @GetMapping("/subcontractor-invoice/{subcontractorInvoiceId}")
    public List<MainInvoiceSettlementResponse> findBySubcontractorInvoice(
            @PathVariable Long subcontractorInvoiceId
    ) {
        return service.findAllBySubcontractorInvoiceId(
                subcontractorInvoiceId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MainInvoiceSettlementResponse create(
            @Valid
            @RequestBody MainInvoiceSettlementCreateRequest request
    ) {
        return service.create(request);
    }

    @PostMapping("/with-new-invoice")
    @ResponseStatus(HttpStatus.CREATED)
    public MainInvoiceSettlementResponse createWithInvoice(
            @Valid @RequestBody MainInvoiceWithSubcontractorInvoiceRequest request) {
        return service.createWithInvoice(request);
    }

    @PutMapping("/{id}")
    public MainInvoiceSettlementResponse update(
            @PathVariable Long id,
            @Valid
            @RequestBody MainInvoiceSettlementUpdateRequest request
    ) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id
    ) {
        service.delete(id);
    }
}