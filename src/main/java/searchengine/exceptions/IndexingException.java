package searchengine.exceptions;

public class IndexingException extends RuntimeException {
    public IndexingException(String message, Throwable cause) {
        super(message, cause);
    }
}