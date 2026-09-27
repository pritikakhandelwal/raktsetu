package com.raktsetu.server;

import com.raktsetu.server.db.AuditRepository;
import com.raktsetu.server.db.AuditRepository.ChainPoint;
import com.raktsetu.server.db.AuditRepository.Checked;
import com.raktsetu.server.db.AuditRepository.Verification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps track of whether the audit log is intact without re-hashing all of it on every page load.
 *
 * <p>{@link #current()} checks only the entries written since the last check, continuing the chain from the last
 * verified hash, so its cost does not grow as the log grows. Every five minutes {@link #full()} re-checks the whole
 * chain, which also catches edits to old entries. Once tampering is found it is reported until a full check shows
 * the chain intact again.
 */
@Component
public class AuditIntegrity {

    private final AuditRepository audit;
    private ChainPoint verifiedTo = ChainPoint.START;
    private Verification last;

    public AuditIntegrity(AuditRepository audit) {
        this.audit = audit;
    }

    /** The chain's state, checking only new entries. */
    public synchronized Verification current() {
        if (last != null && !last.intact()) {
            return last;
        }
        Checked checked = audit.verifyAfter(verifiedTo);
        verifiedTo = checked.end();
        last = checked.verification();
        return last;
    }

    /** Re-checks the whole chain from the first entry. */
    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public synchronized Verification full() {
        Checked checked = audit.verifyAfter(ChainPoint.START);
        verifiedTo = checked.end();
        last = checked.verification();
        return last;
    }
}
