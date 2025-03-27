import java.util.Comparator;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;


public class Scheduler {
    private static final Scheduler instance = new Scheduler();
    private static final int NUM_ELEVATORS = 6;
    private static final int NUM_FLOORS = 11;
    private final CopyOnWriteArrayList<Elevator> elevators;
    // 等待中的乘客列表
    private final CopyOnWriteArrayList<TreeSet<Passenger>> waitingLine;

    private Scheduler() {
        elevators = new CopyOnWriteArrayList<>();
        waitingLine = new CopyOnWriteArrayList<>();
        for (int i = 0; i < NUM_ELEVATORS; i++) {
            // 使用 Comparator.comparingInt(Passenger::getPriority).reversed() 使优先级高的排前面
            waitingLine.add(new TreeSet<>(
                    Comparator.comparingInt((Passenger p) -> p.getRequest().getPriority())
                    .reversed().thenComparingLong(Passenger::getEnterTime)
                            .thenComparingInt((Passenger p) -> p.getRequest().getPersonId())));
        }
        for (int i = 1; i <= NUM_ELEVATORS; i++) {
            Elevator elevator = new Elevator(i);
            elevators.add(elevator);
            new Thread(elevator).start(); // 启动电梯线程
        }
    }

    /**
     * 获取调度器的单例实例
     *
     * @return Scheduler的单例实例
     */
    public static Scheduler getInstance() {
        return instance;
    }

    /**
     *  处理新的请求
     * @param passenger 新的请求
     */
    public static void newRequest(Passenger passenger) {
        getInstance().recieveRequest(passenger);
    }

    /**
     * 处理新的乘客请求，分配电梯并加入等待队列
     *
     * @param passenger 乘客请求对象，包含出发楼层、目标楼层等信息
     */
    public void recieveRequest(Passenger passenger) {
        int elevatorId = passenger.getRequest().getElevatorId();
        synchronized (getInstance().getWaitingLine(elevatorId)) {
            getInstance().getWaitingLine(elevatorId).add(passenger);
//            System.out.println("recieve request " + passenger.getRequest().getPersonId()
//                    + getInstance().getWaitingLine(elevatorId));
            getInstance().getWaitingLine(elevatorId).notify();
        }
    }



    /**
     * 接收InputThread发送的停止输入信号,将其转发给每个电梯线程
     */
    public void stopAllElevators() {
        for (Elevator elevator : elevators) {
            elevator.setShouldTerminate(true);
        }
    }

    public TreeSet<Passenger> getWaitingLine(int elevatorId) {
        return waitingLine.get(elevatorId - 1);
    }
}
