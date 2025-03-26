import com.oocourse.elevator1.*;

public class MainClass {
    public static void main(String[] args) {
        TimableOutput.initStartTimestamp();
        new Thread(InputThread.getInstance()).start();
    }
}
