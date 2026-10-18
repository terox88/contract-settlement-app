package pl.company.settlements.file.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class AttachmentZipWriter {
    @FunctionalInterface public interface Source { InputStream open() throws IOException; }
    public record Entry(long attachmentId, String category, String name, String originalName, Source source) { }
    private AttachmentZipWriter() { }
    public static void write(List<Entry> entries, OutputStream output) throws IOException {
        // finish zamiast close: strumień odpowiedzi HTTP należy do serwera.
        ZipOutputStream zip = new ZipOutputStream(new FilterOutputStream(output) {
            @Override public void close() throws IOException { flush(); }
        }, StandardCharsets.UTF_8);
        Set<Long> seen = new HashSet<>();
        try {
            for (Entry entry : entries) {
                if (!seen.add(entry.attachmentId())) continue;
                String category = entry.category().matches("[A-Z_]+") ? entry.category() : "DOKUMENTY";
                zip.putNextEntry(new ZipEntry(category + "/" + entry.attachmentId() + "-" + AttachmentNames.downloadName(entry.name(), entry.originalName())));
                try (InputStream input = entry.source().open()) { input.transferTo(zip); }
                zip.closeEntry();
            }
            zip.finish(); zip.flush();
        } finally { zip.close(); }
    }
}
