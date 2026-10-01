package com.raktsetu.engine;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.Compatibility;
import com.raktsetu.domain.model.ComponentType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps docs/clinical/compatibility-matrix.md, the table a clinician reviews, identical to what the code does. If a
 * rule changes, this test fails until the table is regenerated with {@code -Draktsetu.writeMatrix=true}, so the
 * reviewed table and the running code cannot silently drift apart.
 */
class ClinicalMatrixTest {

    private static final Path MATRIX = Path.of("..", "docs", "clinical", "compatibility-matrix.md");
    private static final LocalDate ANY_DATE = LocalDate.of(2026, 1, 1);

    @Test
    void reviewedMatrixMatchesTheCode() throws IOException {
        String generated = render();
        if (Boolean.getBoolean("raktsetu.writeMatrix")) {
            Files.createDirectories(MATRIX.getParent());
            Files.writeString(MATRIX, generated, StandardCharsets.UTF_8);
        }
        assertThat(Files.readString(MATRIX, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .as("docs/clinical/compatibility-matrix.md is out of date; rerun with -Draktsetu.writeMatrix=true "
                        + "and send the changed rules for clinical review")
                .isEqualTo(generated);
    }

    static String render() {
        StringBuilder md = new StringBuilder();
        md.append("# Compatibility matrix (generated)\n\n")
                .append("Generated from the code by `ClinicalMatrixTest`. Do not edit by hand: the build fails if this ")
                .append("file and the code disagree.\n\n")
                .append("Rows are the **donor** unit's group, columns the **recipient's** group.\n\n")
                .append("| Symbol | Meaning |\n|---|---|\n")
                .append("| `=` | Identical: the preferred match |\n")
                .append("| `C` | Compatible: a different group that is safe |\n")
                .append("| `W` | Allowed with a warning shown to staff |\n")
                .append("| `.` | Incompatible: never issued |\n");
        for (ComponentType type : ComponentType.values()) {
            BloodComponent sample = type.newUnit("SAMPLE", BloodGroup.O_NEG, ANY_DATE);
            md.append("\n## ").append(title(type)).append("\n\n")
                    .append("Shelf life ").append(sample.getShelfLifeDays()).append(" days; stored at ")
                    .append(sample.getStorageCondition()).append(".\n\n")
                    .append("| Donor \\ Recipient |");
            for (BloodGroup r : BloodGroup.values()) {
                md.append(' ').append(r.label()).append(" |");
            }
            md.append("\n|---|").append("---|".repeat(BloodGroup.values().length)).append('\n');
            for (BloodGroup d : BloodGroup.values()) {
                md.append("| **").append(d.label()).append("** |");
                for (BloodGroup r : BloodGroup.values()) {
                    md.append(' ').append(symbol(type.newUnit("SAMPLE", d, ANY_DATE).compatibilityWith(r).level()))
                            .append(" |");
                }
                md.append('\n');
            }
            md.append("\nOrder in which donor groups are tried for each recipient (first = preferred):\n\n")
                    .append("| Recipient | Donor groups tried |\n|---|---|\n");
            for (BloodGroup r : BloodGroup.values()) {
                List<BloodGroup> order = DonorPreference.forRecipient(type, r);
                md.append("| ").append(r.label()).append(" | ")
                        .append(order.stream().map(BloodGroup::label).collect(Collectors.joining(", ")))
                        .append(" |\n");
            }
            md.append("\nWarnings shown to staff (none if the list is empty):\n\n");
            for (BloodGroup d : BloodGroup.values()) {
                for (BloodGroup r : BloodGroup.values()) {
                    var result = type.newUnit("SAMPLE", d, ANY_DATE).compatibilityWith(r);
                    if (result.level() == Compatibility.ALLOWED_WITH_WARNING) {
                        md.append("- ").append(d.label()).append(" to ").append(r.label()).append(": ")
                                .append(result.reason()).append('\n');
                    }
                }
            }
        }
        return md.toString();
    }

    private static String title(ComponentType type) {
        return switch (type) {
            case WHOLE_BLOOD -> "Whole blood";
            case RED_CELLS -> "Red cell concentrate";
            case PLATELETS -> "Platelet concentrate";
            case PLASMA -> "Fresh frozen plasma";
        };
    }

    private static String symbol(Compatibility c) {
        return switch (c) {
            case IDENTICAL -> "=";
            case COMPATIBLE -> "C";
            case ALLOWED_WITH_WARNING -> "W";
            case INCOMPATIBLE -> ".";
        };
    }
}
