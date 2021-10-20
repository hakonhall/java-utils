package no.ion.utils.concurrent;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A Synchronizer encapsulates thread-safe access to an instance.
 *
 * <p>A Synchronizer is constructed with the instance it guards ("data"):  The data MUST NOT be accessed, directly or
 * indirectly, by any thread except by the thread executing the callback passed to one of the {@code #synchronize()}
 * methods.  The simplest method {@link #synchronize(Consumer)} runs the consumer as-if in a {@code synchronized} block.
 * Overloaded methods provide additional use-cases:</p>
 *
 * <ul>
 *     <li>The callback may return a value, which will be returned from the {@code synchronize()} method.</li>
 *     <li>The callback may want to relinquish the lock and put the current thread to sleep until a condition is
 *     satisfied.  This functionality is provided by the {@link Sleeper} parameter.</li>
 *     <li>The callback may want to try to acquire the lock non-blocking, or with a timeout.</li>
 * </ul>
 */
public class Synchronizer<T> {
    private final Lock lock = new ReentrantLock(false);
    private final T data;
    private final ArrayDeque<Wait> waits = new ArrayDeque<>();

    public Synchronizer(Class<T> classInstance) {
        this(invokeDefaultConstructor(classInstance));
    }

    public Synchronizer(Supplier<T> factory) {
        this(factory.get());
    }

    public Synchronizer(T instanceRequiringExclusiveAccess) {
        this.data = instanceRequiringExclusiveAccess;
    }

    /**
     * Invoke the callback in this thread with the guarded instance passed to the consumer.  The callback has exclusive
     * access to the instance as-if in a {@code synchronized} block.
     *
     * @param callback The callback to invoke exactly once, once an exclusive lock has been acquired.
     */
    public void synchronize(Consumer<T> callback) {
        synchronize((data, sleeper) -> {
            callback.accept(data);
            return null;
        });
    }

    public <R> R synchronize(Function<T, R> callback) {
        return synchronize((data, sleeper) -> {
            return callback.apply(data);
        });
    }

    /** Returns false on timeout. */
    public boolean synchronize(Consumer<T> callback, Duration acquireTimeout) {
        Objects.requireNonNull(acquireTimeout);
        return synchronizeImpl((data, sleeper) -> {
            callback.accept(data);
            return null;
        }, acquireTimeout) != null;
    }

    /** Returns null on timeout. */
    public <R> Optional<R> synchronize(Function<T, R> callback, Duration acquireTimeout) {
        Objects.requireNonNull(acquireTimeout);
        return synchronizeImpl((data, sleeper) -> {
            return callback.apply(data);
        }, acquireTimeout);
    }

    public void synchronize(BiConsumer<T, Sleeper> callback) {
        synchronizeImpl((data, sleeper) -> {
            callback.accept(data, sleeper);
            return null;
        }, null);
    }

    public <R> R synchronize(BiFunction<T, Sleeper, R> callback) {
        return synchronizeImpl(callback, null).orElseThrow();
    }

    /** Returns false on timeout. */
    public boolean synchronize(BiConsumer<T, Sleeper> callback, Duration acquireTimeout) {
        Objects.requireNonNull(acquireTimeout);
        return synchronizeImpl((data, sleeper) -> {
            callback.accept(data, sleeper);
            return null;
        }, acquireTimeout) != null;
    }

    /**
     * Try to acquire the lock within the given timeout. If the timeout is zero or negative, a non-blocking try is
     * attempted.  If the lock acquisition failed, null is returned.  Otherwise, the callback is invoked, and its
     * return value is wrapped in {@link Optional#ofNullable(Object)}.  The callback is passed the instance it has
     * thread-safe and exclusive access to, and a {@link Sleeper} object that can be used to relinquish the lock
     * and sleep until a condition on the guarded instance is satisfied.
     *
     * @param callback       The callback to invoke once if the lock was successfully acquired.
     * @param acquireTimeout Zero or less duration if a non-blocking attempt at acquiring the lock should be done,
     *                       or otherwise the minimum duration to wait for the acquisition of the lock before giving up.
     * @param <R>            The type returned by the callback.
     * @return null if the lock was not acquired before the timeout, or {@link Optional#ofNullable(Object)} of the
     * object returned by the callback.
     */
    public <R> Optional<R> synchronize(BiFunction<T, Sleeper, R> callback, Duration acquireTimeout) {
        Objects.requireNonNull(acquireTimeout);
        return synchronizeImpl(callback, acquireTimeout);
    }

    /**
     * Invoke the callback at most once, and if and only if the lock was acquired within the acquisition timeout.
     *
     * <p>The invoking thread has exclusive access to the instance of type {@code T} guarded by this synchronizer,
     * for the duration of the callback.  The callback has the option to relinquish the exclusive access temporarily
     * and atomically until a condition is satisfied, see {@link Sleeper}.</p>
     *
     * @param callback             Invoked once if the lock was successfully acquired
     * @param acquireTimeoutOrNull Duration to wait for the acquisition of the lock before timing out, or wait without
     *                             a timeout if null.
     * @param <R>                  The type returned by the callback.
     * @return null if the lock was not acquired before the timeout, or otherwise the instance returned from the callback
     * wrapped in an {@link Optional#ofNullable(Object)}.
     */
    private <R> Optional<R> synchronizeImpl(BiFunction<T, Sleeper, R> callback, Duration acquireTimeoutOrNull) {
        if (!acquireLock(acquireTimeoutOrNull))
            return null;

        try {
            return Optional.ofNullable(callback.apply(data, newSleeper()));

        } finally {
            // TODO: Only signal if actually releasing the reentrant lock
            signalIfNecessary(null);
            lock.unlock();
        }
    }

    private boolean acquireLock(Duration acquireTimeoutOrNull) {
        if (acquireTimeoutOrNull == null) {
            lock.lock();
            return true;
        }

        long nanosLeft = acquireTimeoutOrNull.toNanos();
        if (nanosLeft <= 0) {
            return lock.tryLock();
        }

        long deadline = System.nanoTime() + nanosLeft;
        do {
            try {
                return lock.tryLock(nanosLeft, TimeUnit.NANOSECONDS);
            } catch (InterruptedException ignored) {
                nanosLeft = deadline - System.nanoTime();
                if (nanosLeft <= 0) {
                    return lock.tryLock();
                }
            }
        } while (true);
    }

    private static <U> U invokeDefaultConstructor(Class<U> classInstance) {
        try {
            return classInstance.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Sleeper newSleeper() {
        return new Sleeper() {
            @Override
            public SleepLoopVoid until(Condition condition, Runnable callback) {
                return newSleepLoopVoid(until(condition, () -> {
                    callback.run();
                    return null;
                }));
            }

            @Override
            public SleepLoopVoid until(Duration timeout, Runnable callback) {
                return newSleepLoopVoid(until(timeout, () -> {
                    callback.run();
                    return null;
                }));
            }

            @Override
            public <S> SleepLoop<S> until(Condition condition, Supplier<S> callback) {
                verifyLockIsHeld();
                Objects.requireNonNull(condition);
                Objects.requireNonNull(callback);

                var conditionCallbacks = new ArrayList<ConditionCallback<S>>();
                conditionCallbacks.add(new ConditionCallback<>(condition, callback));
                return newSleepLoop(lock.newCondition(), conditionCallbacks, 0L, null);
            }

            @Override
            public <S> SleepLoop<S> until(Duration timeout, Supplier<S> callback) {
                verifyLockIsHeld();
                Objects.requireNonNull(callback);

                long deadlineNanos = System.nanoTime() + timeout.toNanos();
                return newSleepLoop(lock.newCondition(), new ArrayList<>(), deadlineNanos, callback);
            }
        };
    }

    private SleepLoopVoid newSleepLoopVoid(SleepLoop<Void> toWrap) {
        return new SleepLoopVoid() {
            @Override
            public SleepLoopVoid or(Condition condition, Runnable callback) {
                SleepLoop<Void> inner = toWrap.or(condition, () -> {
                    callback.run();
                    return null;
                });

                return newSleepLoopVoid(inner);
            }

            @Override
            public SleepLoopVoid or(Duration timeout, Runnable callback) {
                SleepLoop<Void> inner = toWrap.or(timeout, () -> {
                    callback.run();
                    return null;
                });

                return newSleepLoopVoid(inner);
            }

            @Override
            public void sleep() {
                toWrap.sleep();
            }
        };
    }

    private <S> SleepLoop<S> newSleepLoop(java.util.concurrent.locks.Condition conditionVariable,
                                          List<ConditionCallback<S>> conditionCallbacks,
                                          long timeoutDeadlineNanos,
                                          Supplier<S> timeoutCallback) {
        return new SleepLoop<S>() {
            @Override
            public SleepLoop<S> or(Condition condition, Supplier<S> callback) {
                verifyLockIsHeld();
                Objects.requireNonNull(condition);
                Objects.requireNonNull(callback);

                conditionCallbacks.add(new ConditionCallback<>(condition, callback));
                return newSleepLoop(conditionVariable, conditionCallbacks, timeoutDeadlineNanos, timeoutCallback);
            }

            @Override
            public SleepLoop<S> or(Duration timeout, Supplier<S> callback) {
                verifyLockIsHeld();
                Objects.requireNonNull(callback);
                if (timeoutCallback != null) {
                    throw new IllegalArgumentException("A timeout has already been specified");
                }

                long deadlineNanos = System.nanoTime() + timeout.toNanos();
                return newSleepLoop(conditionVariable, conditionCallbacks, deadlineNanos, callback);
            }

            @Override
            public S sleep() {
                verifyLockIsHeld();

                Wait wait = null;
                do {
                    for (var conditionCallback : conditionCallbacks) {
                        if (conditionCallback.condition().evaluate()) {
                            if (wait != null) waits.removeLastOccurrence(wait);
                            return conditionCallback.callback().get();
                        }
                    }

                    long timeoutNanos = 0;  // initialization is unnecessary, but compiler requires it.
                    if (timeoutCallback != null) {
                        timeoutNanos = timeoutDeadlineNanos - System.nanoTime();
                        if (timeoutNanos <= 0L) {
                            if (wait != null) waits.removeLastOccurrence(wait);
                            return timeoutCallback.get();
                        }
                    }

                    signalIfNecessary(wait);

                    if (wait == null) {
                        wait = new Wait(conditionVariable, () -> {
                            for (var conditionCallback : conditionCallbacks) {
                                if (conditionCallback.condition().evaluate()) {
                                    return true;
                                }
                            }

                            return false;
                        });

                        waits.addLast(wait);
                    }

                    if (timeoutCallback == null) {
                        try { conditionVariable.await(); } catch (InterruptedException ignored) { }
                    } else {
                        long left;
                        try {
                            // timeoutNanos is guaranteed to be > 0L
                            left = conditionVariable.awaitNanos(timeoutNanos);
                        } catch (InterruptedException ignored) {
                            left = 1L;
                        }

                        if (left <= 0L) {
                            waits.removeLastOccurrence(wait);
                            return timeoutCallback.get();
                        }
                    }
                } while (true);
            }
        };
    }

    private void verifyLockIsHeld() {
        // TODO: Implement
    }

    private void signalIfNecessary(Wait waitToSkip) {
        for (var wait : waits) {
            if (wait != waitToSkip) {
                if (wait.someConditionsAreTrue()) {
                    wait.conditionVariable().signal();
                }
            }
        }
    }
}
