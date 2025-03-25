import com.oocourse.elevator1.*;

import java.io.IOException;

public class MainClass {
    public static void main(String[] args) {
        TimableOutput.initStartTimestamp();
        new Thread(Input.getInstance()).start();
    }
}
