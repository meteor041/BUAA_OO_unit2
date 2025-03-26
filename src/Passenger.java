import com.oocourse.elevator1.PersonRequest;

public class Passenger {
    private final int enterTime;
    private final PersonRequest request;

    public Passenger(int enterTime, PersonRequest request) {
        this.enterTime = enterTime;
        this.request = request;
    }

    public int getEnterTime() {
        return enterTime;
    }

    public PersonRequest getRequest() {
        return request;
    }
}
