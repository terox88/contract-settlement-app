package pl.company.settlements.contract.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.company.settlements.contract.model.ContractPenaltyCreateRequest;
import pl.company.settlements.contract.model.ContractPenaltyResponse;
import pl.company.settlements.contract.model.ContractPenaltyUpdateRequest;
import pl.company.settlements.contract.service.ContractPenaltyService;

import java.util.List;

@RestController
@RequestMapping("/api/contract-penalties")
@RequiredArgsConstructor
public class ContractPenaltyController {

    private final ContractPenaltyService service;

    @GetMapping("/{id}")
    public ContractPenaltyResponse getById(
            @PathVariable Long id
    ) {
        return service.getById(id);
    }

    @GetMapping("/contract/{contractId}")
    public List<ContractPenaltyResponse> findAllByContractId(
            @PathVariable Long contractId
    ) {
        return service.findAllByContractId(
                contractId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractPenaltyResponse create(
            @Valid
            @RequestBody ContractPenaltyCreateRequest request
    ) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ContractPenaltyResponse update(
            @PathVariable Long id,
            @Valid
            @RequestBody ContractPenaltyUpdateRequest request
    ) {
        return service.update(
                id,
                request
        );
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}