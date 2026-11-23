package com.sumasoft.tracking.audit;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a "snapshot" of a record (for example a ClientResponse) into JSON text for the audit log.
 * Small and self-contained on purpose: only text, numbers, true/false, empty values,
 * dates/times (as text) and simple lists are needed.
 */
public final class AuditJson {

    private AuditJson() {
    }

    /** Record -> ordered map of its fields. Anything that is not a record is stored as {"value": ...}. */
    public static Map<String, Object> snapshot(Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> m) {
            m.forEach((k, v) -> map.put(String.valueOf(k), v));
            return map;
        }
        if (value.getClass().isRecord()) {
            for (RecordComponent component : value.getClass().getRecordComponents()) {
                try {
                    java.lang.reflect.Method accessor = component.getAccessor();
                    accessor.trySetAccessible();
                    map.put(component.getName(), accessor.invoke(value));
                } catch (ReflectiveOperationException e) {
                    map.put(component.getName(), "(unreadable)");
                }
            }
            return map;
        }
        map.put("value", value);
        return map;
    }

    /** Snapshot -> JSON text, or null when there is nothing. */
    public static String toJson(Object value) {
        Map<String, Object> map = snapshot(value);
        return map == null ? null : write(map);
    }

    private static String write(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(quote(String.valueOf(e.getKey()))).append(':').append(write(e.getValue()));
            }
            return sb.append('}').toString();
        }
        if (value instanceof Collection<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(write(item));
            }
            return sb.append(']').toString();
        }
        // Text, dates, times and anything else: stored as text.
        return quote(value.toString());
    }

    private static String quote(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}