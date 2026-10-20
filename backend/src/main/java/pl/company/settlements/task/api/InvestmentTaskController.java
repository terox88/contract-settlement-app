package pl.company.settlements.task.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.company.settlements.task.domain.InvestmentTaskStatus;
import pl.company.settlements.task.model.InvestmentTaskCreateRequest;
import pl.company.settlements.task.model.InvestmentTaskResponse;
import pl.company.settlements.task.model.InvestmentTaskUpdateRequest;
import pl.company.settlements.task.service.InvestmentTaskService;

import java.util.List;

@RestController
@RequestMapping("/api/investment-tasks")
@RequiredArgsConstructor
public class InvestmentTaskController {

    private final InvestmentTaskService service;

    @GetMapping("/{id}")
    public InvestmentTaskResponse getById(
            @PathVariable Long id
    ) {
        return service.getById(id);
    }

    @GetMapping
    public List<InvestmentTaskResponse> findAll(
            @RequestParam(required = false)
            InvestmentTaskStatus status,
            @RequestParam(required = false) String implementer
    ) {
        return service.findAll(
                status, implementer
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvestmentTaskResponse create(
            @Valid
            @RequestBody InvestmentTaskCreateRequest request
    ) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public InvestmentTaskResponse update(
            @PathVariable Long id,
            @Valid
            @RequestBody InvestmentTaskUpdateRequest request
    ) {
        return service.update(id, request);
    }
}