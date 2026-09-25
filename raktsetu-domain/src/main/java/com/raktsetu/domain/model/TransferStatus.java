package com.raktsetu.domain.model;

/** Lifecycle of a recommended transfer. Only staff approval moves stock. */
public enum TransferStatus {
    PROPOSED,
    COMPLETED,
    REJECTED
}
