import com.oocourse.elevator1.ElevatorInput;
import com.oocourse.elevator1.PersonRequest;
import com.oocourse.elevator1.Request;

public class InputThread implements Runnable{
    private static InputThread instance;
    private InputThread() {}
    public static InputThread getInstance() {
        if (instance == null) {
            instance = new InputThread();
        }
        return instance;
    }

    @Override
    public void run() {
        ElevatorInput elevatorInput = new ElevatorInput(System.in);
        while (true) {
            Request request = elevatorInput.nextRequest();
            if (request == null) {
                break;
            } else {
                // a new valid request
                if (request instanceof PersonRequest) {
                    PersonRequest personRequest = (PersonRequest) request;
                    Scheduler.newRequest(personRequest);
                }
            }
        }
        try {
            elevatorInput.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            Thread.sleep(100);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Scheduler.getInstance().stopAllElevators();

    }
}
