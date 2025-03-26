import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

import static utils.FloorConverter.floorString2Int;

import com.oocourse.elevator1.PersonRequest;

public class Scheduler {
    private static final Scheduler instance = new Scheduler();
    private static final int NUM_ELEVATORS = 6;
    private static final int NUM_FLOORS = 11;
    private final CopyOnWriteArrayList<Elevator> elevators;
    // 等待中的乘客列表
    public final List<TreeSet<Passenger>> waitingLine;

    private Scheduler() {
        elevators = new CopyOnWriteArrayList<>();
        waitingLine = new ArrayList<>(NUM_ELEVATORS);
        for (int i = 0; i < NUM_FLOORS; i++) {
            // 使用 Comparator.comparingInt(Passenger::getPriority).reversed() 使优先级高的排前面
            waitingLine.add(new TreeSet<>(Comparator.comparingInt((Passenger p) -> p.getRequest().getPriority())
                    .reversed().thenComparingInt(Passenger::getEnterTime)));
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
//        System.out.println("-----------ID:" + Thread.currentThread().getId());
        getInstance().recieveRequest(passenger);
    }

    /**
     * 处理新的乘客请求，分配电梯并加入等待队列
     *
     * @param passenger 乘客请求对象，包含出发楼层、目标楼层等信息
     */
    public void recieveRequest(Passenger passenger) {
//        TimableOutput.println("Recieving request: " + request);
        int elevatorId = passenger.getRequest().getElevatorId();
        synchronized (getInstance().waitingLine.get(elevatorId-1)) {
//            System.out.println("Waiting for elevator: " + elevatorId);
            waitingLine.get(elevatorId-1).add(passenger);
            getInstance().waitingLine.get(elevatorId-1).notify();
        }
    }

    /**
     * 判断电梯是否可以在当前楼层接载乘客
     *
     * @param elevator      电梯对象
     * @param floor         当前楼层
     * @param leaveElevator 是否有乘客要离开电梯
     * @return true表示可以接载乘客，false表示不能接载
     */
    public boolean canEnter(Elevator elevator, int floor, boolean leaveElevator) {
        // 如果电梯满载且无人在此楼层下电梯,
        // 则电梯一定无法新加乘客
        if (elevator.full() && !leaveElevator) {
            return false;
        }
        int id = elevator.getId();
        for (Passenger passenger : waitingLine.get(id-1)) {
            if (floorString2Int(passenger.getRequest().getFromFloor()) == floor) {
                return true;
            }
        }
        return false;
    }

    /**
     * 接收InputThread发送的停止输入信号,将其转发给每个电梯线程
     */
    public void stopAllElevators() {
        for (Elevator elevator : elevators) {
            elevator.setShouldTerminate(true);
        }
    }
}
