package pl.company.settlements.invoice.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.company.settlements.invoice.model.MainContractorInvoiceCreateRequest;
import pl.company.settlements.invoice.model.MainContractorInvoiceResponse;
import pl.company.settlements.invoice.model.MainContractorInvoiceUpdateRequest;
import pl.company.settlements.invoice.service.MainContractorInvoiceService;

import java.util.List;

@RestController
@RequestMapping("/api/main-contractor-invoices")
@RequiredArgsConstructor
public class MainContractorInvoiceController {

    private final MainContractorInvoiceService service;

    @GetMapping("/{id}")
    public MainContractorInvoiceResponse getById(
            @PathVariable Long id
    ) {
        return service.getById(id);
    }

    @GetMapping("/main-contract/{mainContractId}")
    public List<MainContractorInvoiceResponse>
    findAllByMainContractId(
            @PathVariable Long mainContractId
    ) {
        return service.findAllByMainContractId(
                mainContractId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MainContractorInvoiceResponse create(
            @Valid
            @RequestBody
            MainContractorInvoiceCreateRequest request
    ) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public MainContractorInvoiceResponse update(
            @PathVariable Long id,

            @Valid
            @RequestBody
            MainContractorInvoiceUpdateRequest request
    ) {
        return service.update(
                id,
                request
        );
    }
}