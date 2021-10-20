package no.ion.utils.concurrent;

public class TestTask implements Task, AutoCloseable {
    private final String name;
    private final Runnable runnable;

    private final Object monitor = new Object();
    private State state = State.CREATED;
    private boolean cancelled = false;

    public enum State { CREATED, STARTED, POST_RUN, DONE }

    public TestTask(String name) { this(name, () -> {}); }

    public TestTask(String name, Runnable runnable) {
        this.name = name;
        this.runnable = runnable;
    }

    public boolean hasStarted() {
        synchronized (monitor) {
            return state != State.CREATED;
        }
    }

    @Override public String name() { return name; }

    @Override
    public void go(Params context) {
        synchronized (monitor) {
            state = State.STARTED;
            monitor.notifyAll();
        }

        runnable.run();

        synchronized (monitor) {
            state = State.POST_RUN;

            while (!cancelled) {
                try { monitor.wait(); } catch (InterruptedException ignored) {}
            }

            state = State.DONE;
            monitor.notifyAll();
        }
    }

    public void waitForRun() {
        synchronized (monitor) {
            switch (state) {
                case CREATED:
                case STARTED:
                    try { monitor.wait(); } catch (InterruptedException ignored) {}
                    break;
                case POST_RUN:
                    return;
                case DONE:
                    throw new IllegalStateException("Will never reach RUNNING: is DONE");
            }
        }
    }

    @Override
    public void close() {
        synchronized (monitor) {
            cancelled = true;
            monitor.notifyAll();

            while (state != State.DONE) {
                try { monitor.wait(); } catch (InterruptedException ignored) {}
            }
        }
    }
}
