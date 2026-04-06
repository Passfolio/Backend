package com.capstone.passfolio.common.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class MapUtils {
    private MapUtils() {}

    public static Map<String, Object> asStringObjectMap(Object obj) {
        if (!(obj instanceof Map<?, ?> raw)) return Collections.emptyMap();
        Map<String, Object> safe = new HashMap<>(raw.size());
        for (Map.Entry<?, ?> e : raw.entrySet()) safe.put(String.valueOf(e.getKey()), e.getValue());
        return safe;
    }

    public static Map<String, Object> getMap(Map<String, Object> map, String key) {
        return asStringObjectMap(map.get(key));
    }

    public static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return (v instanceof String s) ? s : (v != null ? String.valueOf(v) : null);
    }
}
