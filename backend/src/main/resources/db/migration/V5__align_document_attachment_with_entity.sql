-- ============================================================
-- V5 - Align document_attachment with current entity model
-- ============================================================


-- StoredFile relation was previously stored as file_id.
-- Rename it to match DocumentAttachment.storedFile mapping.

ALTER TABLE document_attachment
    RENAME COLUMN file_id TO stored_file_id;


-- Attachment type was previously stored as "type".
-- Rename it to match the current entity mapping.

ALTER TABLE document_attachment
    RENAME COLUMN type TO attachment_type;


-- Current entity allows descriptions up to 1000 characters.

ALTER TABLE document_attachment
ALTER COLUMN description TYPE VARCHAR(1000);