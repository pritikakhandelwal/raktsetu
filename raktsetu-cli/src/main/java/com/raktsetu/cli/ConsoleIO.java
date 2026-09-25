package com.raktsetu.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Console input and output. Every read either returns valid input or throws {@link IllegalArgumentException} with a
 * message fit for staff, so actions never crash on bad input.
 */
final class ConsoleIO {

    /** Thrown when input ends (for example Ctrl+Z, or the end of a scripted test). */
    static final class EndOfInput extends RuntimeException {
        private static final long serialVersionUID = 1L;

        EndOfInput() {
            super("End of input");
        }
    }

    private final BufferedReader in;
    private final PrintStream out;

    ConsoleIO(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    PrintStream out() {
        return out;
    }

    void println(String text) {
        out.println(text);
    }

    void println() {
        out.println();
    }

    void printf(String format, Object... args) {
        out.printf(format, args);
    }

    /** Reads a line, trimmed; may be empty. */
    String optional(String label) {
        out.print(label + ": ");
        out.flush();
        String line;
        try {
            line = in.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (line == null) {
            throw new EndOfInput();
        }
        return line.trim();
    }

    String text(String label) {
        String value = optional(label);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    int integer(String label, int min, int max) {
        String value = text(label);
        try {
            int n = Integer.parseInt(value);
            if (n < min || n > max) {
                throw new IllegalArgumentException(label + " must be between " + min + " and " + max);
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a whole number, not '" + value + "'");
        }
    }

    /** An integer with a default used when the input is blank. */
    int integer(String label, int min, int max, int defaultValue) {
        String value = optional(label + " [" + defaultValue + "]");
        if (value.isEmpty()) {
            return defaultValue;
        }
        try {
            int n = Integer.parseInt(value);
            if (n < min || n > max) {
                throw new IllegalArgumentException(label + " must be between " + min + " and " + max);
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a whole number, not '" + value + "'");
        }
    }

    /** A date, or {@code defaultValue} if blank. */
    LocalDate date(String label, LocalDate defaultValue) {
        String value = optional(label + " [" + defaultValue + "]");
        if (value.isEmpty()) {
            return defaultValue;
        }
        return parseDate(value);
    }

    static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Dates must look like 2026-10-01, not '" + value + "'");
        }
    }
}
