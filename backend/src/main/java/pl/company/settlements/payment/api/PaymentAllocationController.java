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
import pl.company.settlements.payment.model.PaymentAllocationCreateRequest;
import pl.company.settlements.payment.model.PaymentAllocationResponse;
import pl.company.settlements.payment.model.PaymentAllocationUpdateRequest;
import pl.company.settlements.payment.service.PaymentAllocationService;

import java.util.List;

@RestController
@RequestMapping("/api/payment-allocations")
@RequiredArgsConstructor
public class PaymentAllocationController {

    private final PaymentAllocationService service;

    @GetMapping("/{id}")
    public PaymentAllocationResponse getById(
            @PathVariable Long id
    ) {
        return service.getById(id);
    }

    @GetMapping("/payment/{paymentId}")
    public List<PaymentAllocationResponse> findByPayment(
            @PathVariable Long paymentId
    ) {
        return service.findAllByPaymentId(
                paymentId
        );
    }

    @GetMapping("/subcontractor-invoice/{invoiceId}")
    public List<PaymentAllocationResponse> findBySubcontractorInvoice(
            @PathVariable Long invoiceId
    ) {
        return service.findAllBySubcontractorInvoiceId(
                invoiceId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentAllocationResponse create(
            @Valid
            @RequestBody PaymentAllocationCreateRequest request
    ) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public PaymentAllocationResponse update(
            @PathVariable Long id,
            @Valid
            @RequestBody PaymentAllocationUpdateRequest request
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