import com.oocourse.elevator1.ElevatorInput;
import com.oocourse.elevator1.PersonRequest;
import com.oocourse.elevator1.Request;

public class Input implements Runnable{
    private static Input instance;
    private Input() {}
    public static Input getInstance() {
        if (instance == null) {
            instance = new Input();
        }
        return instance;
    }

    @Override
    public void run() {
        ElevatorInput elevatorInput = new ElevatorInput(System.in);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
        Scheduler.getInstance().stopAllElevators();

    }
}
