package searchengine.exceptions;

public class EmptyLinkException extends RuntimeException {
    public EmptyLinkException(Throwable cause) {
        super("The link provided is empty", cause);
    }
}