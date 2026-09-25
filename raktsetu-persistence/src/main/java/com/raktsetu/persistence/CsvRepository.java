package com.raktsetu.persistence;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.interfaces.Identifiable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link Repository} kept in memory and backed by one CSV file. Call {@link #load()} to read the file and
 * {@link #flush()} to write it back.
 *
 * @param <T> the stored type
 */
public final class CsvRepository<T extends Identifiable> implements Repository<T> {

    private final Path file;
    private final CsvMapper<T> mapper;
    private final Map<String, T> items = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    public CsvRepository(Path file, CsvMapper<T> mapper) {
        this.file = Objects.requireNonNull(file, "file");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Replaces the contents with the file's rows. A missing file means an empty repository. Bad rows and duplicate IDs
     * are skipped and listed in {@link #problems()}.
     */
    public void load() throws RaktSetuException {
        items.clear();
        problems.clear();
        if (!Files.exists(file)) {
            return;
        }
        for (T item : CsvFiles.read(file, mapper, problems)) {
            if (items.putIfAbsent(item.getId(), item) != null) {
                problems.add(file.getFileName() + ": duplicate ID '" + item.getId() + "' skipped");
            }
        }
    }

    public void flush() throws RaktSetuException {
        CsvFiles.write(file, mapper, new ArrayList<>(items.values()));
    }

    /** Problems found by the last {@link #load()}. */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    @Override
    public void save(T item) {
        items.put(item.getId(), item);
    }

    @Override
    public Optional<T> findById(String id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public List<T> findAll() {
        return List.copyOf(items.values());
    }

    @Override
    public boolean delete(String id) {
        return items.remove(id) != null;
    }
}
