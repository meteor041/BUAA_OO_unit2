import com.oocourse.elevator1.TimableOutput;

public class MainClass {
    public static void main(String[] args) {
        TimableOutput.initStartTimestamp();
        Scheduler.getInstance();
        new Thread(InputThread.getInstance()).start();
    }
}
