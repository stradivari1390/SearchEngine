package searchengine.exceptions;

public class NotFoundSiteException extends Exception {
    public NotFoundSiteException(Throwable cause) {
        super("Error creating JSON response for not found site: ", cause);
    }
}
