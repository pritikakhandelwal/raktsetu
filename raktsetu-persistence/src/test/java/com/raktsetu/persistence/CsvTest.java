package com.raktsetu.persistence;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvTest {

    @Test
    void parsesQuotedFieldsWithCommasAndQuotes() {
        assertThat(Csv.parseLine("a,\"b, c\",\"say \"\"hi\"\"\",")).containsExactly("a", "b, c", "say \"hi\"", "");
    }

    @Test
    void rejectsUnclosedQuote() {
        assertThatThrownBy(() -> Csv.parseLine("a,\"b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Property
    void formatThenParseGivesBackTheSameFields(@ForAll @Size(min = 1, max = 6) List<String> fields) {
        // Line breaks are only allowed inside quotes in multi-line files, which we never write.
        List<String> singleLine = fields.stream().map(f -> f.replace("\n", " ").replace("\r", " ")).toList();
        assertThat(Csv.parseLine(Csv.formatLine(singleLine))).isEqualTo(singleLine);
    }
}
