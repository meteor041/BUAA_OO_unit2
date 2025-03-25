import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

import static utils.FloorConverter.floorString2Int;
import com.oocourse.elevator1.PersonRequest;

public class Scheduler {
    private static Scheduler instance;
    private static final int NUM_ELEVATORS = 6;
    private static final int NUM_FLOORS = 11;
    private final CopyOnWriteArrayList<Elevator> elevators;
    // 上行的乘客列表
    private final CopyOnWriteArrayList<PersonRequest>[][] waitingLineUp;
    // 下行的乘客列表
    private final CopyOnWriteArrayList<PersonRequest>[][] waitingLineDown;
//    // 用户输入是否终止
//    private volatile boolean end = false;

    public Scheduler() {
        elevators = new CopyOnWriteArrayList<>();
        waitingLineUp = new CopyOnWriteArrayList[NUM_FLOORS+1][NUM_ELEVATORS+1];
        waitingLineDown = new CopyOnWriteArrayList[NUM_FLOORS+1][NUM_ELEVATORS+1];
        for (int i = 1; i <= NUM_ELEVATORS; i++) {
            Elevator elevator = new Elevator(i);
            elevators.add(elevator);
            new Thread(elevator).start(); // 启动电梯线程
        }
        for (int i = 0; i <= NUM_FLOORS; i++) {
            for (int j = 0; j <= NUM_ELEVATORS; j++) {
                waitingLineUp[i][j] = new CopyOnWriteArrayList<>();
                waitingLineDown[i][j] = new CopyOnWriteArrayList<>();
            }
        }
    }

    /**
     * 获取调度器的单例实例
     * @return Scheduler的单例实例
     */
    public static Scheduler getInstance() {
        if (instance == null) {
            instance = new Scheduler();
        }
        return instance;
    }

    public static void newRequest(PersonRequest request) {
        System.out.println("-----------ID:" + Thread.currentThread().getId());
        getInstance().recieveRequest(request);
    }

    /**
     * 处理新的乘客请求，分配电梯并加入等待队列
     * @param request 乘客请求对象，包含出发楼层、目标楼层等信息
     */
    public synchronized void recieveRequest(PersonRequest request) {
        System.out.println("Recieving request: " + request);
        int elevatorId = request.getElevatorId();
        Elevator elevator = this.elevators.get(elevatorId - 1);
        Integer fromFloor = floorString2Int(request.getFromFloor());
        Integer toFloor = floorString2Int(request.getToFloor());
        addWaitingLine(request, fromFloor, toFloor, elevatorId);
        elevator.assignFloor(fromFloor);
    }

    /**
     * 将乘客加入等待队列
     * @param request
     */
    private synchronized void addWaitingLine(PersonRequest request, int fromFloor,
                                int toFloor, int elevatorId) {
        if (fromFloor < toFloor) {
            waitingLineUp[fromFloor+4][elevatorId].add(request);
        } else {
            waitingLineDown[fromFloor+4][elevatorId].add(request);
        }
    }

    /**
     * 判断电梯是否可以在当前楼层接载乘客
     * @param elevator 电梯对象
     * @param floor 当前楼层
     * @param leaveElevator 是否有乘客要离开电梯
     * @return true表示可以接载乘客，false表示不能接载
     */
    public boolean canEnter(Elevator elevator, int floor, boolean leaveElevator) {
        int id = elevator.getId();
        return (!elevator.full() || leaveElevator) &&
                (waitingLineDown[floor+4][id].size() > 0 || waitingLineUp[floor+4][id].size() > 0);
    }

    /**
     * 电梯到达调度器指定的楼层
     * @param elevator
     * @param floor
     * @return 返回值表示是否有乘客在当前楼层上电梯
     */
    public boolean elevatorArrived(Elevator elevator, int floor) {
        boolean allEnter = true;
        int elevatorId = elevator.getId();
        // 不管你上行还是下行,能接就接
        CopyOnWriteArrayList<PersonRequest> lineUp = waitingLineUp[floor+4][elevatorId];
        CopyOnWriteArrayList<PersonRequest> lineDown = waitingLineDown[floor+4][elevatorId];
        ArrayList<PersonRequest> upToKeep = new ArrayList<>();
        ArrayList<PersonRequest> downToKeep = new ArrayList<>();

        for (PersonRequest request : lineUp) {
            int toFloor = floorString2Int(request.getToFloor());
            if (!elevator.full()) {
                elevator.addRequest(request, toFloor);
            } else {
                allEnter = false;
                upToKeep.add(request);
            }
        }
        waitingLineUp[floor+4][elevatorId] = new CopyOnWriteArrayList<>(upToKeep);

        for (PersonRequest request : lineDown) {
            int toFloor = floorString2Int(request.getToFloor());
            if (!elevator.full()) {
                elevator.addRequest(request, toFloor);
            } else {
                allEnter = false;
                downToKeep.add(request);
            }
        }
        waitingLineDown[floor+4][elevatorId] = new CopyOnWriteArrayList<>(downToKeep);

        return allEnter;
    }

//    public boolean isEnd() {
//        return end;
//    }

//    public void setEnd(boolean end) {
//        this.end = end;
//    }

    public void stopAllElevators() {
        for (Elevator elevator : elevators) {
            elevator.setShouldTerminate(true);
        }
    }
}
