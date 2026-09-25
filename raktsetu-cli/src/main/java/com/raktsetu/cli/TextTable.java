package com.raktsetu.cli;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/** A plain-text table with columns sized to fit their contents. */
final class TextTable {

    private final List<String> header;
    private final List<List<String>> rows = new ArrayList<>();

    TextTable(String... header) {
        this.header = List.of(header);
    }

    TextTable row(Object... cells) {
        if (cells.length != header.size()) {
            throw new IllegalArgumentException("Expected " + header.size() + " cells, got " + cells.length);
        }
        List<String> row = new ArrayList<>();
        for (Object c : cells) {
            row.add(String.valueOf(c));
        }
        rows.add(row);
        return this;
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }

    void print(PrintStream out) {
        int[] widths = new int[header.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = header.get(i).length();
            for (List<String> row : rows) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }
        printRow(out, header, widths);
        StringBuilder rule = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            rule.append("  ").append("-".repeat(widths[i]));
        }
        out.println(rule);
        for (List<String> row : rows) {
            printRow(out, row, widths);
        }
    }

    private static void printRow(PrintStream out, List<String> cells, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            line.append("  ").append(String.format("%-" + widths[i] + "s", cells.get(i)));
        }
        out.println(line.toString().stripTrailing());
    }
}
