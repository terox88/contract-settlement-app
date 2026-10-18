package pl.company.settlements.invoice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.company.settlements.invoice.model.MainInvoiceReview;
import pl.company.settlements.invoice.service.MainInvoiceReviewService;
import java.util.List;

@RestController
public class MainInvoiceReviewController {
    private final MainInvoiceReviewService service;
    public MainInvoiceReviewController(MainInvoiceReviewService service) { this.service = service; }
    public record Complete(@NotBlank @Pattern(regexp = "[0-9a-f]{64}") String fingerprint,
                           boolean documentsNotRequired, @Size(max = 1000) String reason, boolean checked) { }
    @GetMapping("/api/main-contracts/{id}/invoice-reviews")
    public List<MainInvoiceReview> list(@PathVariable Long id) { return service.list(id); }
    @GetMapping("/api/main-contractor-invoices/{id}/review")
    public MainInvoiceReview get(@PathVariable Long id) { return service.get(id); }
    @PostMapping("/api/main-contractor-invoices/{id}/review")
    public MainInvoiceReview complete(@PathVariable Long id, @Valid @RequestBody Complete request) {
        return service.complete(id, request.fingerprint(), request.documentsNotRequired(), request.reason(), request.checked());
    }
    @DeleteMapping("/api/main-contractor-invoices/{id}/review")
    public MainInvoiceReview reopen(@PathVariable Long id) { return service.reopen(id); }
}
