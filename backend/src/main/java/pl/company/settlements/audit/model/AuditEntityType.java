package pl.company.settlements.audit.model;

public enum AuditEntityType {
    APP_USER("Konto użytkownika"),
    INVESTMENT_TASK("Zadanie inwestycyjne"),
    BUDGET("Budżet zadania"),
    CONTRACTOR("Wykonawca"),
    MAIN_CONTRACT("Umowa główna"),
    SUBCONTRACT("Umowa podwykonawcza"),
    CONTRACT_AMENDMENT("Aneks"),
    FINANCIAL_LIMIT("Limity finansowe"),
    MAIN_CONTRACTOR_INVOICE("Faktura GW"),
    SUBCONTRACTOR_INVOICE("Faktura podwykonawcy"),
    SUBCONTRACTOR_STATEMENT("Oświadczenie podwykonawcy"),
    PAYMENT("Przelew"),
    PAYMENT_ALLOCATION("Zapłata i potrącenie"),
    INVOICE_ASSIGNMENT("Powiązanie faktur"),
    CONTRACT_PENALTY("Kara umowna"),
    DOCUMENT_ATTACHMENT("Załącznik");
    private final String label;

    AuditEntityType(String label) { this.label = label; }

    public String getLabel() { return label; }
}