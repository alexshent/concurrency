package ua.alexshent.concurrency;

public class MutatorInteger {
    private volatile int value;

    public int getValue() {
        return value;
    }

    public synchronized void setValue(int value) {
        this.value = value;
    }

    public synchronized int addAndGet(int summand) {
        value += summand;
        return value;
    }
}
