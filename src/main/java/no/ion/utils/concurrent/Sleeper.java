package no.ion.utils.concurrent;

import java.time.Duration;
import java.util.function.Supplier;

public interface Sleeper {
    /**
     * When {@link SleepLoop#sleep()} is invoked on the returned object, the callback will be invoked if condition
     * is true.
     *
     * @param condition
     * @param callback
     * @param <S>
     * @return
     */
    <S> SleepLoop<S> until(Condition condition, Supplier<S> callback);

    <S> SleepLoop<S> until(Duration timeout, Supplier<S> callback);

    default SleepLoopVoid until(Condition condition) { return until(condition, () -> {}); }
    SleepLoopVoid until(Condition condition, Runnable callback);
    default SleepLoopVoid until(Duration timeout) { return until(timeout, () -> {}); }
    SleepLoopVoid until(Duration timeout, Runnable callback);
}
