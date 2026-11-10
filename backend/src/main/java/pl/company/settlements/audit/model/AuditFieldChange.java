package pl.company.settlements.audit.model;

public record AuditFieldChange(
        String fieldName,
        String fieldLabel,
        String beforeValue,
        String afterValue
) {
}