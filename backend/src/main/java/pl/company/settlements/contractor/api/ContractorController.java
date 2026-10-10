package pl.company.settlements.contractor.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.company.settlements.contractor.model.ContractorCreateRequest;
import pl.company.settlements.contractor.model.ContractorResponse;
import pl.company.settlements.contractor.model.ContractorUpdateRequest;
import pl.company.settlements.contractor.service.ContractorService;

@RestController
@RequestMapping("/api/contractors")
@RequiredArgsConstructor
public class ContractorController {

    private final ContractorService service;

    @GetMapping("/{id}")
    public ContractorResponse getById(
            @PathVariable Long id
    ) {
        return service.getById(id);
    }

    @GetMapping("/tax-id/{taxId}")
    public ResponseEntity<ContractorResponse> findByTaxId(
            @PathVariable String taxId
    ) {
        return ResponseEntity.of(
                service.findByTaxId(taxId)
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractorResponse create(
            @Valid
            @RequestBody ContractorCreateRequest request
    ) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ContractorResponse update(
            @PathVariable Long id,
            @Valid
            @RequestBody ContractorUpdateRequest request
    ) {
        return service.update(
                id,
                request
        );
    }
}