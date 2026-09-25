package com.raktsetu.persistence;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal CSV reading and writing (RFC 4180 style): fields containing commas, quotes or line breaks are quoted, and
 * quotes inside quoted fields are doubled.
 */
public final class Csv {

    private Csv() {
    }

    /**
     * Splits one line into fields.
     *
     * @throws IllegalArgumentException if a quoted field is not closed
     */
    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Unclosed quote");
        }
        fields.add(field.toString());
        return fields;
    }

    /** Joins fields into one line, quoting where needed. */
    public static String formatLine(List<String> fields) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            String f = fields.get(i) == null ? "" : fields.get(i);
            if (f.contains(",") || f.contains("\"") || f.contains("\n") || f.contains("\r")) {
                line.append('"').append(f.replace("\"", "\"\"")).append('"');
            } else {
                line.append(f);
            }
        }
        return line.toString();
    }
}
