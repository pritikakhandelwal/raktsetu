package com.raktsetu.server;

import com.raktsetu.domain.exception.RaktSetuException;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Guards the shared in-memory network with a readers-writer lock: any number of users may read at the same time,
 * and a change waits until it has the network to itself. Most traffic is reading (the overview, stock, request
 * lists), so reads no longer queue behind each other as they did with one {@code synchronized} lock.
 *
 * <p>The lock is fair, so a steady stream of readers cannot starve a waiting change. A thread holding the write
 * lock may also read, but a reader must never ask for the write lock (Java's lock cannot be upgraded): anything
 * that might change the network, such as the daily roll-over, runs before a read begins.
 */
public final class NetworkLock {

    /** Work done under the lock that may be refused with a {@link RaktSetuException}. */
    @FunctionalInterface
    public interface Action<T> {
        T run() throws RaktSetuException;
    }

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    public <T> T read(Action<T> action) throws RaktSetuException {
        lock.readLock().lock();
        try {
            return action.run();
        } finally {
            lock.readLock().unlock();
        }
    }

    public <T> T write(Action<T> action) throws RaktSetuException {
        lock.writeLock().lock();
        try {
            return action.run();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** As {@link #read}, for work that cannot be refused. */
    public <T> T readSafely(Action<T> action) {
        return unchecked(() -> read(action));
    }

    /** As {@link #write}, for work that cannot be refused. */
    public <T> T writeSafely(Action<T> action) {
        return unchecked(() -> write(action));
    }

    boolean holdsWrite() {
        return lock.isWriteLockedByCurrentThread();
    }

    private static <T> T unchecked(Action<T> action) {
        try {
            return action.run();
        } catch (RaktSetuException e) {
            throw new IllegalStateException(e);
        }
    }
}
