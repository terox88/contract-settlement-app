package pl.company.settlements.contractor.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import pl.company.settlements.common.error.ConflictException;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.contractor.model.ContractorCreateRequest;
import pl.company.settlements.contractor.repository.ContractorRepository;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractorCreationServiceTest {
    @Mock private ContractorRepository repository;
    @InjectMocks private ContractorService service;

    @Test
    void createsNormalizedEntityAndKeepsAddressFields() {
        ContractorCreateRequest request = request();
        request.setTaxId("123-456-78-90");
        request.setStreet(" Długa ");
        request.setBuildingNumber(" 12 ");
        request.setEmail(" Firma@Example.pl ");
        when(repository.saveAndFlush(any(Contractor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Contractor entity = service.createEntity(request);

        assertEquals("Firma", entity.getName());
        assertEquals("1234567890", entity.getTaxId());
        assertEquals("Długa", entity.getStreet());
        assertEquals("12", entity.getBuildingNumber());
        assertEquals("firma@example.pl", entity.getEmail());
    }

    @Test
    void rejectsAlreadyRegisteredTaxIdBeforeInsert() {
        when(repository.existsByTaxId("1234567890")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.createEntity(request()));

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void translatesConcurrentTaxIdUniqueViolationIntoConflict() {
        SQLException sql = new SQLException("duplicate key: Key (tax_id)=(1234567890)", "23505");
        DataIntegrityViolationException failure = new DataIntegrityViolationException("duplicate", sql);
        when(repository.saveAndFlush(any(Contractor.class))).thenThrow(failure);

        assertThrows(ConflictException.class, () -> service.createEntity(request()));
    }

    @Test
    void recognizesConstraintNameWithoutParsingDatabaseMessage() {
        SQLException sql = new SQLException("duplicate", "23505");
        ConstraintViolationException violation = new ConstraintViolationException(
                "unique", sql, "uk_contractor_tax_id");
        when(repository.saveAndFlush(any(Contractor.class))).thenThrow(
                new DataIntegrityViolationException("duplicate", violation));

        assertThrows(ConflictException.class, () -> service.createEntity(request()));
    }

    @Test
    void doesNotMislabelAnUnrelatedDatabaseErrorAsDuplicateTaxId() {
        SQLException sql = new SQLException("duplicate key: Key (id)=(1)", "23505");
        DataIntegrityViolationException failure = new DataIntegrityViolationException("duplicate", sql);
        when(repository.saveAndFlush(any(Contractor.class))).thenThrow(failure);

        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> service.createEntity(request())));
    }

    @Test
    void rejectsInvalidTaxIdWithoutQueryingDatabase() {
        ContractorCreateRequest request = request();
        request.setTaxId("123");

        assertThrows(IllegalArgumentException.class, () -> service.createEntity(request));
        verifyNoInteractions(repository);
    }

    @Test
    void preservesStandaloneCreateResponse() {
        when(repository.saveAndFlush(any(Contractor.class))).thenAnswer(invocation -> {
            Contractor entity = invocation.getArgument(0);
            entity.setId(5L);
            return entity;
        });

        var response = service.create(request());

        assertEquals(5L, response.getId());
        assertEquals("1234567890", response.getTaxId());
    }

    private ContractorCreateRequest request() {
        ContractorCreateRequest request = new ContractorCreateRequest();
        request.setName(" Firma ");
        request.setTaxId("1234567890");
        return request;
    }
}
