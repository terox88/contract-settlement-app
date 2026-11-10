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
    CONTRACTOR_UPDATED("Edycja wykonawcy"),
    MAIN_CONTRACT_CREATED("Utworzenie umowy głównej"),
    MAIN_CONTRACT_UPDATED("Edycja umowy głównej"),
    SECURITY_RETURN_UPDATED("Zmiana statusu zwrotu zabezpieczenia"),
    SUBCONTRACT_CREATED("Utworzenie umowy podwykonawczej"),
    SUBCONTRACT_UPDATED("Edycja umowy podwykonawczej"),
    CONTRACT_AMENDMENT_CREATED("Utworzenie aneksu"),
    CONTRACT_AMENDMENT_UPDATED("Edycja aneksu"),
    FINANCIAL_LIMIT_CREATED("Utworzenie limitów finansowych"),
    FINANCIAL_LIMIT_UPDATED("Zmiana limitów finansowych"),

    MAIN_CONTRACTOR_INVOICE_CREATED("Utworzenie faktury GW"),
    MAIN_CONTRACTOR_INVOICE_UPDATED("Edycja faktury GW"),
    SUBCONTRACTOR_INVOICE_CREATED("Utworzenie faktury podwykonawcy"),
    SUBCONTRACTOR_INVOICE_UPDATED("Edycja faktury podwykonawcy"),
    SUBCONTRACTOR_INVOICE_DELETED("Usunięcie faktury podwykonawcy"),
    SUBCONTRACTOR_STATEMENT_CREATED("Utworzenie oświadczenia podwykonawcy"),
    SUBCONTRACTOR_STATEMENT_UPDATED("Edycja oświadczenia podwykonawcy"),
    SUBCONTRACTOR_STATEMENT_DELETED("Usunięcie oświadczenia podwykonawcy"),
    PAYMENT_CREATED("Utworzenie przelewu"),
    PAYMENT_UPDATED("Edycja przelewu"),
    PAYMENT_DELETED("Usunięcie przelewu"),
    PAYMENT_ALLOCATION_CREATED("Przypisanie przelewu do faktury"),
    PAYMENT_ALLOCATION_UPDATED("Zmiana kwot zapłaty lub potrącenia"),
    PAYMENT_ALLOCATION_DELETED("Usunięcie przypisania przelewu"),
    INVOICE_ASSIGNMENT_CREATED("Powiązanie faktur"),
    INVOICE_ASSIGNMENT_UPDATED("Zmiana kwoty powiązania faktur"),
    INVOICE_ASSIGNMENT_DELETED("Usunięcie powiązania faktur"),
    MAIN_INVOICE_REVIEW_COMPLETED("Ręczne oznaczenie faktury GW jako rozliczonej"),
    MAIN_INVOICE_REVIEW_REOPENED("Cofnięcie oznaczenia rozliczenia faktury GW"),
    DEDUCTION_REVIEW_CONFIRMED("Potwierdzenie rozbieżności potrąceń"),
    DEDUCTION_REVIEW_REVOKED("Cofnięcie potwierdzenia rozbieżności potrąceń"),
    CONTRACT_PENALTY_CREATED("Dodanie kary umownej"),
    CONTRACT_PENALTY_UPDATED("Edycja kary umownej"),
    CONTRACT_PENALTY_DELETED("Usunięcie kary umownej"),

    ATTACHMENT_UPLOADED("Dodanie załącznika"),
    ATTACHMENT_CREATED_FROM_PDF("Utworzenie załącznika przez podział PDF"),
    ATTACHMENT_RENAMED("Zmiana nazwy załącznika"),
    ATTACHMENT_DELETED("Usunięcie załącznika");

    private final String label;

    AuditOperation(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}