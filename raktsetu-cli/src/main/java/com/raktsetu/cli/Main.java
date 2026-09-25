package com.raktsetu.cli;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.CompatibilityResult;
import com.raktsetu.domain.model.ComponentType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * RaktSetu console application.
 *
 * <p>Phase 0 skeleton: the menu is complete and input handling is robust; options are wired in as their modules land
 * (see the roadmap, Section 6).
 */
public final class Main {

    private static final String[] MENU = {
            "View centre stock",
            "View network-wide stock",
            "Add blood units",
            "Raise an emergency request",
            "Check compatibility",
            "Run the expiry-risk check",
            "Generate a redistribution plan",
            "Compare strategies",
            "Approve or reject transfers",
            "View audit log and verify hash chain",
            "Run the 30-day simulation",
            "View the wastage summary",
            "Export reports (CSV)",
            "Data-freshness report",
            "Set simulation date (\"today\")",
            "Save and exit",
    };

    /** Which roadmap module delivers each option that is not wired in yet. */
    private static final String[] PLANNED_IN = {
            "M7", "M7", "M4/M7", "M5", null, "M5", "M5", "M5", "M5", "M4", "M6", "M7", "M4", "M5", null, "M4",
    };

    private final BufferedReader in;
    private final PrintStream out;
    private LocalDate today = LocalDate.now();

    Main(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        new Main(in, System.out).run();
    }

    void run() throws IOException {
        out.println("RaktSetu - expiry-aware blood redistribution network");
        while (true) {
            printMenu();
            String line = prompt("Choose an option (1-" + MENU.length + ")");
            if (line == null) {
                return; // end of input
            }
            int choice;
            try {
                choice = Integer.parseInt(line.trim());
            } catch (NumberFormatException e) {
                out.println("Please enter a number between 1 and " + MENU.length + ".");
                continue;
            }
            if (choice < 1 || choice > MENU.length) {
                out.println("Please enter a number between 1 and " + MENU.length + ".");
                continue;
            }
            switch (choice) {
                case 5 -> checkCompatibility();
                case 15 -> setToday();
                case 16 -> {
                    out.println("Goodbye.");
                    return;
                }
                default -> out.println("'" + MENU[choice - 1] + "' is not built yet (roadmap module "
                        + PLANNED_IN[choice - 1] + ").");
            }
        }
    }

    private void printMenu() {
        out.println();
        out.println("===== RAKTSETU  (today: " + today + ") =====");
        for (int i = 0; i < MENU.length; i++) {
            out.printf("%2d. %s%n", i + 1, MENU[i]);
        }
    }

    private void checkCompatibility() throws IOException {
        try {
            ComponentType type = ComponentType.parse(require("Component (WHOLE_BLOOD, RED_CELLS, PLATELETS, PLASMA)"));
            BloodGroup donor = BloodGroup.parse(require("Donor blood group (e.g. O-)"));
            BloodGroup recipient = BloodGroup.parse(require("Recipient blood group (e.g. AB+)"));
            BloodComponent probe = type.newUnit("CHECK", donor, today);
            CompatibilityResult result = probe.compatibilityWith(recipient);
            out.println((result.isAllowed() ? "ALLOWED" : "NOT ALLOWED") + " (" + result.level() + "): "
                    + result.reason());
        } catch (IllegalArgumentException e) {
            out.println("Input error: " + e.getMessage());
        }
    }

    private void setToday() throws IOException {
        try {
            today = LocalDate.parse(require("New date (YYYY-MM-DD)"));
            out.println("Today is now " + today + ".");
        } catch (DateTimeParseException e) {
            out.println("Input error: dates must look like 2026-10-01.");
        }
    }

    private String require(String label) throws IOException {
        String value = prompt(label);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private String prompt(String label) throws IOException {
        out.print(label + ": ");
        out.flush();
        return in.readLine();
    }
}
