package pl.company.settlements.invoice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.company.settlements.invoice.model.DeductionReview;
import pl.company.settlements.invoice.service.DeductionReviewService;

@RestController
@RequestMapping("/api/subcontractor-invoices/{id}/deduction-review")
public class DeductionReviewController {
    private final DeductionReviewService service;
    public DeductionReviewController(DeductionReviewService service) { this.service = service; }
    public record Confirmation(@NotBlank @Pattern(regexp = "[0-9a-f]{64}") String fingerprint) { }
    @GetMapping public DeductionReview get(@PathVariable Long id) { return service.get(id); }
    @PostMapping public DeductionReview confirm(@PathVariable Long id, @Valid @RequestBody Confirmation request) { return service.confirm(id, request.fingerprint()); }
    @DeleteMapping public DeductionReview revoke(@PathVariable Long id) { return service.revoke(id); }
}
