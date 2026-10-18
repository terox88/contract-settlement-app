package pl.company.settlements.file.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.company.settlements.file.service.AttachmentMetadataService;

@RestController
public class AttachmentMetadataController {
    private final AttachmentMetadataService service;
    public AttachmentMetadataController(AttachmentMetadataService service) { this.service = service; }
    public record Rename(@NotBlank @Size(max = 255) String displayName, @NotNull @PositiveOrZero Long version) { }
    @PutMapping("/api/attachments/{id}/name")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rename(@PathVariable Long id, @Valid @RequestBody Rename request) { service.rename(id, request.displayName(), request.version()); }
}
