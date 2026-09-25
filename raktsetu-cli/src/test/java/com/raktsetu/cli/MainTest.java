package com.raktsetu.cli;

import com.raktsetu.persistence.DataLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end tests: each one runs the real application on a temporary data folder with scripted input. */
class MainTest {

    private static final String TODAY = "2026-10-01";

    @TempDir
    Path dir;

    private record Run(int exitCode, String output) {
    }

    private Run run(String input, String... extraArgs) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        String[] args = new String[4 + extraArgs.length];
        args[0] = "--data";
        args[1] = dir.toString();
        args[2] = "--today";
        args[3] = TODAY;
        System.arraycopy(extraArgs, 0, args, 4, extraArgs.length);
        int code = Main.run(args, new BufferedReader(new StringReader(input)), out, LocalDate.of(2000, 1, 1));
        return new Run(code, bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void firstRunCreatesSampleDataAndSavesOnExit() {
        Run r = run("16\n");

        assertThat(r.exitCode()).isZero();
        assertThat(r.output()).contains("Created a sample network").contains("3 expired units were removed")
                .contains("Saved to");
        assertThat(dir.resolve(DataLoader.FACILITIES)).exists();
        assertThat(dir.resolve(DataLoader.UNITS)).exists();
        assertThat(dir.resolve(DataLoader.WASTAGE)).exists();
        assertThat(dir.resolve(Main.AUDIT_FILE)).exists();
    }

    @Test
    void everyMenuOptionWorks() {
        String script = String.join("\n",
                "1", "SC04",                                  // centre stock
                "2",                                          // network stock
                "3", "SC01", "PLATELETS", "AB-", "", "2",     // add units
                "4", "1", "H01", "RED_CELLS", "O-", "1", "E", // request by quantity
                "5", "PLASMA", "AB+", "O-",                   // compatibility (reversed plasma rule)
                "6",                                          // risk check
                "8",                                          // compare strategies
                "7", "2",                                     // plan with expiry-first
                "9", "a", "r", "q",                           // approve one, reject one
                "10",                                         // audit log
                "11", "5",                                    // 5-day simulation
                "12",                                         // wastage
                "13",                                         // export
                "14", "SC03",                                 // freshness, record a report
                "15", "2026-10-03",                           // move the date forward
                "16", "");
        Run r = run(script);

        assertThat(r.exitCode()).isZero();
        assertThat(r.output())
                .contains("URGENT")
                .contains("Added 2 x AB- PLATELETS to SC01")
                .contains("Request fulfilled: 1 unit")
                .contains("ALLOWED (COMPATIBLE)")
                .contains("units at risk")
                .contains("nearest-first").contains("expiry-first")
                .contains("Approved:").contains("Rejected.")
                .contains("Audit log intact")
                .contains("Wastage reduced by")
                .contains("Exported 5 reports")
                .contains("SC03 marked as reported")
                .contains("Today is now 2026-10-03")
                .doesNotContain("Input error")
                .doesNotContain("Exception");
        assertThat(dir.resolve("reports/simulation.csv")).exists();
        assertThat(dir.resolve("reports/stock.csv")).exists();
    }

    @Test
    void savedStateIsReloadedOnRestart() {
        run("3\nSC01\nPLASMA\nAB-\n\n3\n16\n");
        Run second = run("1\nSC01\n10\n16\n");

        assertThat(second.output()).doesNotContain("Created a sample network");
        assertThat(second.output()).containsPattern("PLASMA\\s+AB-\\s+\\d+");
        assertThat(second.output()).contains("Audit log intact");
    }

    @Test
    void badInputIsExplainedAndNeverCrashes() {
        String script = String.join("\n",
                "abc", "99",
                "1", "NOWHERE",
                "1", "H01",
                "3", "SC01", "PLATELETS", "Q+",
                "4", "1", "H01", "RED_CELLS", "O-", "999",
                "4", "1", "H01", "RED_CELLS", "O-", "50", "E",
                "4", "2", "SC01", "NO-SUCH-UNIT", "H01", "O+",
                "15", "2020-01-01",
                "15", "not a date",
                "9");
        Run r = run(script);

        assertThat(r.exitCode()).isZero();
        assertThat(r.output())
                .contains("Please enter a number between 1 and 16")
                .contains("No facility with ID 'NOWHERE'")
                .contains("is a hospital and holds no stock")
                .contains("Unknown blood group")
                .contains("Units needed must be between 1 and 50")
                .contains("Not enough compatible RED_CELLS")
                .contains("is not in stock")
                .contains("can only move forward")
                .contains("Dates must look like")
                .contains("No transfers are waiting")
                .contains("Input ended");
    }

    @Test
    void tamperingWithTheAuditLogIsDetected() throws Exception {
        run("4\n1\nH06\nPLATELETS\nB+\n1\nE\n16\n");
        Path audit = dir.resolve(Main.AUDIT_FILE);
        Files.writeString(audit, Files.readString(audit).replace("H06", "H05"));

        assertThat(run("10\n16\n").output()).contains("Audit log TAMPERED");
    }

    @Test
    void corruptedRowsAreReportedAtStartup() throws Exception {
        run("16\n");
        Path units = dir.resolve(DataLoader.UNITS);
        Files.writeString(units, Files.readString(units) + "RS-999999,SC01,RED_CELLS,O+,yesterday,\n");

        assertThat(run("16\n").output()).contains("Skipped 1 problem row").contains("units.csv, line");
    }

    @Test
    void commandLineIsValidated() {
        assertThat(run("", "--seed", "abc").exitCode()).isEqualTo(2);
        assertThat(run("", "--bogus").output()).contains("Unknown option: --bogus");
        assertThat(run("", "--help").exitCode()).isZero();
    }
}
