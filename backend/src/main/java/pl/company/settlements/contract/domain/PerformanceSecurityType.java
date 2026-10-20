package pl.company.settlements.contract.domain;

public enum PerformanceSecurityType {
    NOT_ESTABLISHED("Nie ustanowiono", false),
    CASH("Pieniądz", false),
    BANK_GUARANTEE("Gwarancja bankowa", true),
    INSURANCE_GUARANTEE("Gwarancja ubezpieczeniowa", true),
    OTHER_DOCUMENT("Inny dokument", true);

    private final String label;
    private final boolean document;
    PerformanceSecurityType(String label, boolean document) { this.label = label; this.document = document; }
    public String getLabel() { return label; }
    public boolean isDocument() { return document; }
}
