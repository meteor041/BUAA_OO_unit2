import com.oocourse.elevator1.PersonRequest;

public class Passenger {
    private final long enterTime;
    private final PersonRequest request;

    public Passenger(PersonRequest request) {
        this.enterTime = System.currentTimeMillis();
        this.request = request;
    }

    public long getEnterTime() {
        return enterTime;
    }

    public PersonRequest getRequest() {
        return request;
    }
}
