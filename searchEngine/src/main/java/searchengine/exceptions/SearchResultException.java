package searchengine.exceptions;

public class SearchResultException extends Exception {
    public SearchResultException(Throwable cause) {
        super("Error while converting search result object to JSON", cause);
    }
}