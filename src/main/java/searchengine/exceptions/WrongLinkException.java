package searchengine.exceptions;

public class WrongLinkException extends RuntimeException {
    public WrongLinkException(Throwable cause) {
        super("The link provided is incorrect", cause);
    }
}