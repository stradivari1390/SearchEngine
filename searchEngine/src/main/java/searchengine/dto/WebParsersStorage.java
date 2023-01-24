package searchengine.dto;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebParsersStorage {

    private AtomicBoolean terminationInProcess;
    private static WebParsersStorage instance;
    private CopyOnWriteArraySet<WebParser> webParsersStorage;

    private WebParsersStorage() {
        webParsersStorage = new CopyOnWriteArraySet<>();
        terminationInProcess = new AtomicBoolean(false);
    }

    public static WebParsersStorage getInstance() {
        if (instance == null) {
            instance = new WebParsersStorage();
        }
        return instance;
    }

    public void add(WebParser webParser) {
        webParsersStorage.add(webParser);
    }

    public void remove(WebParser webParser) {
        webParsersStorage.remove(webParser);
    }

    public void terminateAll() {
        terminationInProcess.set(true);
        webParsersStorage.forEach(w -> w.cancel(true));
        webParsersStorage.clear();
        terminationInProcess.set(false);
    }

    public AtomicBoolean isTerminationInProcess() {
        return terminationInProcess;
    }
}