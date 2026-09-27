package com.raktsetu.persistence;

import com.raktsetu.domain.model.AuditEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * The canonical form and hash of one audit entry, shared by the file log ({@link AuditLogger}) and the database log in
 * the server, so both produce the same, verifiable chain.
 */
public final class AuditHashing {

    /** The "previous hash" of the first entry. */
    public static final String GENESIS = "0".repeat(64);

    private AuditHashing() {
    }

    /** The fields that are hashed, in order: sequence, event fields, previous hash. */
    public static List<String> fields(long sequence, AuditEvent e, String prevHash) {
        return List.of(String.valueOf(sequence), e.date().toString(), e.type().name(), e.facilityId(),
                e.counterparty(), e.component().name(), e.group().label(), String.join(";", e.unitIds()), e.note(),
                prevHash);
    }

    /** SHA-256 of the fields written as one CSV line. */
    public static String hash(List<String> fields) {
        return sha256(Csv.formatLine(fields));
    }

    public static String hash(long sequence, AuditEvent e, String prevHash) {
        return hash(fields(sequence, e, prevHash));
    }

    static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
