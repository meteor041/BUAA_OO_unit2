import com.oocourse.elevator1.PersonRequest;

import java.util.ArrayList;
import java.util.Iterator;

public class Scheduler {
    private static Scheduler instance;
    private static final int NUM_ELEVATORS = 6;
    private static final int NUM_FLOORS = 11;
    private final ArrayList<Elevator> elevators;
    // 上行的乘客列表
    private final ArrayList<PersonRequest>[][] waitingLineUp;
    // 下行的乘客列表
    private final ArrayList<PersonRequest>[][] waitingLineDown;

    public Scheduler() {
        elevators = new ArrayList<>();
        waitingLineUp = new ArrayList[NUM_FLOORS+1][NUM_ELEVATORS+1];
        waitingLineDown = new ArrayList[NUM_FLOORS+1][NUM_ELEVATORS+1];
        for (int i = 1; i <= NUM_ELEVATORS; i++) {
            elevators.add(new Elevator(i));
        }
        for (int i = 0; i <= NUM_FLOORS; i++) {
            for (int j = 0; j <= NUM_ELEVATORS; j++) {
                waitingLineUp[i][j] = new ArrayList<>();
                waitingLineDown[i][j] = new ArrayList<>();
            }
        }
    }

    public static Scheduler getInstance() {
        if (instance == null) {
            instance = new Scheduler();
        }
        return instance;
    }

    /**
     * 面对新加入的乘客请求,电梯调度器做出的选择
     * @param request
     */
    public void requestElevator(PersonRequest request) {
        synchronized (this) {
            int elevatorId = request.getElevatorId();
            Elevator elevator = this.elevators.get(elevatorId - 1);
            Integer fromFloor = floorString2Int(request.getFromFloor());
            Integer toFloor = floorString2Int(request.getToFloor());
            addWaitingLine(request, fromFloor, toFloor, elevatorId);
            elevator.assign_floor(fromFloor);
            // 如果电梯空闲,则启动电梯进程
            if (elevator.isIdle()) {
                new Thread(elevator).start();
            }
        }
    }

    /**
     * 将乘客加入等待队列
     * @param request
     */
    private void addWaitingLine(PersonRequest request, int fromFloor,
                                int toFloor, int elevatorId) {
        if (fromFloor < toFloor) {
            waitingLineUp[fromFloor+4][elevatorId].add(request);
        } else {
            waitingLineDown[fromFloor+4][elevatorId].add(request);
        }
    }

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
        ArrayList<PersonRequest> lineUp = waitingLineUp[floor+4][elevatorId];
        ArrayList<PersonRequest> lineDown = waitingLineDown[floor+4][elevatorId];
        Iterator<PersonRequest> iterUp = lineUp.iterator();
        Iterator<PersonRequest> iterDown = lineDown.iterator();
        while (iterUp.hasNext()) {
            PersonRequest request = iterUp.next();
            int toFloor = floorString2Int(request.getToFloor());
            if (!elevator.full()) {
                elevator.addRequest(request, toFloor);
                iterUp.remove();
            } else {
                allEnter = false;
                break;
            }
        }

        while (iterDown.hasNext()) {
            PersonRequest request = iterDown.next();
            int toFloor = floorString2Int(request.getToFloor());
            if (!elevator.full()) {
                elevator.addRequest(request, toFloor);
                iterDown.remove();
            } else {
                allEnter = false;
                break;
            }
        }

        return allEnter;
    }

    private static int floorString2Int(String toFloorString) {
        boolean negFlag;
        if (toFloorString.substring(0,1).equals("B")) {
            // 地下层
            return -1 * Integer.parseInt(toFloorString.substring(1));
        } else {
            return Integer.parseInt(toFloorString.substring(1));
        }
    }

}
