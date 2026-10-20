package pl.company.settlements.task.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import pl.company.settlements.task.domain.InvestmentTaskStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvestmentTaskUpdateRequest {

    @NotBlank
    private String taskNumber;

    @NotBlank
    private String name;

    private String description;

    @Size(max = 255)
    private String implementer;

    @NotNull
    private InvestmentTaskStatus status;
}