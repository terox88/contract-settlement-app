package pl.company.settlements.common.error;

public class ConflictException
        extends RuntimeException {

    public ConflictException(
            String message
    ) {
        super(message);
    }
}