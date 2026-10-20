package ac.at.tgm.submit.allocation;

/** Thrown when a request is valid but too large for the exact optimizer. */
public class AllocationLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AllocationLimitExceededException(String message) {
        super(message);
    }
}
