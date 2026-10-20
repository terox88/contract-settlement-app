package pl.company.settlements.file.domain;

public enum AttachmentType {
    CONTRACT("Umowa"),
    AMENDMENT("Aneks"),
    MAIN_CONTRACTOR_INVOICE("Faktura GW — PDF/XML"),
    SUBCONTRACTOR_INVOICE("Faktura podwykonawcy — PDF/XML"),
    PAYMENT_CONFIRMATION("Potwierdzenie przelewu"),
    SUBCONTRACTOR_STATEMENT("Oświadczenie podwykonawcy"),
    ACCEPTANCE_PROTOCOL("Protokół odbioru"),
    NECESSITY_PROTOCOL("Protokół konieczności"),
    WORK_PROGRESS_REPORT("Protokół przerobu"),
    INSURANCE_DOCUMENT("Dokument ubezpieczenia"),
    GUARANTEE_DOCUMENT("Dokument gwarancji bankowej/ubezpieczeniowej");

    private final String label;

    AttachmentType(String label) { this.label = label; }

    public String getLabel() { return label; }
}
