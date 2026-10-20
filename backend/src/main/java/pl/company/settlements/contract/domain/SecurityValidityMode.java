package pl.company.settlements.contract.domain;

public enum SecurityValidityMode {
    SINGLE("Jeden termin całego zabezpieczenia"),
    TWO_STAGE("Dwa terminy: realizacja oraz gwarancja i rękojmia");

    private final String label;
    SecurityValidityMode(String label) { this.label = label; }
    public String getLabel() { return label; }
}
