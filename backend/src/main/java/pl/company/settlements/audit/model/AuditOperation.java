package pl.company.settlements.audit.model;

public enum AuditOperation {

    USER_CREATED("Utworzenie konta"),
    USER_UPDATED("Zmiana danych lub uprawnień konta"),
    USER_PASSWORD_RESET("Reset hasła przez administratora"),
    USER_PASSWORD_CHANGED("Zmiana własnego hasła"),
    BOOTSTRAP_ADMIN_CREATED("Utworzenie pierwszego administratora"),

    TASK_CREATED("Utworzenie zadania"),
    TASK_UPDATED("Edycja zadania"),
    BUDGET_CREATED("Utworzenie budżetu"),
    BUDGET_YEAR_ADDED("Dodanie roku do budżetu"),
    BUDGET_YEAR_AMOUNT_CHANGED("Zmiana planu rocznego"),
    CONTRACTOR_CREATED("Utworzenie wykonawcy"),
    CONTRACTOR_UPDATED("Edycja wykonawcy");

    private final String label;

    AuditOperation(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}