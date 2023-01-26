package searchengine.dto;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebParsersStorage {

    private AtomicBoolean terminationInProcess;
    private static WebParsersStorage instance;
    private CopyOnWriteArraySet<WebParser> webParsersSet;

    private WebParsersStorage() {
        webParsersSet = new CopyOnWriteArraySet<>();
        terminationInProcess = new AtomicBoolean(false);
    }

    public static WebParsersStorage getInstance() {
        if (instance == null) {
            instance = new WebParsersStorage();
        }
        return instance;
    }

    public void add(WebParser webParser) {
        webParsersSet.add(webParser);
    }

    public void remove(WebParser webParser) {
        webParsersSet.remove(webParser);
    }

    public void terminateAll() {
        webParsersSet.forEach(w -> w.cancel(true));
        webParsersSet.clear();
    }

    public AtomicBoolean isTerminationInProcess() {
        return terminationInProcess;
    }
}