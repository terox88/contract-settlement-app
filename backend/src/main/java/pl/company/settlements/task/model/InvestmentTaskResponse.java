package pl.company.settlements.task.model;

import lombok.Builder;
import lombok.Getter;
import pl.company.settlements.task.domain.InvestmentTaskStatus;

@Getter
@Builder
public class InvestmentTaskResponse {

    private Long id;
    private String taskNumber;
    private String name;
    private String description;
    private String implementer;
    private InvestmentTaskStatus status;
    private String statusLabel;
}