import com.oocourse.elevator1.*;
import utils.TimeFixer;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static utils.FloorConverter.*;

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
    private final AtomicBoolean idle;
    // 目标楼层到楼层用户请求的映射
    private final HashMap<Integer, TreeSet<PersonRequest>> floor2req;
    // 当前所在楼层
    private int currentFloor;
    // 当前乘坐电梯人数
    private int currentNum;
    // 当前运行方向
    private Direction direction;
    // 是否应该终止线程
    private volatile boolean shouldTerminate = false;
    // 时间修正工具
    private TimeFixer timeFixer;

    public Elevator(int id) {
        this.id = id;
        idle = new AtomicBoolean(true);
        floor2req = new HashMap<>();
        for (int i = -4; i <= 7; i++) {
            if (i == 0) {
                continue;
            }
            floor2req.put(i, new TreeSet<>(Comparator.comparingInt(PersonRequest::getPriority)
                    .reversed().thenComparingInt(PersonRequest::getPersonId)));
        }
        currentFloor = initPos;
        direction = Direction.DUNNO;
        timeFixer = new TimeFixer();
    }

    /**
     * 模拟有人进入电梯
     *
     * @param request
     */
    public void passengerIn(PersonRequest request, int floor) {
        currentNum += 1;
        TimableOutput.println("IN-" + request.getPersonId() + "-" + request.getFromFloor() + "-" + this.id);
        floor2req.get(floor).add(request);
    }

    /**
     * 接收InputThread发送的输入结束信号
     *
     * @param shouldTerminate
     */
    public void setShouldTerminate(boolean shouldTerminate) {
//        System.out.println("Elevator thread" + id + " shouldTerminate: " + this.shouldTerminate + "Thread id: " + Thread.currentThread().getId());
//        System.out.println("idle " + idle);
        if (idle.get()) {
            synchronized (Scheduler.getInstance().waitingLine.get(this.id - 1)) {
                Scheduler.getInstance().waitingLine.get(this.id - 1).notify();
            }
        }
        this.shouldTerminate = shouldTerminate;
    }

    /**
     * 电梯进程被唤醒后立即进行的相关操作
     * 1. 启动时间修正器
     * 2. 修改空闲标志
     * 3. 确定运动方向
     * 4. 决定是否进行量子移动
     */
    public void elevatorAwake() {
        idle.set(false);
        // 遍历当前存在的请求
        // 1. 如果存在同楼层的请求,则放弃量子移动,方向设置为DUNNO
        // 2. 如果只有其他楼层的请求,我们根据距离和优先级判断最优的楼层选择,设置方向为前往对应楼层的方向
        int bestFloor = -10;
        int minDistance = 100;
        int bestPriority = 0;
        if (Scheduler.getInstance().waitingLine.get(this.id - 1).isEmpty()) {
            // 如果等待列表为空,说明电梯线程这次被终止信号唤醒
            // 不要为它选择方向
            return;
        }
        for (PersonRequest request : Scheduler.getInstance().waitingLine.get(this.id - 1)) {
            int floor = floorString2Int(request.getFromFloor());
            if (floor == currentFloor) {
                direction = Direction.DUNNO;
                return;
            }
            int floorDistance = calFloorDistance(floor, currentFloor);
            int priority = request.getPriority();
            if (floorDistance < minDistance) {
                // 更新最小距离和最优楼层
                bestFloor = floor;
                minDistance = floorDistance;
                bestPriority = priority;
            } else if (minDistance == floorDistance && priority > bestPriority) {
                bestFloor = floor;
                bestPriority = priority;
            }
        }
        long gap = timeFixer.archive();
        if (gap < min_gap) {
            try {
                Thread.sleep(min_gap-gap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (bestFloor > currentFloor) {
            direction = Direction.UP;
            moveTo();
        } else if (bestFloor < currentFloor) {
            direction = Direction.DOWN;
            moveTo();
        } else {
            throw new IllegalArgumentException("Wrong move");
        }
    }

    @Override
    public void run() {
//            // 获取当前线程
//            Thread currentThread = Thread.currentThread();
//
//            // 打印线程信息
//            System.out.println("当前线程 ID: " + currentThread.getId());
        // 判断电梯是否还有需要去往的楼层
        while (true) {
            synchronized (Scheduler.getInstance().waitingLine.get(this.id - 1)) {
                if ((Scheduler.getInstance().waitingLine.get(this.id - 1).isEmpty() && currentNum == 0) && !shouldTerminate) {

                    // 这个电梯,不需要了
                    try {
//                        TimableOutput.println("Elevator thread" + id + " wait " + Thread.currentThread().getId());
                        // 等待用户的请求输入
                        Scheduler.getInstance().waitingLine.get(this.id - 1).wait();
                        timeFixer.init();
//                        TimableOutput.println("Elevator thread" + id + " awake " + Thread.currentThread().getId());
                        elevatorAwake();
//                        System.out.println(idle);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

//            timeFixer.init();
                if (Scheduler.getInstance().waitingLine.get(this.id - 1).isEmpty() && currentNum == 0 && shouldTerminate) {
//                System.out.println("here Elevator thread" + id + " 这个电梯,不需要了 " + Thread.currentThread().getId());
                    return;
                }

                // 根据上下乘客情况选择开关门
                openAndCloseDoor();

                if (Scheduler.getInstance().waitingLine.get(this.id - 1).isEmpty() && currentNum == 0) {
                    // 这个电梯,不需要了
                    if (shouldTerminate) {
//                    System.out.println("Elevator thread" + id + " 这个电梯,不需要了 " + Thread.currentThread().getId());
                        return;
                    }
                    idle.set(true);
                    continue;
                }

            }
            chooseDir();
            // 模拟电梯运动所消耗的时间
            try {
                long gap = timeFixer.archive();
                Thread.sleep(this.move_time - gap);
                timeFixer.init();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            moveTo();
        }
    }

    /**
     * 在电梯被唤醒后确定首次移动的方向
     */
    public void dunnoFindPriority() {

        // 有人进入了电梯,则优先考虑电梯内乘客的请求,若同时有上行和下行的乘客根据优先级选择
        if (currentNum != 0) {
            Integer bestFloor = -10;
            int minDistance = 99;
            int bestFloorMaxPriority = 0;
            int bestFloorNum = 0;
            for (Integer floor : floor2req.keySet()) {
                if (!floor2req.get(floor).isEmpty()) {
                    int floorDistance = calFloorDistance(floor, currentFloor);
                    int priority = floor2req.get(floor).getFirst().getPriority();
                    int num = floor2req.get(floor).size();
                    if (minDistance > floorDistance) {
                        // 更新最小距离和最优楼层
                        bestFloor = floor;
                        minDistance = floorDistance;
                        bestFloorMaxPriority = priority;
                        bestFloorNum = num;
                    } else if (minDistance == floorDistance) {
                        if (priority > bestFloorMaxPriority) {
                            bestFloor = floor;
                            bestFloorMaxPriority = priority;
                            bestFloorNum = num;
                        } else if (bestFloorMaxPriority == priority) {
                            // 若最小距离和乘客最大优先级都相同,则比较乘客数量
                            if (num > bestFloorNum) {
                                bestFloor = floor;
                                bestFloorNum = num;
                            }
                        }
                    }
                }
            }
            if (bestFloor > currentFloor) {
                direction = Direction.UP;
            } else {
                direction = Direction.DOWN;
            }
            return;
        }
        // 若电梯内无人,通过距离,优先级寻找最优方向
        Integer bestFloor = -10;
        int minDistance = 99;
        int bestFloorPriority = 0;
        for (PersonRequest request : Scheduler.getInstance().waitingLine.get(id - 1)) {
            int floor = floorString2Int(request.getFromFloor());
            int floorDistance = calFloorDistance(floor, currentFloor);
            int priority = request.getPriority();
            if (minDistance > floorDistance) {
                // 更新最小距离和最优楼层
                bestFloor = floor;
                minDistance = floorDistance;
                bestFloorPriority = priority;
            } else if (minDistance == floorDistance && priority > bestFloorPriority) {
                bestFloor = floor;
                bestFloorPriority = priority;
            }
        }
        if (bestFloor > currentFloor) {
            direction = Direction.UP;
        } else {
            direction = Direction.DOWN;
        }
    }

    /**
     * 基于LOOK策略,不关注乘客的优先级,进行电梯的方向选择
     */
    public void chooseDir() {
//        System.out.println("Elevator thread" + id + " chooseDir");
        boolean flag = false;

        switch (this.direction) {
            case DUNNO:
                dunnoFindPriority();
                break;
            case UP:
                for (Integer floor : floor2req.keySet()) {
                    if (!floor2req.get(floor).isEmpty()) {
                        if (floor > currentFloor) {
                            flag = true;
                            break;
                        }
                    }
                }
                // 如果当前电梯未满载,可以判断电梯外等待中的乘客请求
                if (!this.full()) {
                    for (PersonRequest request : Scheduler.getInstance().waitingLine.get(id - 1)) {
                        if (floorString2Int(request.getFromFloor()) > currentFloor) {
                            flag = true;
                            break;
                        }
                    }
                }
                if (flag) {
                    direction = Direction.UP;
                } else {
                    direction = Direction.DOWN;
                }
                break;
            case DOWN:

                for (Integer floor : floor2req.keySet()) {
                    if (!floor2req.get(floor).isEmpty()) {
                        if (floor < currentFloor) {
                            flag = true;
                            break;
                        }
                    }
                }
                // 如果当前电梯未满载,可以判断电梯外等待中的乘客请求
                if (!this.full()) {
                    if (!flag) {
                        for (PersonRequest request : Scheduler.getInstance().waitingLine.get(id - 1)) {
                            if (floorString2Int(request.getFromFloor()) < currentFloor) {
                                flag = true;
                                break;
                            }
                        }
                    }
                }
                if (flag) {
                    direction = Direction.DOWN;
                } else {
                    direction = Direction.UP;
                }
                break;
            default:
                throw new IllegalArgumentException("No such direction");

        }
    }

    /**
     * 乘客到达目的地
     *
     * @param request
     */
    private void passengerOut(PersonRequest request) {
        currentNum--;
        TimableOutput.println("OUT-" + request.getPersonId() + "-" + request.getToFloor() + "-" + this.id);
    }

    /**
     * 模拟电梯的开关门
     */
    private void openAndCloseDoor() {
//        System.out.println("try to open and close" + Thread.currentThread().getId());
        boolean leaveElevator = !floor2req.get(currentFloor).isEmpty();
        boolean enterElevator = Scheduler.getInstance().canEnter(this, currentFloor, leaveElevator);

        if (enterElevator || leaveElevator) {
            TimableOutput.println("OPEN-" + floorInt2String(currentFloor) + "-" + this.id);
            timeFixer.init();
        }
        if (leaveElevator) {
            // 有人需要离开
            TreeSet<PersonRequest> list = floor2req.get(currentFloor);
            for (PersonRequest request : list) {
                passengerOut(request);
            }
            list.clear();
        }

        // 如果有人要上下电梯,就得依次完成门保持开着400ms和接收乘客的操作
        if (enterElevator || leaveElevator) {
            try {
                long gap = timeFixer.archive();
                Thread.sleep(min_gap - gap);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // 为什么这里不可以只在enterElevator为真的时候调用passengerIn()方法?
            // enterElevator是开门时检测有无乘客进入的结果,
            // 如果这开关门400ms间隔内有新加入的乘客,我们也要让他能进就进
            // 因此只要电梯门开了,我们都要调用该方法,让乘客进来
            // 乘客进入电梯
            passengerIn();
        }
        if (enterElevator || leaveElevator) {
            TimableOutput.println("CLOSE-" + floorInt2String(currentFloor) + "-" + this.id);
            timeFixer.init();
        }
    }

    /**
     * 电梯到达调度器指定的楼层
     */
    private void passengerIn() {
        // 不管你上行还是下行,能接就接
        TreeSet<PersonRequest> line = Scheduler.getInstance().waitingLine.get(id - 1);
        Iterator<PersonRequest> iterator = line.iterator();
        while (iterator.hasNext()) {
            PersonRequest request = iterator.next();
            int toFloor = floorString2Int(request.getToFloor());
            if (!this.full() && floorString2Int(request.getFromFloor()) == currentFloor) {
                this.passengerIn(request, toFloor);
                iterator.remove();
            }
        }
    }

    /**
     * 根据this.direction,实现电梯移动
     */
    private void moveTo() {
        switch (this.direction) {
            case UP:
                this.currentFloor++;
                if (this.currentFloor == 0) {
                    this.currentFloor++;
                }
                break;
            case DOWN:
                this.currentFloor--;
                if (this.currentFloor == 0) {
                    this.currentFloor--;
                }
                break;
        }
        TimableOutput.println("ARRIVE-" + floorInt2String(currentFloor) + "-" + id);
        timeFixer.init();
    }

    /**
     * 获取电梯的唯一标识ID
     *
     * @return 电梯的ID
     */
    public int getId() {
        return id;
    }

    /**
     * 检查电梯是否已满载
     *
     * @return true表示电梯已满(达到最大承载人数)，false表示还有空位
     */
    public boolean full() {
        return this.currentNum == max_num;
    }
}
