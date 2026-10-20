package pl.company.settlements.file.domain;

import java.util.Set;

public enum AttachmentOwner {
    CONTRACT(Set.of(AttachmentType.CONTRACT, AttachmentType.ACCEPTANCE_PROTOCOL,
            AttachmentType.NECESSITY_PROTOCOL, AttachmentType.WORK_PROGRESS_REPORT,
            AttachmentType.INSURANCE_DOCUMENT, AttachmentType.GUARANTEE_DOCUMENT)),
    AMENDMENT(Set.of(AttachmentType.AMENDMENT, AttachmentType.ACCEPTANCE_PROTOCOL,
            AttachmentType.NECESSITY_PROTOCOL, AttachmentType.WORK_PROGRESS_REPORT,
            AttachmentType.INSURANCE_DOCUMENT, AttachmentType.GUARANTEE_DOCUMENT)),
    MAIN_INVOICE(Set.of(AttachmentType.MAIN_CONTRACTOR_INVOICE, AttachmentType.ACCEPTANCE_PROTOCOL,
            AttachmentType.NECESSITY_PROTOCOL, AttachmentType.WORK_PROGRESS_REPORT)),
    SUB_INVOICE(Set.of(AttachmentType.SUBCONTRACTOR_INVOICE, AttachmentType.SUBCONTRACTOR_STATEMENT,
            AttachmentType.ACCEPTANCE_PROTOCOL, AttachmentType.NECESSITY_PROTOCOL, AttachmentType.WORK_PROGRESS_REPORT)),
    PAYMENT(Set.of(AttachmentType.PAYMENT_CONFIRMATION)),
    STATEMENT(Set.of(AttachmentType.SUBCONTRACTOR_STATEMENT));

    private final Set<AttachmentType> types;

    AttachmentOwner(Set<AttachmentType> types) { this.types = types; }

    public boolean supports(AttachmentType type) { return type != null && types.contains(type); }

    public void validate(AttachmentType type) {
        if (!supports(type)) throw new IllegalArgumentException("Wybrany rodzaj załącznika nie pasuje do tego rekordu.");
    }
}
