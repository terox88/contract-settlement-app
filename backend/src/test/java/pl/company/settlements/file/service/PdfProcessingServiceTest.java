package pl.company.settlements.file.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PdfProcessingServiceTest {
    private final PdfPageSelectionService selection = new PdfPageSelectionService();
    private final PdfSplitService split = new PdfSplitService(selection);

    @Test
    void extractedPdfContainsOnlySelectedPagesInSourceOrder() throws Exception {
        byte[] bytes = selection.selectPages(source(), "5,1-2,2");
        try (PDDocument result = Loader.loadPDF(bytes)) {
            assertEquals(3, result.getNumberOfPages());
            assertEquals(101f, result.getPage(0).getMediaBox().getWidth());
            assertEquals(102f, result.getPage(1).getMediaBox().getWidth());
            assertEquals(105f, result.getPage(2).getMediaBox().getWidth());
        }
    }

    @Test
    void splitProducesSeparateNamedPdfs() throws Exception {
        var parts = split.split(source(), "1-2;4");
        assertEquals(2, parts.size());
        assertEquals("scan_part-1.pdf", parts.get(0).filename());
        assertEquals("scan_part-2.pdf", parts.get(1).filename());
        try (PDDocument first = Loader.loadPDF(parts.get(0).content()); PDDocument second = Loader.loadPDF(parts.get(1).content())) {
            assertEquals(2, first.getNumberOfPages());
            assertEquals(1, second.getNumberOfPages());
            assertEquals(104f, second.getPage(0).getMediaBox().getWidth());
        }
    }

    @Test
    void invalidContentCannotBeAcceptedAsPdf() {
        var fake = new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a PDF".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> selection.selectPages(fake, "1"));
    }

    @Test
    void pageSelectionIsUnavailableForXml() {
        var xml = new MockMultipartFile("file", "invoice.xml", "application/xml", "<Faktura/>".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> selection.selectPages(xml, "1"));
    }

    @Test
    void missingPagesEmptyPartsAndExcessiveSplitAreRejected() throws Exception {
        var source = source();
        assertThrows(IllegalArgumentException.class, () -> split.split(source, "1;6"));
        assertThrows(IllegalArgumentException.class, () -> split.split(source, "1;"));
        assertThrows(IllegalArgumentException.class, () -> split.split(source, "1"));
        assertThrows(IllegalArgumentException.class, () -> split.split(source, String.join(";", java.util.Collections.nCopies(21, "1"))));
    }

    private MockMultipartFile source() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int page = 1; page <= 5; page++) document.addPage(new PDPage(new PDRectangle(100 + page, 200)));
            document.save(output);
            return new MockMultipartFile("file", "scan.pdf", "application/pdf", output.toByteArray());
        }
    }
}
