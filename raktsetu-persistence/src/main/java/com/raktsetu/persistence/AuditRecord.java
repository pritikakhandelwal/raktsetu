package com.raktsetu.persistence;

import com.raktsetu.domain.model.AuditEvent;

/**
 * One line of the audit log.
 *
 * @param sequence position in the log, starting at 1
 * @param event    what happened
 * @param prevHash hash of the previous entry (64 zeros for the first)
 * @param hash     SHA-256 of this entry's fields including {@code prevHash}
 */
public record AuditRecord(long sequence, AuditEvent event, String prevHash, String hash) {
}
