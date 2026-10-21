package pl.company.settlements.contract.model;

import jakarta.validation.constraints.NotNull;
import pl.company.settlements.contract.domain.SecurityReturnStage;

public record SecurityReturnRequest(
        @NotNull SecurityReturnStage stage,
        @NotNull Boolean returned
) {}
