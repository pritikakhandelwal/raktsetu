package com.raktsetu.persistence;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.interfaces.Exportable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Writes reports as CSV files that open directly in Excel. */
public final class ReportWriter {

    private final Path directory;

    public ReportWriter(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /**
     * Writes one report.
     *
     * @param fileName name of the file inside the reports folder
     * @param header   column names
     * @param rows     anything exportable; each row must have as many cells as the header
     * @return the file written
     */
    public Path write(String fileName, List<String> header, List<? extends Exportable> rows) throws RaktSetuException {
        List<String> lines = new ArrayList<>(rows.size() + 1);
        lines.add(Csv.formatLine(header));
        for (Exportable row : rows) {
            List<String> cells = row.toCsvRow();
            if (cells.size() != header.size()) {
                throw new IllegalArgumentException(fileName + ": row has " + cells.size() + " cells but the header has "
                        + header.size());
            }
            lines.add(Csv.formatLine(cells));
        }
        Path file = directory.resolve(fileName);
        CsvFiles.writeLines(file, lines);
        return file;
    }
}
