package pl.company.settlements.audit.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class AuditSearchRequest {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
    private String actor;
    private String text;
    private AuditEntityType entityType;
    private AuditOperation operation;
    private Long entityId;
    private Long taskId;
    private Long mainContractId;
    private Long subcontractId;
    private int page;
    private int size = 20;

    public void validate() {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Nieprawidłowa strona historii lub liczba wpisów (1–100).");
        }
        if ((actor != null && actor.length() > 150) || (text != null && text.length() > 200)) {
            throw new IllegalArgumentException("Filtr autora może mieć do 150 znaków, a filtr rekordu do 200.");
        }
        for (Long id : new Long[]{entityId, taskId, mainContractId, subcontractId}) {
            if (id != null && id <= 0) throw new IllegalArgumentException("Identyfikatory muszą być dodatnie.");
        }
        if (entityId != null && entityType == null) {
            throw new IllegalArgumentException("Wybierz rodzaj rekordu przed filtrowaniem jego identyfikatora.");
        }
        for (LocalDate date : new LocalDate[]{dateFrom, dateTo}) {
            if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
                throw new IllegalArgumentException("Podaj datę z roku od 1 do 9999.");
            }
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("Data początkowa nie może być późniejsza od końcowej.");
        }
    }
}
