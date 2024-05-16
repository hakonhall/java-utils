package no.ion.utils.exceptions;

@FunctionalInterface
public interface UncheckedAutoCloseable extends AutoCloseable {
    static UncheckedAutoCloseable of(Runnable runnable) { return runnable::run; }

    @Override
    void close();
}
