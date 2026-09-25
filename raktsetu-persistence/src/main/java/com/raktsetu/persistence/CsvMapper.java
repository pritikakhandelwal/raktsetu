package com.raktsetu.persistence;

import com.raktsetu.domain.exception.InvalidDataException;

import java.util.List;

/**
 * Converts between one CSV row and one object of type {@code T}.
 *
 * @param <T> the type stored in the file
 */
public interface CsvMapper<T> {

    /** Column names, in the order {@link #toRow} writes them. */
    List<String> header();

    T fromRow(CsvRow row) throws InvalidDataException;

    List<String> toRow(T value);
}
