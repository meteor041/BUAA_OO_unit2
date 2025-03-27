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

    public String toString() {
        return String.format("%d-PRI-%d-FROM-%s-TO-%s-BY-%d",
                this.request.getPersonId(),
                this.request.getPriority(),
                this.request.getFromFloor(),
                this.request.getToFloor(),
                this.request.getElevatorId());
    }

    @Override
    public int hashCode() {
        return request.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Passenger && request.equals(((Passenger) obj).request);
    }
}
