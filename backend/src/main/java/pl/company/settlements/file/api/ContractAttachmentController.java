package pl.company.settlements.file.api;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.file.model.ContractAttachmentResponse;
import pl.company.settlements.file.service.AttachmentZipWriter;
import pl.company.settlements.file.service.ContractAttachmentService;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
public class ContractAttachmentController {
    private final ContractAttachmentService service;
    public ContractAttachmentController(ContractAttachmentService service) { this.service = service; }
    @GetMapping("/api/main-contracts/{id}/attachment-workspace")
    public List<ContractAttachmentResponse> list(@PathVariable Long id) { return service.list(id); }
    @GetMapping("/api/main-contracts/{id}/attachment-workspace/zip")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable Long id, @RequestParam(required = false) AttachmentType type) {
        var entries = service.archive(id, type);
        String name = "umowa-" + id + "-" + (type == null ? "wszystkie" : type.name()) + ".zip";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(output -> AttachmentZipWriter.write(entries, output));
    }
}
