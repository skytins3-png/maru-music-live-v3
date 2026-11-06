package com.maru.radio;
import java.util.*;
/** O(n+m) ordered lookup; missing/deleted files and duplicates are skipped. */
public final class CatalogOrder {
    private CatalogOrder() {}
    public static <T> List<T> resolve(Map<String,T> available, List<String> hashes) {
        List<T> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String hash : hashes) {
            T value = available.get(hash);
            if (value != null && seen.add(hash)) result.add(value);
        }
        return result;
    }
}
