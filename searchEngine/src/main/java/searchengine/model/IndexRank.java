package searchengine.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class IndexRank {

    private Page page;
    private Map<String, Float> rankMap;

    private static float maxrAbs = 0;
    private float rAbs;
    private float rRel;

    public IndexRank() {
        rankMap = new HashMap<>();
    }
    public void addRank(String word, Float rank) {
        rankMap.put(word, rank);
    }
    public void setrAbs() {
        rankMap.forEach((key, value) -> this.rAbs += value);
        setMaxrAbs(this.rAbs);
    }

    public void setrRel() {
        rRel = maxrAbs / rAbs;
    }

    private static synchronized void setMaxrAbs(float newMaxrAbs) {
        if (newMaxrAbs > maxrAbs) {
            maxrAbs = newMaxrAbs;
        }
    }
}