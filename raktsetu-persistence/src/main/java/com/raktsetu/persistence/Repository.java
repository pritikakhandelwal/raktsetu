package com.raktsetu.persistence;

import com.raktsetu.domain.interfaces.Identifiable;

import java.util.List;
import java.util.Optional;

/**
 * Generic storage for anything with an ID.
 *
 * @param <T> the stored type; the bound guarantees every item has an ID to key on
 */
public interface Repository<T extends Identifiable> {

    /** Adds or replaces the item with the same ID. */
    void save(T item);

    Optional<T> findById(String id);

    /** All items, in insertion order. */
    List<T> findAll();

    /** Returns true if an item was removed. */
    boolean delete(String id);
}
