package com.raktsetu.server.db;

import com.raktsetu.domain.model.AuditEvent;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.FacilityType;
import com.raktsetu.domain.model.NetworkData;
import com.raktsetu.domain.model.Route;
import com.raktsetu.domain.model.RouteEntry;
import com.raktsetu.domain.model.UsageHistory;
import com.raktsetu.domain.model.WastageRecord;
import com.raktsetu.engine.BloodNetwork;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores the network in the database. The engine works on an in-memory {@link BloodNetwork}; after every command the
 * service passes the command's audit events here, and {@link #apply} makes the same change to the tables. The audit
 * events are exactly the list of what changed: units collected, issued, moved or discarded.
 */
@Repository
public class NetworkRepository {

    private final JdbcClient jdbc;

    public NetworkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isEmpty() {
        return jdbc.sql("select count(*) from facility").query(Integer.class).single() == 0;
    }

    /** Stores a whole network in empty tables (first start). */
    public void seed(NetworkData data) {
        int position = 0;
        for (Facility f : data.facilities()) {
            jdbc.sql("insert into facility (id, name, type, last_reported_on, position) values (?, ?, ?, ?, ?)")
                    .params(f.getId(), f.getName(), f.getFacilityType().name(), f.getLastReportedOn(), position++)
                    .update();
        }
        for (RouteEntry r : data.routes()) {
            jdbc.sql("insert into route (from_id, to_id, distance_km, travel_minutes) values (?, ?, ?, ?)")
                    .params(r.fromId(), r.toId(), r.route().distanceKm(), r.route().travelMinutes()).update();
        }
        for (Facility f : data.facilities()) {
            if (f.canStore()) {
                for (BloodComponent u : f.getInventory().allUnits()) {
                    insertUnit(f.getId(), u);
                }
            }
        }
        for (UsageHistory.Entry e : data.usage().entries()) {
            addUsage(e.date(), e.facilityId(), e.component(), e.group(), e.units());
        }
        for (WastageRecord w : data.wastage()) {
            insertWastage(w, "expired");
        }
    }

    /** Loads the whole network. */
    public NetworkData load(long nextUnitSerial) {
        Map<String, Facility> facilities = new LinkedHashMap<>();
        jdbc.sql("select id, name, type, last_reported_on from facility order by position").query((rs, n) -> {
            Facility f = FacilityType.parse(rs.getString("type")).newFacility(rs.getString("id"), rs.getString("name"),
                    rs.getObject("last_reported_on", LocalDate.class));
            facilities.put(f.getId(), f);
            return f;
        }).list();
        jdbc.sql("select id, facility_id, component, blood_group, collected_on, expires_on from unit order by id")
                .query((rs, n) -> {
                    ComponentType type = ComponentType.valueOf(rs.getString("component"));
                    BloodGroup group = BloodGroup.parse(rs.getString("blood_group"));
                    LocalDate collected = rs.getObject("collected_on", LocalDate.class);
                    LocalDate expires = rs.getObject("expires_on", LocalDate.class);
                    BloodComponent unit = type.newUnit(rs.getString("id"), group, collected);
                    if (!unit.getExpiryDate().equals(expires)) {
                        unit = type.newUnit(rs.getString("id"), group, collected, expires);
                    }
                    facilities.get(rs.getString("facility_id")).getInventory().add(unit);
                    return unit;
                }).list();
        List<RouteEntry> routes = jdbc.sql("select from_id, to_id, distance_km, travel_minutes from route")
                .query((rs, n) -> new RouteEntry(rs.getString("from_id"), rs.getString("to_id"),
                        new Route(rs.getInt("distance_km"), rs.getInt("travel_minutes")))).list();
        UsageHistory usage = new UsageHistory();
        jdbc.sql("select usage_day, facility_id, component, blood_group, units from usage_daily").query((rs, n) -> {
            usage.record(rs.getObject("usage_day", LocalDate.class), rs.getString("facility_id"),
                    ComponentType.valueOf(rs.getString("component")), BloodGroup.parse(rs.getString("blood_group")),
                    rs.getInt("units"));
            return n;
        }).list();
        List<WastageRecord> wastage = jdbc.sql(
                "select wastage_day, facility_id, unit_id, component, blood_group from wastage order by id")
                .query((rs, n) -> new WastageRecord(rs.getObject("wastage_day", LocalDate.class), rs.getString("facility_id"),
                        rs.getString("unit_id"), ComponentType.valueOf(rs.getString("component")),
                        BloodGroup.parse(rs.getString("blood_group")))).list();
        return new NetworkData(new ArrayList<>(facilities.values()), routes, usage, wastage, nextUnitSerial);
    }

    /**
     * Writes one command's changes. {@code network} is the in-memory network after the command, used to look up the
     * details of units that were added.
     */
    public void apply(List<AuditEvent> events, BloodNetwork network) {
        for (AuditEvent e : events) {
            switch (e.type()) {
                case COLLECTED, RETURNED -> {
                    for (String id : e.unitIds()) {
                        insertUnit(e.facilityId(), findUnit(network, e.facilityId(), id));
                    }
                }
                case ISSUED -> {
                    deleteUnits(e.unitIds());
                    addUsage(e.date(), e.facilityId(), e.component(), e.group(), e.unitIds().size());
                }
                case DISCARDED -> {
                    deleteUnits(e.unitIds());
                    for (String id : e.unitIds()) {
                        insertWastage(new WastageRecord(e.date(), e.facilityId(), id, e.component(), e.group()),
                                e.note().isBlank() ? "expired" : e.note());
                    }
                }
                case TRANSFERRED -> {
                    for (String id : e.unitIds()) {
                        jdbc.sql("update unit set facility_id = ? where id = ?").params(e.counterparty(), id).update();
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ facilities and routes (administration)

    /** Where a facility is. Any field may be null for facilities created before locations were recorded. */
    public record Location(Double latitude, Double longitude, String address) {
    }

    /** One stored route, with where its figures came from (OSRM, ESTIMATE or MANUAL). */
    public record RouteRow(String fromId, String toId, int distanceKm, int travelMinutes, String source) {
    }

    public Map<String, Location> locations() {
        Map<String, Location> map = new LinkedHashMap<>();
        jdbc.sql("select id, latitude, longitude, address from facility order by position").query((rs, n) -> {
            map.put(rs.getString("id"), new Location(rs.getObject("latitude", Double.class),
                    rs.getObject("longitude", Double.class), rs.getString("address")));
            return n;
        }).list();
        return map;
    }

    /** The next free ID with this prefix: HUB01, SC03, H12... */
    public String nextFacilityId(String prefix) {
        int max = jdbc.sql("select id from facility where id like ?").param(prefix + "%").query(String.class).list()
                .stream().map(id -> id.substring(prefix.length()))
                .filter(s -> s.matches("\\d+")).mapToInt(Integer::parseInt).max().orElse(0);
        return String.format(java.util.Locale.ROOT, "%s%02d", prefix, max + 1);
    }

    public void insertFacility(String id, String name, FacilityType type, double latitude, double longitude,
                               String address) {
        int position = jdbc.sql("select count(*) from facility").query(Integer.class).single();
        jdbc.sql("insert into facility (id, name, type, last_reported_on, position, latitude, longitude, address) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, name, type.name(), null, position, latitude, longitude, address)
                .update();
    }

    public void updateFacility(String id, String name, double latitude, double longitude, String address) {
        jdbc.sql("update facility set name = ?, latitude = ?, longitude = ?, address = ? where id = ?")
                .params(name, latitude, longitude, address, id).update();
    }

    /** Replaces every route to and from a facility. */
    public void replaceRoutes(String facilityId, List<RouteRow> routes) {
        jdbc.sql("delete from route where from_id = ? or to_id = ?").params(facilityId, facilityId).update();
        for (RouteRow r : routes) {
            upsertRoute(r);
        }
    }

    public void upsertRoute(RouteRow r) {
        int updated = jdbc.sql("update route set distance_km = ?, travel_minutes = ?, source = ? "
                        + "where from_id = ? and to_id = ?")
                .params(r.distanceKm(), r.travelMinutes(), r.source(), r.fromId(), r.toId()).update();
        if (updated == 0) {
            jdbc.sql("insert into route (from_id, to_id, distance_km, travel_minutes, source) values (?, ?, ?, ?, ?)")
                    .params(r.fromId(), r.toId(), r.distanceKm(), r.travelMinutes(), r.source()).update();
        }
    }

    public List<RouteRow> routesFrom(String facilityId) {
        return jdbc.sql("select from_id, to_id, distance_km, travel_minutes, source from route where from_id = ? "
                        + "order by travel_minutes")
                .param(facilityId)
                .query((rs, n) -> new RouteRow(rs.getString("from_id"), rs.getString("to_id"),
                        rs.getInt("distance_km"), rs.getInt("travel_minutes"), rs.getString("source")))
                .list();
    }

    /** Saves each facility's last report date. */
    public void saveReportDates(List<Facility> facilities) {
        for (Facility f : facilities) {
            jdbc.sql("update facility set last_reported_on = ? where id = ?")
                    .params(f.getLastReportedOn(), f.getId()).update();
        }
    }

    private static BloodComponent findUnit(BloodNetwork network, String facilityId, String unitId) {
        Optional<BloodComponent> here = network.storingFacilities().stream()
                .filter(f -> f.getId().equals(facilityId))
                .flatMap(f -> f.getInventory().find(unitId).stream()).findFirst();
        return here.orElseGet(() -> network.storingFacilities().stream()
                .flatMap(f -> f.getInventory().find(unitId).stream()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unit " + unitId + " is not in the network")));
    }

    private void insertUnit(String facilityId, BloodComponent u) {
        jdbc.sql("insert into unit (id, facility_id, component, blood_group, collected_on, expires_on) "
                        + "values (?, ?, ?, ?, ?, ?)")
                .params(u.getId(), facilityId, u.getType().name(), u.getBloodGroup().label(), u.getCollectedOn(),
                        u.getExpiryDate())
                .update();
    }

    private void deleteUnits(List<String> ids) {
        for (String id : ids) {
            jdbc.sql("delete from unit where id = ?").param(id).update();
        }
    }

    private void addUsage(LocalDate day, String facilityId, ComponentType type, BloodGroup group, int units) {
        int updated = jdbc.sql("update usage_daily set units = units + ? "
                        + "where usage_day = ? and facility_id = ? and component = ? and blood_group = ?")
                .params(units, day, facilityId, type.name(), group.label()).update();
        if (updated == 0) {
            jdbc.sql("insert into usage_daily (usage_day, facility_id, component, blood_group, units) values (?, ?, ?, ?, ?)")
                    .params(day, facilityId, type.name(), group.label(), units).update();
        }
    }

    private void insertWastage(WastageRecord w, String reason) {
        jdbc.sql("insert into wastage (wastage_day, facility_id, unit_id, component, blood_group, reason) "
                        + "values (?, ?, ?, ?, ?, ?)")
                .params(w.date(), w.facilityId(), w.unitId(), w.component().name(), w.group().label(), reason)
                .update();
    }
}
