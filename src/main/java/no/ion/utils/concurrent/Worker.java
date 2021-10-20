package no.ion.utils.concurrent;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Worker {
    private final Logger logger = Logger.getLogger(Worker.class.getName());
    private final int id;
    private final SharedData sharedData;

    private final Object monitor = new Object();
    private State state = State.CREATED;

    private enum State { CREATED, STARTED, STOPPED }

    Worker(int id, SharedData sharedData) {
        this.id = id;
        this.sharedData = sharedData;
    }

    void main() {
        synchronized (monitor) {
            state = State.STARTED;
        }

        for (;;) {
            Optional<Task> task = sharedData.awaitTaskOrCancellation();
            if (task.isEmpty()) {
                synchronized (monitor) {
                    state = State.STOPPED;
                    monitor.notifyAll();
                }
                return;
            }

            Task.Params params = new TaskParams();
            try {
                task.get().go(params);
            } catch (Throwable t) {
                // No exception should escape from go()
                // Instead of exit, perhaps keep it as a pending exception to be thrown from thread pool API.
                logger.log(Level.WARNING, "Ignoring exception escaped from task " + task.get().name(), t);
            }
        }
    }

    void awaitStopped() {
        synchronized (monitor) {
            while (state != State.STOPPED) {
                try { monitor.wait(); } catch (InterruptedException ignored) {}
            }
        }
    }
}
