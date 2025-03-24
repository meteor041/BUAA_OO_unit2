import com.oocourse.elevator1.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

public class Elevator implements Runnable {
    // 初始位置
    private final static int initPos = 1;
    // 移动速度
    private final static int move_time = 400; // unit: ms/层
    // 限制乘坐人数
    private final static int max_num = 6;
    // 开门到关门之间的间隔
    private final static int min_gap = 400; // unit: ms
    // 电梯ID
    private final int id;
    // 当前电梯是否空闲
    private boolean idle;
    // 目标楼层列表
    TreeSet<Integer> target;
    // 目标楼层到楼层用户请求的映射
    HashMap<Integer, ArrayList<PersonRequest>> floor2req;
    // 当前所在楼层
    int currentFloor;
    // 当前乘坐电梯人数
    int currentNum;
    // 当前运行方向
    Direction direction;

    public Elevator(int id) {
        this.id = id;
        idle = true;
        target = new TreeSet<>();
        floor2req = new HashMap<>();
        currentFloor = initPos;
    }

    /**
     * 添加电梯需要到达的楼层
     *
     * @param floor
     */
    public void assign_floor(Integer floor) {
        target.add(floor);
    }

    /**
     * 有人上电梯
     *
     * @param request
     */
    public void addRequest(PersonRequest request, int floor) {
        int elevatorId = request.getElevatorId();
        TimableOutput.println("IN-" + request.getPersonId() + "-" + request.getFromFloor() + "-" + this.id);
        ArrayList<PersonRequest> list = floor2req.getOrDefault(floor, new ArrayList<>());
        list.add(request);
        floor2req.put(floor, list);
        assign_floor(floor);
    }

    @Override
    public void run() {
        this.idle = false;
        while (true) {
            // 根据上下乘客情况选择开关门
            openAndCloseDoor();

            // 判断电梯是否还有需要去往的楼层
            if (target.isEmpty()) {
                // 这个电梯,不需要了
                this.idle = true;
                return;
            }

            // 选取下一个目的楼层,确定运动方向
            int targetFloor = target.getFirst();
            if (currentFloor < targetFloor) {
                // 往上走
                this.direction = Direction.UP;
            } else if (currentFloor > targetFloor) {
                // 往下走
                this.direction = Direction.DOWN;
            }

            moveTo();
        }
    }

    /**
     * 乘客到达目的地
     * @param request
     */
    private void finishRequest(PersonRequest request) {
        TimableOutput.println("OUT-" + request.getPersonId() + "-" + currentFloor + "-" + this.id);
    }

    /**
     * 模拟电梯的开关门
     */
    private void openAndCloseDoor() {

        boolean leaveElevator = (floor2req.get(currentFloor) != null) && !floor2req.get(currentFloor).isEmpty();
        boolean enterElevator = Scheduler.getInstance().canEnter(this, currentFloor, leaveElevator);

        if (enterElevator || leaveElevator) {
            TimableOutput.println("OPEN-" + floorInt2String(currentFloor) + "-" + this.id);
        }
        if (leaveElevator) {
            // 有人需要离开
            ArrayList<PersonRequest> list = floor2req.get(currentFloor);
            for (int i = 0; i < list.size(); i++) {
                PersonRequest request = list.get(i);
                finishRequest(request);
            }
            // 电梯上所有去往current_floor的都下了,删去该目的楼层
            floor2req.remove(currentFloor);
        }

        boolean enterAll = Scheduler.getInstance().elevatorArrived(this, currentFloor);

        // 如果有人要上下电梯,就得关门
        if (enterElevator || leaveElevator) {
            try {
                Thread.sleep(min_gap);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            TimableOutput.println("CLOSE-" + floorInt2String(currentFloor) + "-" + this.id);
        }

        // 到达当前楼层后,电梯上要下的乘客一定会下,要上的乘客不一定都能上.
        // 如果要上的乘客都上了,就可以让target列表去除该楼层
        target.remove(currentFloor);
        if (!enterAll) {
            target.add(currentFloor);
        }
    }

    /**
     * 根据this.direction,实现电梯移动
     */
    private void moveTo() {
        // 模拟电梯运动所消耗的时间
        try {
            Thread.sleep(this.move_time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        switch (this.direction) {
            case Direction.UP:
                this.currentFloor++;
                if (this.currentFloor == 0) {
                    this.currentFloor++;
                }
                break;
            case Direction.DOWN:
                this.currentFloor--;
                if (this.currentFloor == 0) {
                    this.currentFloor--;
                }
                break;
        }
        TimableOutput.println("ARRIVE-" + floorInt2String(currentFloor) + "-" + id);
    }

    public int getId() {
        return id;
    }

    public boolean isIdle() {
        return idle;
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public int getCurrentNum() {
        return currentNum;
    }

    public boolean full() {
        return this.currentNum == max_num;
    }

    private String floorInt2String(int floor) {
        if (floor > 0) {
            return "F" + floor;
        } else {
            return "B" + (-floor);
        }
    }
}
