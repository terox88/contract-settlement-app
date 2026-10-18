package pl.company.settlements.invoice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.file.service.DocumentAttachmentService;
import pl.company.settlements.invoice.model.KsefInvoicePreview;
import pl.company.settlements.invoice.model.MainContractorInvoiceCreateRequest;
import pl.company.settlements.invoice.model.MainContractorInvoiceResponse;
import pl.company.settlements.invoice.model.MainContractorInvoiceUpdateRequest;
import pl.company.settlements.invoice.model.MainInvoiceSettlementResponse;
import pl.company.settlements.invoice.model.MainInvoiceWithSubcontractorInvoiceRequest;
import pl.company.settlements.invoice.model.SubcontractorInvoiceCreateRequest;
import pl.company.settlements.invoice.model.SubcontractorInvoiceResponse;
import pl.company.settlements.invoice.model.SubcontractorInvoiceUpdateRequest;

import java.io.IOException;
import java.io.InputStream;

@Service
public class KsefInvoiceImportService {
    private final MainContractorInvoiceService mainInvoices;
    private final SubcontractorInvoiceService subInvoices;
    private final MainInvoiceSettlementService settlements;
    private final DocumentAttachmentService attachments;
    private final KsefXmlParser parser = new KsefXmlParser();

    public KsefInvoiceImportService(MainContractorInvoiceService mainInvoices,
                                   SubcontractorInvoiceService subInvoices,
                                   MainInvoiceSettlementService settlements,
                                   DocumentAttachmentService attachments) {
        this.mainInvoices = mainInvoices;
        this.subInvoices = subInvoices;
        this.settlements = settlements;
        this.attachments = attachments;
    }

    /** Podgląd niczego nie zapisuje, także w magazynie plików. */
    public KsefInvoicePreview preview(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Wybierz niepusty plik XML KSeF.");
        if (file.getSize() > KsefXmlParser.MAX_BYTES) throw new IllegalArgumentException("Plik XML KSeF może mieć maksymalnie 5 MB.");
        try (InputStream input = file.getInputStream()) {
            return parser.parse(input.readNBytes(KsefXmlParser.MAX_BYTES + 1));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Nie można odczytać pliku XML faktury.", exception);
        }
    }

    // Dane sprawdzone i ewentualnie poprawione przez użytkownika przechodzą przez
    // te same serwisy i blokady finansowe co ręczne tworzenie/edycja faktur.
    // Zapis faktury, przypisania i oryginalnego XML jest jedną transakcją.
    @Transactional
    public MainContractorInvoiceResponse createMain(MainContractorInvoiceCreateRequest request, MultipartFile file) {
        preview(file);
        MainContractorInvoiceResponse response = mainInvoices.create(request);
        attachMain(response.getId(), file);
        return response;
    }

    @Transactional
    public MainContractorInvoiceResponse updateMain(Long id, MainContractorInvoiceUpdateRequest request, MultipartFile file) {
        preview(file);
        MainContractorInvoiceResponse response = mainInvoices.update(id, request);
        attachMain(response.getId(), file);
        return response;
    }

    @Transactional
    public SubcontractorInvoiceResponse createSub(SubcontractorInvoiceCreateRequest request, MultipartFile file) {
        preview(file);
        SubcontractorInvoiceResponse response = subInvoices.create(request);
        attachSub(response.getId(), file);
        return response;
    }

    @Transactional
    public SubcontractorInvoiceResponse updateSub(Long id, SubcontractorInvoiceUpdateRequest request, MultipartFile file) {
        preview(file);
        SubcontractorInvoiceResponse response = subInvoices.update(id, request);
        attachSub(response.getId(), file);
        return response;
    }

    @Transactional
    public MainInvoiceSettlementResponse createWithAssignment(MainInvoiceWithSubcontractorInvoiceRequest request, MultipartFile file) {
        preview(file);
        MainInvoiceSettlementResponse response = settlements.createWithInvoice(request);
        attachSub(response.getSubcontractorInvoiceId(), file);
        return response;
    }

    private void attachMain(Long invoiceId, MultipartFile file) {
        attachments.uploadToMainContractorInvoice(invoiceId, file, AttachmentType.MAIN_CONTRACTOR_INVOICE,
                "Oryginalny XML użyty do uzupełnienia formularza faktury.", null);
    }

    private void attachSub(Long invoiceId, MultipartFile file) {
        attachments.uploadToSubcontractorInvoice(invoiceId, file, AttachmentType.SUBCONTRACTOR_INVOICE,
                "Oryginalny XML użyty do uzupełnienia formularza faktury.", null);
    }
}
