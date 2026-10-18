package pl.company.settlements.file.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Service
public class PdfPageSelectionService {

    public byte[] selectPages(
            MultipartFile file,
            String pages
    ) {

        if (!StringUtils.hasText(pages)) {
            throw new IllegalArgumentException(
                    "Podaj strony PDF do zapisania."
            );
        }

        validatePdfFile(file);

        try {
            byte[] sourceBytes =
                    file.getBytes();

            try (
                    PDDocument sourceDocument =
                            Loader.loadPDF(sourceBytes);

                    PDDocument targetDocument =
                            new PDDocument();

                    ByteArrayOutputStream outputStream =
                            new ByteArrayOutputStream()
            ) {

                List<Integer> selectedPages =
                        parsePageSelection(
                                pages,
                                sourceDocument.getNumberOfPages()
                        );

                for (Integer pageNumber : selectedPages) {

                    targetDocument.importPage(
                            sourceDocument.getPage(
                                    pageNumber - 1
                            )
                    );
                }

                targetDocument.save(
                        outputStream
                );

                return outputStream.toByteArray();
            }

        } catch (IOException exception) {

            throw new IllegalArgumentException(
                    "Nie można przetworzyć pliku PDF. Sprawdź, czy jest poprawny i nie jest zabezpieczony hasłem.",
                    exception
            );
        }
    }

    private List<Integer> parsePageSelection(String pages, int totalPages) {
        return PdfPageSelection.parse(pages, totalPages);
    }

    private void validatePdfFile(
            MultipartFile file
    ) {

        String filename =
                file.getOriginalFilename();

        boolean pdfFilename =
                filename != null
                        && filename.toLowerCase(Locale.ROOT)
                        .endsWith(".pdf");

        boolean pdfContentType =
                "application/pdf".equalsIgnoreCase(
                        file.getContentType()
                );

        if (!pdfFilename
                && !pdfContentType) {

            throw new IllegalArgumentException(
                    "Wybór stron jest dostępny tylko dla plików PDF."
            );
        }
    }
}