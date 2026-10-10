package pl.company.settlements.task.api;

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
    public List<InvestmentTaskResponse> findAll() {
        return service.findAll();
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