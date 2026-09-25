package com.raktsetu.persistence;

import com.raktsetu.domain.exception.InvalidDataException;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.FacilityType;
import com.raktsetu.domain.model.Route;
import com.raktsetu.domain.model.RouteEntry;
import com.raktsetu.domain.model.UsageHistory;
import com.raktsetu.domain.model.WastageRecord;

import java.time.LocalDate;
import java.util.List;

/** The CSV formats of the data files (documented in {@code data/README.md}). */
public final class DataMappers {

    public static final CsvMapper<Facility> FACILITIES = new FacilityMapper();
    public static final CsvMapper<StockedUnit> UNITS = new UnitMapper();
    public static final CsvMapper<RouteEntry> ROUTES = new RouteMapper();
    public static final CsvMapper<UsageHistory.Entry> USAGE = new UsageMapper();
    public static final CsvMapper<WastageRecord> WASTAGE = new WastageMapper();

    private DataMappers() {
    }

    private static final class FacilityMapper implements CsvMapper<Facility> {
        @Override
        public List<String> header() {
            return List.of("id", "name", "type", "last_reported_on");
        }

        @Override
        public Facility fromRow(CsvRow row) throws InvalidDataException {
            FacilityType type;
            try {
                type = FacilityType.parse(row.text("type"));
            } catch (IllegalArgumentException e) {
                throw row.invalid(e.getMessage());
            }
            return type.newFacility(row.text("id"), row.text("name"), row.optionalDate("last_reported_on"));
        }

        @Override
        public List<String> toRow(Facility f) {
            LocalDate reported = f.getLastReportedOn();
            return List.of(f.getId(), f.getName(), f.getFacilityType().name(),
                    reported == null ? "" : reported.toString());
        }
    }

    private static final class UnitMapper implements CsvMapper<StockedUnit> {
        @Override
        public List<String> header() {
            return List.of("unit_id", "facility_id", "component", "blood_group", "collected_on", "expires_on");
        }

        @Override
        public StockedUnit fromRow(CsvRow row) throws InvalidDataException {
            String id = row.text("unit_id");
            String facilityId = row.text("facility_id");
            var component = row.component("component");
            var group = row.bloodGroup("blood_group");
            LocalDate collected = row.date("collected_on");
            LocalDate expires = row.optionalDate("expires_on");
            try {
                BloodComponent unit = expires == null
                        ? component.newUnit(id, group, collected)
                        : component.newUnit(id, group, collected, expires);
                return new StockedUnit(facilityId, unit);
            } catch (IllegalArgumentException e) {
                throw row.invalid(e.getMessage());
            }
        }

        @Override
        public List<String> toRow(StockedUnit s) {
            BloodComponent u = s.unit();
            boolean overridden = !u.getExpiryDate().equals(u.standardExpiryDate());
            return List.of(u.getId(), s.facilityId(), u.getType().name(), u.getBloodGroup().label(),
                    u.getCollectedOn().toString(), overridden ? u.getExpiryDate().toString() : "");
        }
    }

    private static final class RouteMapper implements CsvMapper<RouteEntry> {
        @Override
        public List<String> header() {
            return List.of("from_id", "to_id", "distance_km", "travel_minutes");
        }

        @Override
        public RouteEntry fromRow(CsvRow row) throws InvalidDataException {
            int km = row.integer("distance_km");
            int minutes = row.integer("travel_minutes");
            if (km < 0 || minutes < 0) {
                throw row.invalid("distance and travel time cannot be negative");
            }
            return new RouteEntry(row.text("from_id"), row.text("to_id"), new Route(km, minutes));
        }

        @Override
        public List<String> toRow(RouteEntry e) {
            return List.of(e.fromId(), e.toId(), String.valueOf(e.route().distanceKm()),
                    String.valueOf(e.route().travelMinutes()));
        }
    }

    private static final class UsageMapper implements CsvMapper<UsageHistory.Entry> {
        @Override
        public List<String> header() {
            return List.of("date", "facility_id", "component", "blood_group", "units_issued");
        }

        @Override
        public UsageHistory.Entry fromRow(CsvRow row) throws InvalidDataException {
            int units = row.integer("units_issued");
            if (units < 0) {
                throw row.invalid("units_issued cannot be negative");
            }
            return new UsageHistory.Entry(row.date("date"), row.text("facility_id"), row.component("component"),
                    row.bloodGroup("blood_group"), units);
        }

        @Override
        public List<String> toRow(UsageHistory.Entry e) {
            return List.of(e.date().toString(), e.facilityId(), e.component().name(), e.group().label(),
                    String.valueOf(e.units()));
        }
    }

    private static final class WastageMapper implements CsvMapper<WastageRecord> {
        @Override
        public List<String> header() {
            return List.of("date", "facility_id", "unit_id", "component", "blood_group");
        }

        @Override
        public WastageRecord fromRow(CsvRow row) throws InvalidDataException {
            return new WastageRecord(row.date("date"), row.text("facility_id"), row.text("unit_id"),
                    row.component("component"), row.bloodGroup("blood_group"));
        }

        @Override
        public List<String> toRow(WastageRecord w) {
            return w.toCsvRow();
        }
    }
}
