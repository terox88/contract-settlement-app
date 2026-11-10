package pl.company.settlements.audit.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.audit.domain.AuditEvent;

@Service
public class AuditWriter {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(AuditEvent event) {
        entityManager.persist(event);
    }
}