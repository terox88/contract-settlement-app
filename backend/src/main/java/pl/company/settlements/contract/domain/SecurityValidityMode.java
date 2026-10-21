package pl.company.settlements.contract.domain;

public enum SecurityValidityMode {
    SINGLE("Jeden etap — całe zabezpieczenie"),
    TWO_STAGE("Dwa etapy — realizacja oraz gwarancja i rękojmia");

    private final String label;
    SecurityValidityMode(String label) { this.label = label; }
    public String getLabel() { return label; }
}
