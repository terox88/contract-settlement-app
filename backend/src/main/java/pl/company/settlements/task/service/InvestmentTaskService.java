package pl.company.settlements.task.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.task.domain.InvestmentTask;
import pl.company.settlements.task.model.InvestmentTaskCreateRequest;
import pl.company.settlements.task.model.InvestmentTaskResponse;
import pl.company.settlements.task.model.InvestmentTaskUpdateRequest;
import pl.company.settlements.task.repository.InvestmentTaskRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestmentTaskService {

    private final InvestmentTaskRepository repository;

    public InvestmentTaskResponse getById(Long id) {
        return toResponse(findEntityById(id));
    }

    public List<InvestmentTaskResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InvestmentTaskResponse create(
            InvestmentTaskCreateRequest request
    ) {
        String taskNumber = normalizeTaskNumber(request.getTaskNumber());

        if (repository.existsByTaskNumber(taskNumber)) {
            throw new IllegalArgumentException(
                    "Investment task with number "
                            + taskNumber
                            + " already exists"
            );
        }

        InvestmentTask task = new InvestmentTask(
                taskNumber,
                request.getName().trim()
        );

        task.setDescription(
                trimToNull(request.getDescription())
        );

        InvestmentTask savedTask = repository.save(task);

        return toResponse(savedTask);
    }

    @Transactional
    public InvestmentTaskResponse update(
            Long id,
            InvestmentTaskUpdateRequest request
    ) {
        InvestmentTask task = findEntityById(id);

        String taskNumber = normalizeTaskNumber(request.getTaskNumber());

        repository.findByTaskNumber(taskNumber)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Investment task with number "
                                    + taskNumber
                                    + " already exists"
                    );
                });

        task.setTaskNumber(taskNumber);
        task.setName(request.getName().trim());
        task.setDescription(
                trimToNull(request.getDescription())
        );

        return toResponse(task);
    }

    private InvestmentTask findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Investment task with id "
                                + id
                                + " does not exist"
                ));
    }

    private String normalizeTaskNumber(String taskNumber) {
        if (taskNumber == null || taskNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Task number cannot be empty"
            );
        }

        return taskNumber.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private InvestmentTaskResponse toResponse(
            InvestmentTask task
    ) {
        return InvestmentTaskResponse.builder()
                .id(task.getId())
                .taskNumber(task.getTaskNumber())
                .name(task.getName())
                .description(task.getDescription())
                .build();
    }
}