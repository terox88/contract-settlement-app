package pl.company.settlements.contractor.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.contractor.model.ContractorCreateRequest;
import pl.company.settlements.contractor.model.ContractorResponse;
import pl.company.settlements.contractor.model.ContractorUpdateRequest;
import pl.company.settlements.contractor.repository.ContractorRepository;

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
        String normalizedTaxId = normalizeTaxId(request.getTaxId());

        if (repository.existsByTaxId(normalizedTaxId)) {
            throw new IllegalArgumentException(
                    "Contractor with tax ID "
                            + normalizedTaxId
                            + " already exists"
            );
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

        Contractor savedContractor = repository.save(contractor);

        return toResponse(savedContractor);
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