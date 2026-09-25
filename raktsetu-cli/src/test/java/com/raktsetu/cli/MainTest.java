package com.raktsetu.cli;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    private static String runWith(String input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        new Main(new BufferedReader(new StringReader(input)), out).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void checksCompatibilityIncludingThePlasmaReversal() throws Exception {
        String output = runWith("5\nPLASMA\nAB+\nO-\n5\nRED_CELLS\nAB+\nO-\n16\n");
        assertThat(output).contains("ALLOWED (COMPATIBLE)").contains("NOT ALLOWED (INCOMPATIBLE)");
    }

    @Test
    void survivesBadInputAndEndOfInput() throws Exception {
        String output = runWith("abc\n99\n5\nserum\n15\nnot-a-date\n");
        assertThat(output)
                .contains("Please enter a number")
                .contains("Input error: Unknown component")
                .contains("Input error: dates must look like");
    }

    @Test
    void setsTheDateUsedByTheApp() throws Exception {
        assertThat(runWith("15\n2026-10-01\n16\n")).contains("today: 2026-10-01");
    }
}
