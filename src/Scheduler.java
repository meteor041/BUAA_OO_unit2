import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

import static utils.FloorConverter.floorString2Int;

import com.oocourse.elevator1.PersonRequest;
import com.oocourse.elevator1.Request;

public class Scheduler {
    private static final Scheduler instance = new Scheduler();
    private static final int NUM_ELEVATORS = 6;
    private static final int NUM_FLOORS = 11;
    private final CopyOnWriteArrayList<Elevator> elevators;
    // 等待中的乘客列表
    public final CopyOnWriteArrayList<PersonRequest>[] waitingLine;

    private Scheduler() {
        elevators = new CopyOnWriteArrayList<>();
        waitingLine = new CopyOnWriteArrayList[NUM_ELEVATORS + 1];

        for (int i = 1; i <= NUM_ELEVATORS; i++) {
            Elevator elevator = new Elevator(i);
            elevators.add(elevator);
            new Thread(elevator).start(); // 启动电梯线程
        }
        for (int i = 0; i <= NUM_ELEVATORS; i++) {
            waitingLine[i] = new CopyOnWriteArrayList<>();
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

    public static void newRequest(PersonRequest request) {
        System.out.println("-----------ID:" + Thread.currentThread().getId());
        getInstance().recieveRequest(request);
    }

    /**
     * 处理新的乘客请求，分配电梯并加入等待队列
     *
     * @param request 乘客请求对象，包含出发楼层、目标楼层等信息
     */
    public void recieveRequest(PersonRequest request) {
        System.out.println("Recieving request: " + request);
        int elevatorId = request.getElevatorId();
        synchronized (getInstance().waitingLine[elevatorId]) {
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            System.out.println("Waiting for elevator: " + elevatorId);
            waitingLine[elevatorId].add(request);
            getInstance().waitingLine[elevatorId].notify();
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
        for (PersonRequest request : waitingLine[id]) {
            if (floorString2Int(request.getFromFloor()) == floor) {
                return true;
            }
        }
        return false;
    }




    public void stopAllElevators() {
        for (Elevator elevator : elevators) {
            elevator.setShouldTerminate(true);
        }
    }
}
