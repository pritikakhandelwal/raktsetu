package com.raktsetu.server.db;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The directory of Indian blood centres and hospitals (table {@code directory_facility}), from open government data.
 * Reference only: an entry joins the network when an administrator approves someone from it.
 */
@Repository
public class DirectoryRepository {

    /** A blood centre or hospital in the directory. */
    public record Entry(String id, String kind, String name, String category, String state, String district,
                        String city, String address, double latitude, double longitude) {

        /**
         * Where it is, for showing beside the name: "Pune, Maharashtra". Blood centres give their town; hospitals give
         * their district, because the hospital data's locality field often holds a street description instead.
         */
        public String place() {
            String town = kind.equals(BLOOD_CENTRE) && !city.isBlank() ? city : district;
            if (town.isBlank()) {
                town = city;
            }
            if (town.isBlank() || town.equalsIgnoreCase(state)) {
                return state;
            }
            return state.isBlank() ? town : town + ", " + state;
        }
    }

    /** Blood centres in the directory; the rest are hospitals. */
    public static final String BLOOD_CENTRE = "BLOOD_CENTRE";
    public static final String HOSPITAL = "HOSPITAL";

    private final JdbcClient jdbc;

    public DirectoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Entries of one kind whose name or place contains every word of {@code text}, not already in the network,
     * best first (names starting with the first word, then alphabetical). Empty for fewer than 2 characters.
     */
    public List<Entry> search(String kind, String text, int limit) {
        String[] words = Arrays.stream((text == null ? "" : text).toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(w -> !w.isBlank()).limit(5).toArray(String[]::new);
        if (words.length == 0 || String.join("", words).length() < 2) {
            return List.of();
        }
        StringBuilder where = new StringBuilder("kind = ? and id not in "
                + "(select directory_id from facility where directory_id is not null)");
        List<Object> params = new ArrayList<>();
        params.add(kind);
        for (String w : words) {
            where.append(" and search_text like ?");
            params.add("%" + escape(w) + "%");
        }
        params.add(escape(words[0]) + "%");
        return jdbc.sql("select * from directory_facility where " + where
                        + " order by case when lower(name) like ? then 0 else 1 end, name limit " + limit)
                .params(params).query(DirectoryRepository::entry).list();
    }

    public Optional<Entry> find(String id) {
        return jdbc.sql("select * from directory_facility where id = ?").param(id).query(DirectoryRepository::entry)
                .optional();
    }

    public int count() {
        return jdbc.sql("select count(*) from directory_facility").query(Integer.class).single();
    }

    /** The network facility already created from this entry, if any. */
    public Optional<String> networkFacility(String directoryId) {
        return jdbc.sql("select id from facility where directory_id = ?").param(directoryId).query(String.class)
                .optional();
    }

    public void link(String facilityId, String directoryId) {
        jdbc.sql("update facility set directory_id = ? where id = ?").params(directoryId, facilityId).update();
    }

    /** Drops LIKE wildcards, so typed text is matched as text, never as a pattern. */
    private static String escape(String word) {
        return word.replace("%", "").replace("_", "");
    }

    private static Entry entry(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Entry(rs.getString("id"), rs.getString("kind"), rs.getString("name"), rs.getString("category"),
                rs.getString("state"), rs.getString("district"), rs.getString("city"), rs.getString("address"),
                rs.getDouble("latitude"), rs.getDouble("longitude"));
    }
}
