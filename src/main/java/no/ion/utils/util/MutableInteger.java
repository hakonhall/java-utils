package no.ion.utils.util;

/** A non-thread-safe mutable int, useful for e.g. lambdas. */
public class MutableInteger {
    private int value;

    public MutableInteger(int initialValue) {
        this.value = initialValue;
    }

    public int toInt() { return value; }

    public int set(int newValue) { value = newValue; return value; }
    public int increment() { return ++value; }
    public int decrement() { return --value; }
    public int add(int delta) { value += delta; return value; }
    public int subtract(int delta) { value -= delta; return value; }
    public int multiply(int multiplier) { value *= multiplier; return value; }
    public int divide(int divisor) { value /= divisor; return value; }
}
