package pl.company.settlements.file.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class PdfPageSelectionService {

    public byte[] selectPages(
            MultipartFile file,
            String pages
    ) {

        if (!StringUtils.hasText(pages)) {
            throw new IllegalArgumentException(
                    "Page selection cannot be empty"
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
                    "Could not process PDF file",
                    exception
            );
        }
    }

    private List<Integer> parsePageSelection(
            String pages,
            int totalPages
    ) {

        if (totalPages <= 0) {
            throw new IllegalArgumentException(
                    "PDF contains no pages"
            );
        }

        /*
         * TreeSet:
         * - removes duplicates
         * - keeps pages in their natural document order
         *
         * Example:
         * 3,1-2,2
         * becomes:
         * 1,2,3
         */
        Set<Integer> selectedPages =
                new TreeSet<>();

        String[] parts =
                pages.split(",", -1);

        for (String part : parts) {

            String value =
                    part.trim();

            if (value.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid page selection: "
                                + pages
                );
            }

            if (value.matches("\\d+")) {

                int page =
                        parsePageNumber(
                                value
                        );

                validatePageNumber(
                        page,
                        totalPages
                );

                selectedPages.add(
                        page
                );

                continue;
            }

            if (value.matches("\\d+\\s*-\\s*\\d+")) {

                String[] range =
                        value.split("-");

                int start =
                        parsePageNumber(
                                range[0].trim()
                        );

                int end =
                        parsePageNumber(
                                range[1].trim()
                        );

                if (start > end) {
                    throw new IllegalArgumentException(
                            "Invalid page range: "
                                    + value
                    );
                }

                validatePageNumber(
                        start,
                        totalPages
                );

                validatePageNumber(
                        end,
                        totalPages
                );

                for (int page = start;
                     page <= end;
                     page++) {

                    selectedPages.add(
                            page
                    );
                }

                continue;
            }

            throw new IllegalArgumentException(
                    "Invalid page selection: "
                            + value
            );
        }

        if (selectedPages.isEmpty()) {
            throw new IllegalArgumentException(
                    "No PDF pages selected"
            );
        }

        return new ArrayList<>(
                selectedPages
        );
    }

    private int parsePageNumber(
            String value
    ) {

        try {
            return Integer.parseInt(
                    value
            );

        } catch (NumberFormatException exception) {

            throw new IllegalArgumentException(
                    "Invalid page number: "
                            + value
            );
        }
    }

    private void validatePageNumber(
            int page,
            int totalPages
    ) {

        if (page < 1) {
            throw new IllegalArgumentException(
                    "Page number must be greater than zero"
            );
        }

        if (page > totalPages) {
            throw new IllegalArgumentException(
                    "Page "
                            + page
                            + " does not exist. PDF contains "
                            + totalPages
                            + " pages"
            );
        }
    }

    private void validatePdfFile(
            MultipartFile file
    ) {

        String filename =
                file.getOriginalFilename();

        boolean pdfFilename =
                filename != null
                        && filename.toLowerCase()
                        .endsWith(".pdf");

        boolean pdfContentType =
                "application/pdf".equalsIgnoreCase(
                        file.getContentType()
                );

        if (!pdfFilename
                && !pdfContentType) {

            throw new IllegalArgumentException(
                    "Page selection is available only for PDF files"
            );
        }
    }
}