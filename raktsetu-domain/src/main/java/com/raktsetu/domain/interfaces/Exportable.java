package com.raktsetu.domain.interfaces;

import java.util.List;

/** Something that can be written as one row of a CSV report. */
public interface Exportable {

    /** Cell values in the same order as the report's header. */
    List<String> toCsvRow();
}
