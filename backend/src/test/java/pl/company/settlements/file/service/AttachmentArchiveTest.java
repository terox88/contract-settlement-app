package pl.company.settlements.file.service;

import org.junit.jupiter.api.Test;

class AttachmentArchiveTest {
    @Test void exportsRealZipWithSafeUniqueNamesAndOriginalBytes() throws Exception { AttachmentArchiveCases.run(); }
}
