package no.ion.utils.concurrent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ThreadPoolTest {
    @Test
    void test() {
        try (var threadPool = ThreadPool.create("threadPool", 1)) {

            var latch = new CountDownLatch(1);

            var task = new Task() {
                @Override
                public String name() {
                    return "task";
                }

                @Override
                public void go(Params context) {
                    latch.countDown();
                }
            };

            threadPool.doAsync(task);

            try { latch.await(); } catch (InterruptedException ignored) { }
        }
    }

    @Test
    void multiThreadedTest() {
        try (var threadPool = ThreadPool.create("threadPool", 2)) {

            TestTask task0 = new TestTask("0");
            TestTask task1 = new TestTask("1", () -> threadPool.doAsync(task0));
            TestTask task2 = new TestTask("2");

            threadPool.doAsync(task1);
            task1.waitForRun();
            task0.waitForRun();

            // This one will never be scheduled with 2 threads
            threadPool.doAsync(task2);

            // This is flaky, but it's the best we can do
            try { Thread.sleep(10); } catch (InterruptedException ignore) {}
            assertFalse(task2.hasStarted());

            task0.close();
            // Now task2 will be scheduled
            task2.waitForRun();

            task2.close();
            task1.close();
        }
    }
}