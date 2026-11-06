package pl.company.settlements.user.model;

public enum UserRole {

    READ_ONLY("Odczyt"),
    EDITOR("Edycja"),
    ADMIN("Administrator");

    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}