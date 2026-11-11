package pl.company.settlements.audit.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import pl.company.settlements.audit.domain.AuditEvent;
import pl.company.settlements.audit.model.AuditEntityType;
import pl.company.settlements.audit.model.AuditSearchRequest;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Repozytorium odczytu: nie udostępnia zapisu ani usuwania historii. */
@Repository
public class AuditEventQueryRepository {
    public static final ZoneId TIME_ZONE = ZoneId.of("Europe/Warsaw");
    @PersistenceContext private EntityManager entityManager;

    public Page<AuditEvent> search(AuditSearchRequest request, boolean includeAccounts) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        var query = builder.createQuery(AuditEvent.class);
        Root<AuditEvent> root = query.from(AuditEvent.class);
        query.select(root).where(predicates(builder, root, request, includeAccounts))
                .orderBy(builder.desc(root.get("occurredAt")), builder.desc(root.get("id")));
        List<AuditEvent> content = entityManager.createQuery(query)
                .setFirstResult(request.getPage() * request.getSize()).setMaxResults(request.getSize()).getResultList();

        var count = builder.createQuery(Long.class);
        Root<AuditEvent> countRoot = count.from(AuditEvent.class);
        count.select(builder.count(countRoot)).where(predicates(builder, countRoot, request, includeAccounts));
        long total = entityManager.createQuery(count).getSingleResult();
        return new PageImpl<>(content, PageRequest.of(request.getPage(), request.getSize()), total);
    }

    public Optional<AuditEvent> findById(Long id, boolean includeAccounts) {
        return entityManager.createQuery("""
                        select e from AuditEvent e where e.id = :id
                        and (:includeAccounts = true or e.entityType <> :accounts)
                        """, AuditEvent.class)
                .setParameter("id", id).setParameter("includeAccounts", includeAccounts)
                .setParameter("accounts", AuditEntityType.APP_USER).getResultStream().findFirst();
    }

    private Predicate[] predicates(CriteriaBuilder builder, Root<AuditEvent> root,
                                   AuditSearchRequest request, boolean includeAccounts) {
        List<Predicate> result = new ArrayList<>();
        if (!includeAccounts) result.add(builder.notEqual(root.get("entityType"), AuditEntityType.APP_USER));
        if (request.getDateFrom() != null) result.add(builder.greaterThanOrEqualTo(root.get("occurredAt"),
                request.getDateFrom().atStartOfDay(TIME_ZONE).toInstant()));
        if (request.getDateTo() != null) result.add(builder.lessThan(root.get("occurredAt"),
                request.getDateTo().plusDays(1).atStartOfDay(TIME_ZONE).toInstant()));
        if (hasText(request.getActor())) {
            String pattern = literalPattern(request.getActor());
            result.add(builder.or(builder.like(builder.lower(root.get("actorUsername")), pattern, '\\'),
                    builder.like(builder.lower(root.get("actorDisplayName")), pattern, '\\')));
        }
        if (hasText(request.getText())) {
            result.add(builder.like(builder.lower(root.get("entityLabel")), literalPattern(request.getText()), '\\'));
        }
        equal(builder, root, result, "entityType", request.getEntityType());
        equal(builder, root, result, "operation", request.getOperation());
        equal(builder, root, result, "entityId", request.getEntityId());
        equal(builder, root, result, "taskId", request.getTaskId());
        equal(builder, root, result, "mainContractId", request.getMainContractId());
        equal(builder, root, result, "subcontractId", request.getSubcontractId());
        return result.toArray(Predicate[]::new);
    }

    private void equal(CriteriaBuilder builder, Root<AuditEvent> root, List<Predicate> result, String field, Object value) {
        if (value != null) result.add(builder.equal(root.get(field), value));
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private String literalPattern(String value) {
        return "%" + value.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
