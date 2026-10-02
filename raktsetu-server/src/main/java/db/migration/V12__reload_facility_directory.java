package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * Loads the directory of Indian facilities afresh: the government's licensed blood centres and hospitals (with
 * garbled characters in the source repaired) and, from OpenStreetMap, the hospitals and blood banks the government
 * data lacks, chiefly private ones. Facilities and requests that refer to directory ids keep working, because the
 * government entries keep their ids.
 */
public class V12__reload_facility_directory extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement clear = context.getConnection().createStatement()) {
            clear.executeUpdate("delete from directory_facility");
        }
        DirectoryFiles.load(context.getConnection(), DirectoryFiles.GOVERNMENT);
        DirectoryFiles.load(context.getConnection(), DirectoryFiles.OPENSTREETMAP);
    }
}
