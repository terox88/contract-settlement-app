package pl.company.settlements.contractor.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContractorCreateRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String taxId;

    @Size(max = 255)
    private String street;

    @Size(max = 255)
    private String buildingNumber;

    @Size(max = 255)
    private String apartmentNumber;

    @Size(max = 255)
    private String postalCode;

    @Size(max = 255)
    private String city;

    @Size(max = 255)
    private String country = "Polska";

    @Email
    @Size(max = 255)
    private String email;
}