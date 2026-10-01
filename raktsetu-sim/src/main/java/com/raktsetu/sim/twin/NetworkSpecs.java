package com.raktsetu.sim.twin;

import com.raktsetu.domain.exception.InvalidDataException;
import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.FacilityType;
import com.raktsetu.domain.model.RouteEntry;
import com.raktsetu.persistence.CsvFiles;
import com.raktsetu.persistence.CsvMapper;
import com.raktsetu.persistence.CsvRow;
import com.raktsetu.persistence.DataMappers;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Loads a network from a folder holding {@code sites.csv}, {@code routes.csv} and {@code spec.properties} (see
 * {@code data/networks/}). Unlike the app's data loader, this is strict: experiments must not silently skip rows.
 */
public final class NetworkSpecs {

    private NetworkSpecs() {
    }

    private static final CsvMapper<Site> SITES = new CsvMapper<>() {
        @Override
        public List<String> header() {
            return List.of("id", "name", "type", "latitude", "longitude", "demand_weight", "supply_weight", "area");
        }

        @Override
        public Site fromRow(CsvRow row) throws InvalidDataException {
            FacilityType type;
            try {
                type = FacilityType.parse(row.text("type"));
            } catch (IllegalArgumentException e) {
                throw row.invalid(e.getMessage());
            }
            String area = row.text("area").toLowerCase(Locale.ROOT);
            if (!area.equals("urban") && !area.equals("rural")) {
                throw row.invalid("'area' must be urban or rural, was '" + area + "'");
            }
            try {
                return new Site(row.text("id"), row.text("name"), type, row.decimal("latitude"),
                        row.decimal("longitude"), row.decimal("demand_weight"), row.decimal("supply_weight"),
                        area.equals("rural"));
            } catch (IllegalArgumentException e) {
                throw row.invalid(e.getMessage());
            }
        }

        @Override
        public List<String> toRow(Site s) {
            return List.of(s.id(), s.name(), s.type().name(), String.valueOf(s.latitude()),
                    String.valueOf(s.longitude()), String.valueOf(s.demandWeight()), String.valueOf(s.supplyWeight()),
                    s.rural() ? "rural" : "urban");
        }
    };

    /**
     * Loads and validates a network.
     *
     * @throws RaktSetuException if a file is missing or any row is invalid
     */
    public static NetworkSpec load(Path folder) throws RaktSetuException {
        List<String> problems = new ArrayList<>();
        List<Site> sites = CsvFiles.read(folder.resolve("sites.csv"), SITES, problems);
        List<RouteEntry> routes = CsvFiles.read(folder.resolve("routes.csv"), DataMappers.ROUTES, problems);
        Set<String> ids = new HashSet<>();
        for (Site s : sites) {
            if (!ids.add(s.id())) {
                problems.add("sites.csv: duplicate ID " + s.id());
            }
        }
        for (RouteEntry r : routes) {
            if (!ids.contains(r.fromId()) || !ids.contains(r.toId())) {
                problems.add("routes.csv: route " + r.fromId() + " -> " + r.toId() + " refers to an unknown site");
            }
        }
        if (!problems.isEmpty()) {
            throw new RaktSetuException("Invalid network in " + folder + ": " + String.join("; ", problems));
        }

        Properties p = new Properties();
        Path specFile = folder.resolve("spec.properties");
        try (Reader r = Files.newBufferedReader(specFile, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            throw new RaktSetuException("Could not read " + specFile + ": " + e.getMessage(), e);
        }
        Map<ComponentType, Double> demand = new EnumMap<>(ComponentType.class);
        Map<ComponentType, Double> supply = new EnumMap<>(ComponentType.class);
        for (ComponentType t : ComponentType.values()) {
            demand.put(t, number(p, "demand." + t.name(), specFile));
            supply.put(t, number(p, "supply_factor." + t.name(), specFile));
        }
        try {
            return new NetworkSpec(p.getProperty("name", folder.getFileName().toString()), sites, routes, demand,
                    supply);
        } catch (IllegalArgumentException e) {
            throw new RaktSetuException("Invalid network in " + folder + ": " + e.getMessage(), e);
        }
    }

    private static double number(Properties p, String key, Path file) throws RaktSetuException {
        String value = p.getProperty(key);
        if (value == null) {
            throw new RaktSetuException(file + " is missing " + key);
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new RaktSetuException(file + ": " + key + " must be a number, was '" + value + "'");
        }
    }
}
