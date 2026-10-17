package pl.company.settlements.payment.api;

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
import pl.company.settlements.payment.model.PaymentCreateRequest;
import pl.company.settlements.payment.model.PaymentResponse;
import pl.company.settlements.payment.model.PaymentUpdateRequest;
import pl.company.settlements.payment.service.PaymentService;
import pl.company.settlements.payment.service.PaymentWorkflowService;
import pl.company.settlements.payment.model.PaymentWithAllocationsRequest;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;
    private final PaymentWorkflowService workflowService;

    @GetMapping("/{id}")
    public PaymentResponse getById(
            @PathVariable Long id
    ) {
        return service.getById(id);
    }

    @GetMapping
    public List<PaymentResponse> findAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(
            @Valid
            @RequestBody PaymentCreateRequest request
    ) {
        return service.create(request);
    }

    @PostMapping("/with-allocations")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createWithAllocations(@Valid @RequestBody PaymentWithAllocationsRequest request) {
        return workflowService.create(request);
    }

    @PutMapping("/{id}")
    public PaymentResponse update(
            @PathVariable Long id,
            @Valid
            @RequestBody PaymentUpdateRequest request
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