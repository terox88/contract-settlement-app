package pl.company.settlements.contract.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.company.settlements.contract.model.SubcontractCreateRequest;
import pl.company.settlements.contract.model.SubcontractFilter;
import pl.company.settlements.contract.model.SubcontractResponse;
import pl.company.settlements.contract.model.SubcontractUpdateRequest;
import pl.company.settlements.contract.service.SubcontractService;

import java.util.List;

@RestController
@RequestMapping("/api/subcontracts")
@RequiredArgsConstructor
public class SubcontractController {

    private final SubcontractService service;

    @GetMapping("/{id}")
    public SubcontractResponse getById(
            @PathVariable Long id
    ) {
        return service.getById(id);
    }

    @GetMapping
    public List<SubcontractResponse> findAll(
            @ModelAttribute SubcontractFilter filter
    ) {
        return service.findAll(filter);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubcontractResponse create(
            @Valid
            @RequestBody SubcontractCreateRequest request
    ) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public SubcontractResponse update(
            @PathVariable Long id,
            @Valid
            @RequestBody SubcontractUpdateRequest request
    ) {
        return service.update(
                id,
                request
        );
    }
}