package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Loaded the first directory of Indian facilities. Superseded by {@link V12__reload_facility_directory}, which loads
 * the cleaned government data together with OpenStreetMap, so on a new database this does nothing.
 */
public class V9__load_facility_directory extends BaseJavaMigration {

    @Override
    public void migrate(Context context) {
        // Nothing: V12 loads the directory.
    }
}
