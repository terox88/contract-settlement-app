package pl.company.settlements.contract.api;

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
import pl.company.settlements.contract.model.ContractAmendmentCreateRequest;
import pl.company.settlements.contract.model.ContractAmendmentResponse;
import pl.company.settlements.contract.model.ContractAmendmentUpdateRequest;
import pl.company.settlements.contract.service.ContractAmendmentService;

import java.util.List;

@RestController
@RequestMapping("/api/contract-amendments")
@RequiredArgsConstructor
public class ContractAmendmentController {

    private final ContractAmendmentService service;

    @GetMapping("/{id}")
    public ContractAmendmentResponse getById(
            @PathVariable Long id
    ) {
        return service.getById(id);
    }

    @GetMapping("/contract/{contractId}")
    public List<ContractAmendmentResponse> findAllByContractId(
            @PathVariable Long contractId
    ) {
        return service.findAllByContractId(
                contractId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractAmendmentResponse create(
            @Valid
            @RequestBody ContractAmendmentCreateRequest request
    ) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ContractAmendmentResponse update(
            @PathVariable Long id,
            @Valid
            @RequestBody ContractAmendmentUpdateRequest request
    ) {
        return service.update(
                id,
                request
        );
    }
}