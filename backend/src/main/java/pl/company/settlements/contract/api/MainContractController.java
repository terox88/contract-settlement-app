package pl.company.settlements.contract.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.company.settlements.contract.model.MainContractCreateRequest;
import pl.company.settlements.contract.model.MainContractFilter;
import pl.company.settlements.contract.model.MainContractResponse;
import pl.company.settlements.contract.model.MainContractUpdateRequest;
import pl.company.settlements.contract.model.MainContractAdministrationResponse;
import pl.company.settlements.contract.model.SecurityReturnRequest;
import pl.company.settlements.contract.service.MainContractService;

import java.util.List;

@RestController
@RequestMapping("/api/main-contracts")
@RequiredArgsConstructor
public class MainContractController {

    private final MainContractService service;

    @GetMapping("/{id}")
    public MainContractResponse getById(
            @PathVariable Long id
    ) {
        return service.getById(id);
    }

    @GetMapping
    public List<MainContractResponse> findAll(
            @ModelAttribute MainContractFilter filter
    ) {
        return service.findAll(filter);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MainContractResponse create(
            @Valid
            @RequestBody MainContractCreateRequest request
    ) {
        return service.create(request);
    }

    @PatchMapping("/{id}/security-return")
    public MainContractAdministrationResponse updateSecurityReturn(
            @PathVariable Long id,
            @Valid @RequestBody SecurityReturnRequest request
    ) {
        return service.updateSecurityReturn(id, request);
    }

    @PutMapping("/{id}")
    public MainContractResponse update(
            @PathVariable Long id,
            @Valid
            @RequestBody MainContractUpdateRequest request
    ) {
        return service.update(
                id,
                request
        );
    }
}
