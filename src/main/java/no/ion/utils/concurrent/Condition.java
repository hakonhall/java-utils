package no.ion.utils.concurrent;

@FunctionalInterface
public interface Condition {
    boolean evaluate();
}
