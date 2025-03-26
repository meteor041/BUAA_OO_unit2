package utils;

public class TimeFixer {
    private long stop;

    public void init() {
        stop = System.currentTimeMillis();
    }

    public long archive(){
        long currentTime = System.currentTimeMillis();
        return currentTime - stop;
    }

    public static void main(String[] args) {
        TimeFixer timeFixer = new TimeFixer();
        timeFixer.init();
        for (int i = 0; i < 1000; i++)
            System.out.println("Stop");
        System.out.println(timeFixer.archive());
    }
}
