package com.raktsetu.server;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import java.nio.file.Path;

/**
 * Resolves {@code raktsetu.data-dir} to an absolute path and publishes it as {@code raktsetu.data-path}, which the
 * embedded database's address uses. H2 refuses a path that is implicitly relative ({@code raktsetu-data}) and
 * misreads one written as {@code ./C:/...}, so the folder is always handed over absolute: a relative folder is taken
 * from the working directory as before, and an absolute one (or {@code ${user.home}/RaktSetu}) now works too.
 */
public class DataDirectory implements EnvironmentPostProcessor, Ordered {

    static final String DATA_DIR = "raktsetu.data-dir";
    static final String DATA_PATH = "raktsetu.data-path";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(new DataPath(environment));
    }

    /**
     * Works the path out each time it is read, so a data folder set by any later source is still seen. The
     * environment is kept in a field, not as the source object: Spring Boot walks into a source that is itself an
     * environment, and would then loop forever through this one.
     */
    static final class DataPath extends PropertySource<String> {
        private final ConfigurableEnvironment environment;

        DataPath(ConfigurableEnvironment environment) {
            super("raktsetu-data-path", DATA_PATH);
            this.environment = environment;
        }

        @Override
        public Object getProperty(String name) {
            return DATA_PATH.equals(name) ? absolute(environment.getProperty(DATA_DIR, "raktsetu-data")) : null;
        }
    }

    /**
     * The folder as an absolute path with forward slashes, as H2 expects on every platform. A leading {@code ~} is
     * the user's home folder, so the Windows app can keep its data in {@code ~/RaktSetu/data} wherever it is unzipped.
     */
    static String absolute(String dir) {
        String expanded = dir.equals("~") || dir.startsWith("~/") || dir.startsWith("~\\")
                ? System.getProperty("user.home") + dir.substring(1) : dir;
        return Path.of(expanded).toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
