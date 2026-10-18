package pl.company.settlements.file.api;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.file.domain.AttachmentOwner;
import pl.company.settlements.file.model.AttachmentTypeOption;
import java.util.Arrays;
import pl.company.settlements.file.model.AttachmentDownload;
import pl.company.settlements.file.model.AttachmentResponse;
import pl.company.settlements.file.service.DocumentAttachmentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AttachmentController {

    private final DocumentAttachmentService service;

    @GetMapping("/api/attachments/types")
    public List<AttachmentTypeOption> types(@RequestParam AttachmentOwner owner) {
        return Arrays.stream(AttachmentType.values()).filter(owner::supports)
                .map(type -> new AttachmentTypeOption(type, type.getLabel())).toList();
    }


    @PostMapping(
            value = "/api/contracts/{contractId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public AttachmentResponse uploadToContract(
            @PathVariable Long contractId,
            @RequestPart("file") MultipartFile file,
            @RequestParam AttachmentType type,
            @RequestParam(required = false)
            String description,
            @RequestParam(required = false)
            String pages
    ) {
        return service.uploadToContract(
                contractId,
                file,
                type,
                description,
                pages
        );
    }

    @GetMapping(
            "/api/contracts/{contractId}/attachments"
    )
    public List<AttachmentResponse> findByContract(
            @PathVariable Long contractId,
            @RequestParam(required = false)
            AttachmentType type
    ) {
        return service.findAllByContractId(
                contractId,
                type
        );
    }

    @PostMapping(
            value = "/api/contract-amendments/{amendmentId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public AttachmentResponse uploadToAmendment(
            @PathVariable Long amendmentId,
            @RequestPart("file") MultipartFile file,
            @RequestParam AttachmentType type,
            @RequestParam(required = false)
            String description,
            @RequestParam(required = false)
            String pages
    ) {
        return service.uploadToContractAmendment(
                amendmentId,
                file,
                type,
                description,
                pages
        );
    }

    @GetMapping(
            "/api/contract-amendments/{amendmentId}/attachments"
    )
    public List<AttachmentResponse> findByAmendment(
            @PathVariable Long amendmentId,
            @RequestParam(required = false)
            AttachmentType type
    ) {
        return service.findAllByContractAmendmentId(
                amendmentId,
                type
        );
    }

    @PostMapping(
            value = "/api/main-contractor-invoices/{invoiceId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public AttachmentResponse uploadToMainInvoice(
            @PathVariable Long invoiceId,
            @RequestPart("file") MultipartFile file,
            @RequestParam AttachmentType type,
            @RequestParam(required = false)
            String description,
            @RequestParam(required = false)
            String pages
    ) {
        return service.uploadToMainContractorInvoice(
                invoiceId,
                file,
                type,
                description,
                pages
        );
    }

    @GetMapping(
            "/api/main-contractor-invoices/{invoiceId}/attachments"
    )
    public List<AttachmentResponse> findByMainInvoice(
            @PathVariable Long invoiceId,
            @RequestParam(required = false)
            AttachmentType type
    ) {
        return service.findAllByMainContractorInvoiceId(
                invoiceId,
                type
        );
    }

    @PostMapping(
            value = "/api/subcontractor-invoices/{invoiceId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public AttachmentResponse uploadToSubcontractorInvoice(
            @PathVariable Long invoiceId,
            @RequestPart("file") MultipartFile file,
            @RequestParam AttachmentType type,
            @RequestParam(required = false)
            String description,
            @RequestParam(required = false)
            String pages
    ) {
        return service.uploadToSubcontractorInvoice(
                invoiceId,
                file,
                type,
                description,
                pages
        );
    }

    @GetMapping(
            "/api/subcontractor-invoices/{invoiceId}/attachments"
    )
    public List<AttachmentResponse> findBySubcontractorInvoice(
            @PathVariable Long invoiceId,
            @RequestParam(required = false)
            AttachmentType type
    ) {
        return service.findAllBySubcontractorInvoiceId(
                invoiceId,
                type
        );
    }

    @PostMapping(
            value = "/api/payments/{paymentId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public AttachmentResponse uploadToPayment(
            @PathVariable Long paymentId,
            @RequestPart("file") MultipartFile file,
            @RequestParam AttachmentType type,
            @RequestParam(required = false)
            String description,
            @RequestParam(required = false)
            String pages
    ) {
        return service.uploadToPayment(
                paymentId,
                file,
                type,
                description,
                pages
        );
    }

    @GetMapping(
            "/api/payments/{paymentId}/attachments"
    )
    public List<AttachmentResponse> findByPayment(
            @PathVariable Long paymentId,
            @RequestParam(required = false)
            AttachmentType type
    ) {
        return service.findAllByPaymentId(
                paymentId,
                type
        );
    }

    @PostMapping(
            value = "/api/subcontractor-statements/{statementId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public AttachmentResponse uploadToStatement(
            @PathVariable Long statementId,
            @RequestPart("file") MultipartFile file,
            @RequestParam AttachmentType type,
            @RequestParam(required = false)
            String description,
            @RequestParam(required = false)
            String pages
    ) {
        return service.uploadToSubcontractorStatement(
                statementId,
                file,
                type,
                description,
                pages
        );
    }

    @GetMapping(
            "/api/subcontractor-statements/{statementId}/attachments"
    )
    public List<AttachmentResponse> findByStatement(
            @PathVariable Long statementId,
            @RequestParam(required = false)
            AttachmentType type
    ) {
        return service.findAllBySubcontractorStatementId(
                statementId,
                type
        );
    }

    @GetMapping(
            "/api/attachments/{attachmentId}/download"
    )
    public ResponseEntity<Resource> download(
            @PathVariable Long attachmentId
    ) {

        AttachmentDownload download =
                service.download(
                        attachmentId
                );

        MediaType mediaType =
                resolveMediaType(
                        download.contentType()
                );

        ResponseEntity.BodyBuilder response =
                ResponseEntity.ok()
                        .contentType(mediaType)
                        .header("X-Content-Type-Options", "nosniff")
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition
                                        .attachment()
                                        .filename(
                                                download.originalFilename(),
                                                StandardCharsets.UTF_8
                                        )
                                        .build()
                                        .toString()
                        );

        if (download.size() != null) {
            response.contentLength(
                    download.size()
            );
        }

        return response.body(
                download.resource()
        );
    }

    @DeleteMapping(
            "/api/attachments/{attachmentId}"
    )
    public ResponseEntity<Void> delete(
            @PathVariable Long attachmentId
    ) {

        service.delete(
                attachmentId
        );

        return ResponseEntity.noContent()
                .build();
    }

    private MediaType resolveMediaType(
            String contentType
    ) {

        if (contentType == null
                || contentType.isBlank()) {

            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            return MediaType.parseMediaType(
                    contentType
            );
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    @PostMapping(
            value = "/api/subcontractor-invoices/{invoiceId}/attachments/split",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public List<AttachmentResponse>
    splitSubcontractorInvoiceAttachment(
            @PathVariable Long invoiceId,
            @RequestPart("file") MultipartFile file,
            @RequestParam AttachmentType type,
            @RequestParam(required = false)
            String description,
            @RequestParam String parts
    ) {

        return service.splitToSubcontractorInvoice(
                invoiceId,
                file,
                type,
                description,
                parts
        );
    }

    @PostMapping(
            value = "/api/main-contractor-invoices/{invoiceId}/attachments/split",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public List<AttachmentResponse>
    splitMainContractorInvoiceAttachment(
            @PathVariable Long invoiceId,
            @RequestPart("file") MultipartFile file,
            @RequestParam AttachmentType type,
            @RequestParam(required = false)
            String description,
            @RequestParam String parts
    ) {

        return service.splitToMainContractorInvoice(
                invoiceId,
                file,
                type,
                description,
                parts
        );
    }

    @PostMapping(
            value = "/api/contracts/{contractId}/attachments/split",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public List<AttachmentResponse>
    splitContractAttachment(
            @PathVariable Long contractId,
            @RequestPart("file") MultipartFile file,
            @RequestParam AttachmentType type,
            @RequestParam(required = false)
            String description,
            @RequestParam String parts
    ) {

        return service.splitToContract(
                contractId,
                file,
                type,
                description,
                parts
        );
    }

    @PostMapping(
            value = "/api/contract-amendments/{amendmentId}/attachments/split",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public List<AttachmentResponse>
    splitContractAmendmentAttachment(
            @PathVariable Long amendmentId,
            @RequestPart("file") MultipartFile file,
            @RequestParam AttachmentType type,
            @RequestParam(required = false)
            String description,
            @RequestParam String parts
    ) {

        return service.splitToContractAmendment(
                amendmentId,
                file,
                type,
                description,
                parts
        );
    }

    @PostMapping(
            value = "/api/payments/{paymentId}/attachments/split",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public List<AttachmentResponse>
    splitPaymentAttachment(
            @PathVariable Long paymentId,
            @RequestPart("file") MultipartFile file,
            @RequestParam AttachmentType type,
            @RequestParam(required = false)
            String description,
            @RequestParam String parts
    ) {

        return service.splitToPayment(
                paymentId,
                file,
                type,
                description,
                parts
        );
    }

    @PostMapping(
            value = "/api/subcontractor-statements/{statementId}/attachments/split",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public List<AttachmentResponse>
    splitStatementAttachment(
            @PathVariable Long statementId,
            @RequestPart("file") MultipartFile file,
            @RequestParam AttachmentType type,
            @RequestParam(required = false)
            String description,
            @RequestParam String parts
    ) {

        return service.splitToSubcontractorStatement(
                statementId,
                file,
                type,
                description,
                parts
        );
    }
}