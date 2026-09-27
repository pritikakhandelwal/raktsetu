package com.raktsetu.persistence;

import com.raktsetu.domain.exception.InvalidDataException;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * One data row with typed accessors. Every accessor reports problems as {@link InvalidDataException} with the file
 * name and line number, so a bad row can be skipped with a clear message.
 */
public final class CsvRow {

    private final String source;
    private final int lineNumber;
    private final Map<String, Integer> columns;
    private final List<String> cells;

    CsvRow(String source, int lineNumber, Map<String, Integer> columns, List<String> cells) {
        this.source = source;
        this.lineNumber = lineNumber;
        this.columns = columns;
        this.cells = cells;
    }

    public int lineNumber() {
        return lineNumber;
    }

    /** A required, non-blank text value. */
    public String text(String column) throws InvalidDataException {
        String value = optional(column);
        if (value.isEmpty()) {
            throw invalid("'" + column + "' is missing");
        }
        return value;
    }

    /** A text value that may be blank; returns an empty string when absent. */
    public String optional(String column) {
        Integer index = columns.get(column);
        if (index == null || index >= cells.size()) {
            return "";
        }
        return cells.get(index).trim();
    }

    public int integer(String column) throws InvalidDataException {
        String value = text(column);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw invalid("'" + column + "' must be a whole number, was '" + value + "'");
        }
    }

    public double decimal(String column) throws InvalidDataException {
        String value = text(column);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw invalid("'" + column + "' must be a number, was '" + value + "'");
        }
    }

    public LocalDate date(String column) throws InvalidDataException {
        return parseDate(column, text(column));
    }

    /** A date that may be blank; returns {@code null} when blank. */
    public LocalDate optionalDate(String column) throws InvalidDataException {
        String value = optional(column);
        return value.isEmpty() ? null : parseDate(column, value);
    }

    public BloodGroup bloodGroup(String column) throws InvalidDataException {
        try {
            return BloodGroup.parse(text(column));
        } catch (IllegalArgumentException e) {
            throw invalid(e.getMessage());
        }
    }

    public ComponentType component(String column) throws InvalidDataException {
        try {
            return ComponentType.parse(text(column));
        } catch (IllegalArgumentException e) {
            throw invalid(e.getMessage());
        }
    }

    /** An error for this row, for checks that the mapper does itself. */
    public InvalidDataException invalid(String problem) {
        return new InvalidDataException(source, lineNumber, problem);
    }

    private LocalDate parseDate(String column, String value) throws InvalidDataException {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw invalid("'" + column + "' must be a date like 2026-10-01, was '" + value + "'");
        }
    }
}
