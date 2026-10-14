package pl.company.settlements.contractor.service;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.contractor.model.ContractorCreateRequest;
import pl.company.settlements.contractor.model.ContractorResponse;
import pl.company.settlements.contractor.model.ContractorUpdateRequest;
import pl.company.settlements.contractor.repository.ContractorRepository;
import pl.company.settlements.common.error.ConflictException;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractorService {

    private final ContractorRepository repository;

    public ContractorResponse getById(Long id) {
        return toResponse(findEntityById(id));
    }

    public Optional<ContractorResponse> findByTaxId(String taxId) {
        String normalizedTaxId = normalizeTaxId(taxId);

        return repository.findByTaxId(normalizedTaxId)
                .map(this::toResponse);
    }

    @Transactional
    public ContractorResponse create(ContractorCreateRequest request) {
        return toResponse(createEntity(request));
    }

    @Transactional
    public Contractor createEntity(ContractorCreateRequest request) {
        String normalizedTaxId = normalizeTaxId(request.getTaxId());

        if (repository.existsByTaxId(normalizedTaxId)) {
            throw duplicateTaxId(normalizedTaxId);
        }

        Contractor contractor = new Contractor(
                request.getName().trim(),
                normalizedTaxId
        );

        applyData(
                contractor,
                request.getName(),
                normalizedTaxId,
                request.getStreet(),
                request.getBuildingNumber(),
                request.getApartmentNumber(),
                request.getPostalCode(),
                request.getCity(),
                request.getCountry(),
                request.getEmail()
        );

        try {
            // Flush pozwala zamienić konflikt równoczesnych zapisów NIP
            // na 409, zanim metoda zwróci odpowiedź. Nie zatwierdza transakcji.
            return repository.saveAndFlush(contractor);
        } catch (DataIntegrityViolationException error) {
            if (isTaxIdConflict(error)) throw duplicateTaxId(normalizedTaxId);
            throw error;
        }
    }

    private ConflictException duplicateTaxId(String taxId) {
        return new ConflictException(
                "Wykonawca o NIP " + taxId
                        + " już istnieje. Wyszukaj go i wybierz istniejącego wykonawcę."
        );
    }

    private boolean isTaxIdConflict(DataIntegrityViolationException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null) {
                return "23505".equals(violation.getSQLState())
                        && violation.getConstraintName().toLowerCase(Locale.ROOT).contains("tax_id");
            }
            if (cause instanceof SQLException sqlError
                    && "23505".equals(sqlError.getSQLState())
                    && String.valueOf(sqlError.getMessage())
                            .toLowerCase(Locale.ROOT).contains("tax_id")) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public ContractorResponse update(
            Long id,
            ContractorUpdateRequest request
    ) {
        Contractor contractor = findEntityById(id);

        String normalizedTaxId = normalizeTaxId(request.getTaxId());

        repository.findByTaxId(normalizedTaxId)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Contractor with tax ID "
                                    + normalizedTaxId
                                    + " already exists"
                    );
                });

        applyData(
                contractor,
                request.getName(),
                normalizedTaxId,
                request.getStreet(),
                request.getBuildingNumber(),
                request.getApartmentNumber(),
                request.getPostalCode(),
                request.getCity(),
                request.getCountry(),
                request.getEmail()
        );

        return toResponse(contractor);
    }

    private Contractor findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Contractor with id "
                                + id
                                + " does not exist"
                ));
    }

    private void applyData(
            Contractor contractor,
            String name,
            String taxId,
            String street,
            String buildingNumber,
            String apartmentNumber,
            String postalCode,
            String city,
            String country,
            String email
    ) {
        contractor.setName(name.trim());
        contractor.setTaxId(taxId);
        contractor.setStreet(trimToNull(street));
        contractor.setBuildingNumber(trimToNull(buildingNumber));
        contractor.setApartmentNumber(trimToNull(apartmentNumber));
        contractor.setPostalCode(trimToNull(postalCode));
        contractor.setCity(trimToNull(city));
        contractor.setCountry(trimToNull(country));
        contractor.setEmail(normalizeEmail(email));
    }

    private String normalizeTaxId(String taxId) {
        if (taxId == null) {
            throw new IllegalArgumentException("Tax ID cannot be null");
        }

        String normalized = taxId.replaceAll("[^0-9]", "");

        if (normalized.length() != 10) {
            throw new IllegalArgumentException(
                    "Polish tax ID must contain exactly 10 digits"
            );
        }

        return normalized;
    }

    private String normalizeEmail(String email) {
        String normalized = trimToNull(email);

        return normalized == null
                ? null
                : normalized.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private ContractorResponse toResponse(Contractor contractor) {
        return ContractorResponse.builder()
                .id(contractor.getId())
                .name(contractor.getName())
                .taxId(contractor.getTaxId())
                .street(contractor.getStreet())
                .buildingNumber(contractor.getBuildingNumber())
                .apartmentNumber(contractor.getApartmentNumber())
                .postalCode(contractor.getPostalCode())
                .city(contractor.getCity())
                .country(contractor.getCountry())
                .email(contractor.getEmail())
                .build();
    }
}