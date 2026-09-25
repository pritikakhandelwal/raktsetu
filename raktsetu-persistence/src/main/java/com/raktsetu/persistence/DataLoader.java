package com.raktsetu.persistence;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.interfaces.StockDataSource;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.LoadResult;
import com.raktsetu.domain.model.NetworkData;
import com.raktsetu.domain.model.RouteEntry;
import com.raktsetu.domain.model.UnitIds;
import com.raktsetu.domain.model.UsageHistory;
import com.raktsetu.domain.model.WastageRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Loads and saves the network from a folder of CSV files (formats in {@code data/README.md}).
 *
 * <p>Loading is forgiving: a malformed row, a unit at an unknown facility or a duplicate ID is skipped and reported,
 * and everything else still loads.
 */
public final class DataLoader implements StockDataSource {

    public static final String FACILITIES = "facilities.csv";
    public static final String UNITS = "units.csv";
    public static final String DISTANCES = "distances.csv";
    public static final String USAGE = "usage.csv";
    public static final String WASTAGE = "wastage.csv";
    public static final String STATE = "state.properties";

    private static final String NEXT_SERIAL = "next_unit_serial";

    private final Path directory;

    public DataLoader(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    public Path directory() {
        return directory;
    }

    /** Whether the folder already holds a network (at least a facilities file). */
    public boolean hasData() {
        return Files.exists(directory.resolve(FACILITIES));
    }

    @Override
    public LoadResult load() throws RaktSetuException {
        if (!hasData()) {
            throw new RaktSetuException("No data found: " + directory.resolve(FACILITIES) + " does not exist");
        }
        List<String> problems = new ArrayList<>();

        CsvRepository<Facility> facilityRepo = new CsvRepository<>(directory.resolve(FACILITIES),
                DataMappers.FACILITIES);
        facilityRepo.load();
        problems.addAll(facilityRepo.problems());
        Map<String, Facility> facilities = new LinkedHashMap<>();
        for (Facility f : facilityRepo.findAll()) {
            facilities.put(f.getId(), f);
        }

        CsvRepository<StockedUnit> unitRepo = new CsvRepository<>(directory.resolve(UNITS), DataMappers.UNITS);
        unitRepo.load();
        problems.addAll(unitRepo.problems());
        for (StockedUnit s : unitRepo.findAll()) {
            Facility f = facilities.get(s.facilityId());
            if (f == null) {
                problems.add(UNITS + ": unit " + s.getId() + " is at unknown facility '" + s.facilityId() + "'");
            } else if (!f.canStore()) {
                problems.add(UNITS + ": unit " + s.getId() + " is at " + f.getId() + ", which does not store blood");
            } else {
                f.getInventory().add(s.unit());
            }
        }

        List<RouteEntry> routes = new ArrayList<>();
        for (RouteEntry r : readIfPresent(DISTANCES, DataMappers.ROUTES, problems)) {
            if (!facilities.containsKey(r.fromId()) || !facilities.containsKey(r.toId())) {
                problems.add(DISTANCES + ": route " + r.fromId() + " -> " + r.toId()
                        + " refers to an unknown facility");
            } else {
                routes.add(r);
            }
        }

        UsageHistory usage = new UsageHistory();
        for (UsageHistory.Entry e : readIfPresent(USAGE, DataMappers.USAGE, problems)) {
            usage.record(e.date(), e.facilityId(), e.component(), e.group(), e.units());
        }

        List<WastageRecord> wastage = readIfPresent(WASTAGE, DataMappers.WASTAGE, problems);

        long nextSerial = readNextSerial(facilities.values());
        NetworkData data = new NetworkData(new ArrayList<>(facilities.values()), routes, usage, wastage, nextSerial);
        return new LoadResult(data, problems);
    }

    @Override
    public void save(NetworkData data) throws RaktSetuException {
        CsvFiles.write(directory.resolve(FACILITIES), DataMappers.FACILITIES, data.facilities());
        List<StockedUnit> units = new ArrayList<>();
        for (Facility f : data.facilities()) {
            if (f.canStore()) {
                for (BloodComponent u : f.getInventory().allUnits()) {
                    units.add(new StockedUnit(f.getId(), u));
                }
            }
        }
        CsvFiles.write(directory.resolve(UNITS), DataMappers.UNITS, units);
        CsvFiles.write(directory.resolve(DISTANCES), DataMappers.ROUTES, data.routes());
        CsvFiles.write(directory.resolve(USAGE), DataMappers.USAGE, data.usage().entries());
        CsvFiles.write(directory.resolve(WASTAGE), DataMappers.WASTAGE, data.wastage());
        writeNextSerial(data.nextUnitSerial());
    }

    private <T> List<T> readIfPresent(String name, CsvMapper<T> mapper, List<String> problems)
            throws RaktSetuException {
        Path file = directory.resolve(name);
        return Files.exists(file) ? CsvFiles.read(file, mapper, problems) : new ArrayList<>();
    }

    /**
     * The next serial for new unit IDs. Taken from the state file when present, so IDs of issued units are never
     * reused; otherwise one more than the highest serial in stock.
     */
    private long readNextSerial(Iterable<Facility> facilities) throws RaktSetuException {
        Path file = directory.resolve(STATE);
        if (Files.exists(file)) {
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
                return Long.parseLong(p.getProperty(NEXT_SERIAL, "1").trim());
            } catch (IOException | NumberFormatException e) {
                throw new RaktSetuException("Could not read " + file + ": " + e.getMessage(), e);
            }
        }
        long max = 0;
        for (Facility f : facilities) {
            if (f.canStore()) {
                for (BloodComponent u : f.getInventory().allUnits()) {
                    max = Math.max(max, UnitIds.serialOf(u.getId()));
                }
            }
        }
        return max + 1;
    }

    private void writeNextSerial(long next) throws RaktSetuException {
        Properties p = new Properties();
        p.setProperty(NEXT_SERIAL, String.valueOf(next));
        Path file = directory.resolve(STATE);
        try {
            Files.createDirectories(directory);
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                p.store(w, "RaktSetu state");
            }
        } catch (IOException e) {
            throw new RaktSetuException("Could not write " + file + ": " + e.getMessage(), e);
        }
    }
}
