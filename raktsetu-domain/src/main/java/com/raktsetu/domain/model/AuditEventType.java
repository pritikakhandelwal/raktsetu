package com.raktsetu.domain.model;

/** What happened to the units in an audit entry. */
public enum AuditEventType {
    COLLECTED,
    ISSUED,
    TRANSFERRED,
    DISCARDED,
    /** Put back on the shelf, for example when a request is cancelled before dispatch. */
    RETURNED
}
