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

        if (this.rAbs > maxrAbs) maxrAbs = rAbs;
    }

    public void setrRel() {
        rRel = maxrAbs / rAbs;
    }
}