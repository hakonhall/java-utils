package no.ion.utils.concurrent;

import java.util.HashMap;
import java.util.Map;

public class ThreadPool implements AutoCloseable {
    private final String name;
    private final SharedData sharedData = new SharedData();
    private final Map<Integer, Worker> workers = new HashMap<>();

    public static ThreadPool create(String name, int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("There must be at least 1 thread in the pool");
        }

        return new ThreadPool(name, threads);
    }

    private ThreadPool(String name, int threads) {
        this.name = name;

        for (int thread_id = 0; thread_id < threads; ++thread_id) {
            var worker = new Worker(thread_id, sharedData);
            workers.put(thread_id, worker);

            var thread = new Thread(worker::main, name + "-worker-" + thread_id);
            thread.setDaemon(false);
            thread.start();
        }
    }

    public void doAsync(Task task) {
        sharedData.addTask(task);
    }

    /**
     * Disallow scheduling more tasks, wait until all threads have completed their current task (if any),
     * and force each thread to exit.
     */
    @Override
    public void close() {
        sharedData.cancel();
        workers.values().forEach(Worker::awaitStopped);
    }
}
