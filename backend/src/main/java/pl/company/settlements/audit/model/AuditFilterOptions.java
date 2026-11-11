package pl.company.settlements.audit.model;

import java.util.List;

public record AuditFilterOptions(List<Option> entityTypes, List<Option> operations,
                                 String timeZone, boolean canReadAccountHistory) {
    public record Option(String value, String label) { }
}
