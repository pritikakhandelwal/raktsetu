package com.raktsetu.domain.interfaces;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.model.LoadResult;
import com.raktsetu.domain.model.NetworkData;

/**
 * Loads and saves the state of the network.
 *
 * <p>The current implementation reads CSV files; an e-Raktkosh-backed source could be added later without changing the
 * engine.
 */
public interface StockDataSource {

    /**
     * Loads everything. Malformed rows are skipped and reported in {@link LoadResult#problems()} rather than failing
     * the whole load.
     *
     * @throws RaktSetuException if the data cannot be read at all
     */
    LoadResult load() throws RaktSetuException;

    void save(NetworkData data) throws RaktSetuException;
}
