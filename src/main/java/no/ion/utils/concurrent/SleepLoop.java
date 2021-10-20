package no.ion.utils.concurrent;

import java.time.Duration;
import java.util.function.Supplier;

public interface SleepLoop<S> {
    /**
     * A wait-loop is used to put the current thread to sleep until the {@link Synchronizer monitor guarded data} satisfies
     * a {@link Condition}.
     *
     * @param condition A predicate on the instance the monitor gives exclusive access to.  The condition may
     *                  be evaluated any number of times, by any thread having the exclusive access, and at any
     *                  time while the {@link #sleep()} is running.  If the condition is evaluated to false by
     *                  one thread having the exclusive access If the condition is evaluated to true in the
     *                  {@link #sleep()} thread, it is expected to be evaluated to true in another thread just prior
     *                  and unless some other thread gets exclusive access to the data.
     * @param callback
     * @return
     */
    SleepLoop<S> or(Condition condition, Supplier<S> callback);

    /**
     *
     * @param timeout
     * @param callback
     * @return
     */
    SleepLoop<S> or(Duration timeout, Supplier<S> callback);

    S sleep();
}
