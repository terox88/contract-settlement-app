package pl.company.settlements.invoice.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.company.settlements.invoice.model.SubcontractorInvoiceCreateRequest;
import pl.company.settlements.invoice.model.SubcontractorInvoiceFilter;
import pl.company.settlements.invoice.model.SubcontractorInvoiceResponse;
import pl.company.settlements.invoice.model.SubcontractorInvoiceUpdateRequest;
import pl.company.settlements.invoice.service.SubcontractorInvoiceService;

import java.util.List;

@RestController
@RequestMapping("/api/subcontractor-invoices")
@RequiredArgsConstructor
public class SubcontractorInvoiceController {

    private final SubcontractorInvoiceService service;

    @GetMapping("/{id}")
    public SubcontractorInvoiceResponse getById(
            @PathVariable Long id
    ) {
        return service.getById(id);
    }

    @GetMapping
    public List<SubcontractorInvoiceResponse> findAll(
            @ModelAttribute SubcontractorInvoiceFilter filter
    ) {
        return service.findAll(filter);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubcontractorInvoiceResponse create(
            @Valid
            @RequestBody
            SubcontractorInvoiceCreateRequest request
    ) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public SubcontractorInvoiceResponse update(
            @PathVariable Long id,

            @Valid
            @RequestBody
            SubcontractorInvoiceUpdateRequest request
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