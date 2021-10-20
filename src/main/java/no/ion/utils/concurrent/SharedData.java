package no.ion.utils.concurrent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

class SharedData {
    private final Object monitor = new Object();
    private boolean cancelled = false;
    private final Deque<Task> tasks = new ArrayDeque<>();

    void addTask(Task task) {
        synchronized (monitor) {
            if (cancelled) {
                throw new IllegalStateException("Unable to add task: thread pool is shutting down");
            }

            tasks.addFirst(task);
            // TODO: Problem: here it is correct with notify, below notifyAll. It's not a wait-site-decision!
            monitor.notify();
        }
    }

    void cancel() {
        synchronized (monitor) {
            cancelled = true;
            monitor.notifyAll();
        }
    }

    Optional<Task> awaitTaskOrCancellation() {
        synchronized (monitor) {
            while (true) {
                if (cancelled) {
                    return Optional.empty();
                }

                Task task = tasks.pollLast();
                if (task != null) {
                    return Optional.of(task);
                }

                try { monitor.wait(); } catch (InterruptedException e) { /* ignored */ }
            }
        }
    }
}
