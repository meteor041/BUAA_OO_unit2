package utils;

public class TimeFixer {
    private long stop;

    public TimeFixer() {
        stop = 0;
    }

    public void init() {
        stop = System.currentTimeMillis();
    }

    public long archive() {
        long currentTime = System.currentTimeMillis();
        return currentTime - stop;
    }
}
