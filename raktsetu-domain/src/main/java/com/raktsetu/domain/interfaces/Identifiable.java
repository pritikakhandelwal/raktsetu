package com.raktsetu.domain.interfaces;

/** Anything with a stable string ID; required by the generic repository. */
public interface Identifiable {

    String getId();
}
