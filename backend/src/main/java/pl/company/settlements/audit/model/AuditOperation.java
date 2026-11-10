package pl.company.settlements.audit.model;

public enum AuditOperation {

    USER_CREATED("Utworzenie konta"),
    USER_UPDATED("Zmiana danych lub uprawnień konta"),
    USER_PASSWORD_RESET("Reset hasła przez administratora"),
    USER_PASSWORD_CHANGED("Zmiana własnego hasła"),
    BOOTSTRAP_ADMIN_CREATED("Utworzenie pierwszego administratora");

    private final String label;

    AuditOperation(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}