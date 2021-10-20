package no.ion.utils.concurrent;

import java.time.Duration;

public interface SleepLoopVoid {
    SleepLoopVoid or(Condition condition, Runnable callback);
    SleepLoopVoid or(Duration timeout, Runnable callback);
    void sleep();
}
