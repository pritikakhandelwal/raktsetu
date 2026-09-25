package com.raktsetu.persistence;

import com.raktsetu.domain.exception.InvalidDataException;
import com.raktsetu.domain.exception.RaktSetuException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads and writes whole CSV files of any type, given a {@link CsvMapper}. */
public final class CsvFiles {

    private CsvFiles() {
    }

    /**
     * Reads every row. Blank lines and lines starting with {@code #} are ignored. A row that cannot be mapped is
     * skipped and its problem is added to {@code problems}.
     *
     * @throws RaktSetuException if the file cannot be read or has no header
     */
    public static <T> List<T> read(Path file, CsvMapper<T> mapper, List<String> problems) throws RaktSetuException {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RaktSetuException("Could not read " + file + ": " + e.getMessage(), e);
        }
        String source = file.getFileName().toString();
        Map<String, Integer> columns = null;
        List<T> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i == 0 && line.startsWith("﻿")) {
                line = line.substring(1); // byte-order mark written by Excel
            }
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int lineNumber = i + 1;
            List<String> cells;
            try {
                cells = Csv.parseLine(line);
            } catch (IllegalArgumentException e) {
                problems.add(new InvalidDataException(source, lineNumber, e.getMessage()).getMessage());
                continue;
            }
            if (columns == null) {
                columns = indexHeader(cells);
                continue;
            }
            try {
                result.add(mapper.fromRow(new CsvRow(source, lineNumber, columns, cells)));
            } catch (InvalidDataException e) {
                problems.add(e.getMessage());
            }
        }
        if (columns == null) {
            throw new RaktSetuException(source + " has no header row");
        }
        return result;
    }

    /** Writes all rows, replacing the file atomically so a crash never leaves it half written. */
    public static <T> void write(Path file, CsvMapper<T> mapper, List<? extends T> rows) throws RaktSetuException {
        List<String> lines = new ArrayList<>(rows.size() + 1);
        lines.add(Csv.formatLine(mapper.header()));
        for (T row : rows) {
            lines.add(Csv.formatLine(mapper.toRow(row)));
        }
        writeLines(file, lines);
    }

    /** Writes text lines, replacing the file atomically. */
    public static void writeLines(Path file, List<String> lines) throws RaktSetuException {
        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                for (String line : lines) {
                    w.write(line);
                    w.write('\n');
                }
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RaktSetuException("Could not write " + file + ": " + e.getMessage(), e);
        }
    }

    private static Map<String, Integer> indexHeader(List<String> cells) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            columns.put(cells.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        return columns;
    }
}
