package searchengine.exceptions;

public class EmptyQueryException extends RuntimeException {
    public EmptyQueryException(Throwable cause) {
        super("Error creating JSON response for empty query: ", cause);
    }
}