package pl.company.settlements.file.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PdfSplitService {

    private final PdfPageSelectionService
            pdfPageSelectionService;

    public List<SplitPdfPart> split(
            MultipartFile file,
            String parts
    ) {

        if (!StringUtils.hasText(parts)) {
            throw new IllegalArgumentException(
                    "Podaj strony dla każdej części PDF."
            );
        }

        String[] selections =
                parts.split(";", -1);

        if (selections.length > 20) {
            throw new IllegalArgumentException("Jednorazowo można utworzyć najwyżej 20 części PDF.");
        }

        if (selections.length < 2) {
            throw new IllegalArgumentException(
                    "Podział PDF wymaga co najmniej dwóch części."
            );
        }

        List<SplitPdfPart> result =
                new ArrayList<>();

        for (int i = 0;
             i < selections.length;
             i++) {

            String selection =
                    selections[i].trim();

            if (selection.isEmpty()) {
                throw new IllegalArgumentException(
                        "Podaj strony dla każdej części PDF."
                );
            }

            byte[] content =
                    pdfPageSelectionService
                            .selectPages(
                                    file,
                                    selection
                            );

            String filename =
                    buildFilename(
                            file.getOriginalFilename(),
                            i + 1
                    );

            result.add(
                    new SplitPdfPart(
                            selection,
                            filename,
                            content
                    )
            );
        }

        return result;
    }

    private String buildFilename(
            String originalFilename,
            int partNumber
    ) {

        if (!StringUtils.hasText(
                originalFilename
        )) {

            return "document_part-"
                    + partNumber
                    + ".pdf";
        }

        String filename =
                originalFilename.trim();

        int dotIndex =
                filename.lastIndexOf('.');

        String baseName;

        if (dotIndex > 0) {
            baseName =
                    filename.substring(
                            0,
                            dotIndex
                    );
        } else {
            baseName =
                    filename;
        }

        return baseName
                + "_part-"
                + partNumber
                + ".pdf";
    }

    public record SplitPdfPart(
            String pageSelection,
            String filename,
            byte[] content
    ) {
    }
}