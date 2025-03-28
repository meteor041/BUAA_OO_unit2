import com.oocourse.elevator1.PersonRequest;
import com.oocourse.elevator1.TimableOutput;
import utils.TimeFixer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static utils.FloorConverter.floorString2Int;
import static utils.FloorConverter.floorInt2String;

public class Elevator implements Runnable {
    // 初始位置
    private static final int initPos = 1;
    // 移动速度
    private static final int move_time = 400; // unit: ms/层
    // 限制乘坐人数
    private static final int max_num = 6;
    // 开门到关门之间的间隔
    private static final int min_gap = 400; // unit: ms
    // 电梯ID
    private final int id;
    // 当前电梯是否空闲
    private final AtomicBoolean idle;
    // 目标楼层到楼层用户请求的映射
    private final HashMap<Integer, TreeSet<Passenger>> floor2req;
    // 当前所在楼层
    private int currentFloor;
    // 当前乘坐电梯人数
    private int currentNum;
    // 当前运行方向
    private Direction direction;
    // 是否应该终止线程
    private volatile boolean shouldTerminate = false;
    // 时间修正工具
    private final TimeFixer timeFixer;

    public Elevator(int id) {
        this.id = id;
        idle = new AtomicBoolean(true);
        floor2req = new HashMap<>();
        for (int i = -4; i <= 7; i++) {
            if (i == 0) {
                continue;
            }
            floor2req.put(i, new TreeSet<>(Comparator.comparingInt(
                    (Passenger p) -> p.getRequest().getPriority())
                    .reversed().thenComparingLong(Passenger::getEnterTime)));
        }
        currentFloor = initPos;
        direction = Direction.DUNNO;
        timeFixer = new TimeFixer();
    }

    /**
     * 模拟有人进入电梯
     *
     * @param passenger 乘客
     * @param floor
     */
    public void passengerIn(Passenger passenger, int floor) {
        currentNum += 1;
        PersonRequest request = passenger.getRequest();
        TimableOutput.println("IN-" + request.getPersonId() + 
                "-" + request.getFromFloor() + "-" + this.id);
        floor2req.get(floor).add(passenger);
    }

    /**
     * 接收InputThread发送的输入结束信号
     *
     * @param shouldTerminate
     */
    public void setShouldTerminate(boolean shouldTerminate) {
        if (idle.get()) {
            synchronized (Scheduler.getInstance().getWaitingLine(id)) {
                Scheduler.getInstance().getWaitingLine(id).notify();
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
        int bestPriority = 0;
        if (Scheduler.getInstance().getWaitingLine(id).isEmpty()) {
            // 如果等待列表为空,说明电梯线程这次被终止信号唤醒
            // 不要为它选择方向
            return;
        }
        for (Passenger passenger : Scheduler.getInstance().getWaitingLine(id)) {
            PersonRequest request = passenger.getRequest();
            int floor = floorString2Int(request.getFromFloor());
            if (floor == currentFloor) {
                direction = Direction.DUNNO;
                return;
            }
            int priority = request.getPriority();
            if (priority > bestPriority) {
                bestFloor = floor;
                bestPriority = priority;
            }
        }
        long gap = timeFixer.archive();
        if (gap < min_gap) {
            try {
                Thread.sleep(min_gap - gap);
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
        // 判断电梯是否还有需要去往的楼层
        while (true) {
            synchronized (Scheduler.getInstance().getWaitingLine(id)) {
                if ((Scheduler.getInstance().getWaitingLine(id).isEmpty() 
                        && currentNum == 0) && !shouldTerminate) {
                    // 这个电梯,不需要了
                    try {
                        // 等待用户的请求输入
                        Scheduler.getInstance().getWaitingLine(id).wait();
                        timeFixer.init();
                        elevatorAwake();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if (Scheduler.getInstance().getWaitingLine(id).isEmpty() 
                        && currentNum == 0 && shouldTerminate) {
                    return;
                }
                // 选择电梯方向
                chooseDir();

                // 根据上下乘客情况选择开关门
                openAndCloseDoor();

                if (Scheduler.getInstance().getWaitingLine(id).isEmpty() 
                        && currentNum == 0) {
                    // 这个电梯,不需要了
                    if (shouldTerminate) {
                        return;
                    }
                    idle.set(true);
                    continue;
                }

            }
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
        int status = 0;
        int bestFloor = 99;
        int bestFloorPriority = 0;
        long bestFloorEnterTime = 999999999L;
        // 优先查看当前楼层外等待的用户
        for (Passenger passenger : Scheduler.getInstance().getWaitingLine(id)) {
            PersonRequest request = passenger.getRequest();
            long enterTime = passenger.getEnterTime();
            int floor = floorString2Int(request.getFromFloor());
            if (floor == currentFloor) {
                status = 2;
                int toFloor = floorString2Int(request.getToFloor());
                int priority = request.getPriority();
                if (priority > bestFloorPriority || (priority == bestFloorPriority 
                        && enterTime < bestFloorEnterTime)) {
                    bestFloor = toFloor;
                    bestFloorPriority = priority;
                    bestFloorEnterTime = enterTime;
                }
            } else {
                // 楼上或者楼下发来的请求
                if (status == 2) {
                    continue;
                }
                status = 1;
                int fromFloor = floorString2Int(request.getFromFloor());
                int priority = request.getPriority();
                if (priority > bestFloorPriority || 
                        (priority == bestFloorPriority && enterTime < bestFloorEnterTime)) {
                    bestFloor = fromFloor;
                    bestFloorPriority = priority;
                    bestFloorEnterTime = enterTime;
                }
            }

        }
        if (bestFloor > currentFloor) {
            direction = Direction.UP;
        } else {
            // 也可能是IDLE,但是在run()方法中会被统一终止
            direction = Direction.DOWN;
        }
    }

    /**
     * 基于LOOK策略,不关注乘客的优先级,进行电梯的方向选择
     */
    public void chooseDir() {
        switch (this.direction) {
            case DUNNO:
                dunnoFindPriority();
                break;
            case UP:
                for (Integer floor : floor2req.keySet()) {
                    if (!floor2req.get(floor).isEmpty()) {
                        if (floor > currentFloor) {
                            // 电梯方向保持不变
                            return;
                        }
                    }
                }
                // 如果当前电梯未满载,可以判断电梯外等待中的乘客请求
                if (!this.full()) {
                    for (Passenger passenger : Scheduler.getInstance().getWaitingLine(id)) {
                        int fromFloor = floorString2Int(passenger.getRequest().getFromFloor());
                        if (fromFloor > currentFloor) {
                            // 电梯方向保持不变
                            return;
                        } else if (fromFloor == currentFloor) {
                            int toFloor = floorString2Int(passenger.getRequest().getToFloor());
                            if (toFloor > currentFloor) {
                                // 电梯方向保持不变
                                return;
                            }
                        }
                    }
                }
                direction = Direction.DOWN;
                break;
            case DOWN:
                for (Integer floor : floor2req.keySet()) {
                    if (!floor2req.get(floor).isEmpty()) {
                        if (floor < currentFloor) {
                            // 电梯方向保持不变
                            return;
                        }
                    }
                }
                // 如果当前电梯未满载,可以判断电梯外等待中的乘客请求
                if (!this.full()) {
                    for (Passenger passenger : Scheduler.getInstance().getWaitingLine(id)) {
                        int fromFloor = floorString2Int(passenger.getRequest().getFromFloor());
                        if (fromFloor < currentFloor) {
                            // 电梯方向保持不变
                            return;
                        } else if (fromFloor == currentFloor) {
                            int toFloor = floorString2Int(passenger.getRequest().getToFloor());
                            if (toFloor < currentFloor) {
                                // 电梯方向保持不变
                                return;
                            }
                        }
                    }
                }
                direction = Direction.UP;
                break;
            default:
                throw new IllegalArgumentException("No such direction");

        }
    }

    /**
     * 乘客到达目的地
     *
     * @param passenger
     */
    private void passengerOut(Passenger passenger) {
        currentNum--;
        PersonRequest request = passenger.getRequest();
        TimableOutput.println("OUT-" + request.getPersonId() + 
                "-" + request.getToFloor() + "-" + this.id);
    }

    /**
     * 模拟电梯的开关门
     */
    private void openAndCloseDoor() {
        boolean leaveElevator = !floor2req.get(currentFloor).isEmpty();
        boolean enterElevator = Scheduler.getInstance().canEnter(this, currentFloor, leaveElevator);

        if (enterElevator || leaveElevator) {
            TimableOutput.println("OPEN-" + floorInt2String(currentFloor) + "-" + this.id);
            timeFixer.init();
        }
        if (leaveElevator) {
            // 有人需要离开
            TreeSet<Passenger> list = floor2req.get(currentFloor);
            for (Passenger passenger : list) {
                passengerOut(passenger);
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
            passengersIn();
        }
        if (enterElevator || leaveElevator) {
            TimableOutput.println("CLOSE-" + floorInt2String(currentFloor) + "-" + this.id);
            timeFixer.init();
        }
    }

    /**
     * 电梯到达调度器指定的楼层
     */
    private void passengersIn() {
        // 不管你上行还是下行,能接就接
        TreeSet<Passenger> line = Scheduler.getInstance().getWaitingLine(id);
        Iterator<Passenger> iterator = line.iterator();
        while (iterator.hasNext()) {
            Passenger passenger = iterator.next();
            int toFloor = floorString2Int(passenger.getRequest().getToFloor());
            int fromFloor = floorString2Int(passenger.getRequest().getFromFloor());
            if (!this.full() && fromFloor == currentFloor
                    && ((direction == Direction.UP && toFloor > fromFloor)
                    || (direction == Direction.DOWN && toFloor < fromFloor))) {
                this.passengerIn(passenger, toFloor);
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
            default:
                throw new IllegalArgumentException("No such direction");
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
