package no.ion.utils.concurrent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SynchronizerTest {
    private final Synchronizer<GuardedData> synchronizer = new Synchronizer<>(GuardedData.class);

    @Test
    void testSingleThreaded() {
        var result = synchronizer.synchronize((data, waiter) -> {
            data.counter += 1;
            return data.counter;
        });
        assertEquals(1, result);
    }

    @Test
    void testBarrier() {
        Thread consumerThread = new Thread(() -> {
            synchronizer.synchronize((data, sleeper) -> {
                data.consumerStarted = true;
                sleeper.until(() -> data.mainThreadStarted).sleep();
            });
        });

        consumerThread.start();

        synchronizer.synchronize((data, sleeper) -> {
            data.mainThreadStarted = true;
            sleeper.until(() -> data.consumerStarted).sleep();
        });
    }

    @Test
    void testCountingTo10() {
        Thread consumerThread = new Thread(() -> {
            synchronizer.synchronize((data, sleeper) -> {
                while (sleeper
                        .until(() -> data.counter >= 10, () -> false)
                        .or(() -> data.counter % 2 == 0, () -> {
                            ++data.counter;
                            return true;
                        })
                        .sleep()) {
                    // nothing
                }
            });
        });

        consumerThread.start();

        synchronizer.synchronize((data, sleeper) -> {
            while (sleeper
                    .until(() -> data.counter >= 10, () -> false)
                    .or(() -> data.counter % 2 == 1, () -> {
                        ++data.counter;
                        return true;
                    })
                    .sleep()) {
                // nothing
            }
        });

        int counter = synchronizer.synchronize((data) -> data.counter);
        Assertions.assertEquals(10, counter);
    }

    public static class GuardedData {
        public int counter = 0;

        public boolean consumerStarted = false;
        public boolean mainThreadStarted = false;
    }
}