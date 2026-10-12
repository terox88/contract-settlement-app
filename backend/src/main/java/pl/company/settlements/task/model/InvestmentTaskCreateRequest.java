package pl.company.settlements.task.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import pl.company.settlements.task.domain.InvestmentTaskStatus;

@Getter
@Setter
public class InvestmentTaskCreateRequest {

    @NotBlank
    private String taskNumber;

    @NotBlank
    private String name;

    private String description;

    private InvestmentTaskStatus status =
            InvestmentTaskStatus.IN_PROGRESS;
}