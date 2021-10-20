package no.ion.utils.concurrent;

public interface Task {
    String name();

    interface Params {}
    void go(Params context);
}
