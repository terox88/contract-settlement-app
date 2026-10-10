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
import pl.company.settlements.invoice.model.SubcontractorStatementCreateRequest;
import pl.company.settlements.invoice.model.SubcontractorStatementResponse;
import pl.company.settlements.invoice.model.SubcontractorStatementUpdateRequest;
import pl.company.settlements.invoice.service.SubcontractorStatementService;

@RestController
@RequestMapping("/api/subcontractor-statements")
@RequiredArgsConstructor
public class SubcontractorStatementController {

    private final SubcontractorStatementService service;

    @GetMapping("/{id}")
    public SubcontractorStatementResponse getById(
            @PathVariable Long id
    ) {
        return service.getById(id);
    }

    @GetMapping("/subcontractor-invoice/{invoiceId}")
    public SubcontractorStatementResponse getBySubcontractorInvoice(
            @PathVariable Long invoiceId
    ) {
        return service.getBySubcontractorInvoiceId(
                invoiceId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubcontractorStatementResponse create(
            @Valid
            @RequestBody SubcontractorStatementCreateRequest request
    ) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public SubcontractorStatementResponse update(
            @PathVariable Long id,
            @Valid
            @RequestBody SubcontractorStatementUpdateRequest request
    ) {
        return service.update(
                id,
                request
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id
    ) {
        service.delete(id);
    }
}