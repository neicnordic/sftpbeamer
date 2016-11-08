package no.neic.tryggve;

import java.util.HashMap;
import java.util.Map;

public final class DownloadCounter {
    private static DownloadCounter instance = new DownloadCounter();

    public static DownloadCounter getCounter() {
        return instance;
    }

    private Map<String, Boolean> counterMap;

    private DownloadCounter() {
        counterMap = new HashMap<>();
    }

    public boolean checkIfBusy(String key) {
        return counterMap.containsKey(key) && counterMap.get(key);
    }

    public void addDownloadTask(String key) {
        counterMap.put(key, true);
    }

    public void removeDownloadTask(String key) {
        if (counterMap.containsKey(key)) {
            counterMap.remove(key);
        }
    }
}
