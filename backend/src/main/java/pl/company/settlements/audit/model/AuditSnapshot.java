package pl.company.settlements.audit.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AuditSnapshot {

    private final Map<String, Field> fields = new LinkedHashMap<>();

    public AuditSnapshot field(String name, String label, String value) {
        fields.put(name, new Field(label, value));
        return this;
    }

    public List<AuditFieldChange> changesFrom(AuditSnapshot before) {
        Map<String, Field> previous = before == null
                ? Map.of()
                : before.fields;

        Set<String> names = new LinkedHashSet<>(fields.keySet());
        names.addAll(previous.keySet());

        List<AuditFieldChange> changes = new ArrayList<>();

        for (String name : names) {
            Field oldField = previous.get(name);
            Field newField = fields.get(name);
            String oldValue = oldField == null ? null : oldField.value();
            String newValue = newField == null ? null : newField.value();

            if (!Objects.equals(oldValue, newValue)) {
                String label = newField == null
                        ? oldField.label()
                        : newField.label();

                changes.add(new AuditFieldChange(
                        name, label, oldValue, newValue
                ));
            }
        }

        return List.copyOf(changes);
    }

    private record Field(String label, String value) {
    }
}