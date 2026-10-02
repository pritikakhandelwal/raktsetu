package db.migration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Loads a directory file (gzipped CSV built by tools/directory) into {@code directory_facility}. Used by the
 * migrations that load or reload the directory.
 */
final class DirectoryFiles {

    /** Licensed blood centres and government hospitals, National Health Portal open data. */
    static final String GOVERNMENT = "directory/india-facilities.csv.gz";
    /** Other hospitals and blood banks, chiefly private: OpenStreetMap contributors, ODbL. */
    static final String OPENSTREETMAP = "directory/osm-facilities.csv.gz";

    private static final int BATCH = 500;

    private DirectoryFiles() {
    }

    static void load(Connection connection, String resource) throws Exception {
        try (InputStream raw = DirectoryFiles.class.getClassLoader().getResourceAsStream(resource)) {
            if (raw == null) {
                throw new IllegalStateException("Missing " + resource);
            }
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8));
            List<String> header = parse(in.readLine());
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into directory_facility (id, kind, name, category, state, district, city, address, "
                            + "pincode, latitude, longitude, search_text) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                int pending = 0;
                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    List<String> f = parse(line);
                    String name = f.get(header.indexOf("name"));
                    String city = f.get(header.indexOf("city"));
                    String district = f.get(header.indexOf("district"));
                    String state = f.get(header.indexOf("state"));
                    insert.setString(1, f.get(header.indexOf("id")));
                    insert.setString(2, f.get(header.indexOf("kind")));
                    insert.setString(3, name);
                    insert.setString(4, f.get(header.indexOf("category")));
                    insert.setString(5, state);
                    insert.setString(6, district);
                    insert.setString(7, city);
                    insert.setString(8, f.get(header.indexOf("address")));
                    insert.setString(9, f.get(header.indexOf("pincode")));
                    insert.setDouble(10, Double.parseDouble(f.get(header.indexOf("latitude"))));
                    insert.setDouble(11, Double.parseDouble(f.get(header.indexOf("longitude"))));
                    insert.setString(12, searchText(name, city, district, state));
                    insert.addBatch();
                    if (++pending == BATCH) {
                        insert.executeBatch();
                        pending = 0;
                    }
                }
                if (pending > 0) {
                    insert.executeBatch();
                }
            }
        }
    }

    /** Lower-case text that a search matches against: name and place. */
    static String searchText(String name, String city, String district, String state) {
        String text = String.join(" ", name, city, district, state).toLowerCase(Locale.ROOT);
        return text.length() > 600 ? text.substring(0, 600) : text;
    }

    /** One CSV line (RFC 4180: fields may be quoted, with "" for a quote). The directory has no line breaks in fields. */
    static List<String> parse(String line) throws IOException {
        if (line == null) {
            throw new IOException("Empty directory file");
        }
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }
}
