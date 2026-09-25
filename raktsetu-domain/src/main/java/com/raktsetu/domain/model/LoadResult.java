package com.raktsetu.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of loading data.
 *
 * @param data     what was loaded successfully
 * @param problems one message per skipped row, with file name and line number
 */
public record LoadResult(NetworkData data, List<String> problems) {

    public LoadResult {
        Objects.requireNonNull(data, "data");
        problems = List.copyOf(problems);
    }
}
