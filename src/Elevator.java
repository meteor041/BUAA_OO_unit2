import com.oocourse.elevator1.*;

//import java.lang.management.ManagementFactory;
//import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static utils.FloorConverter.floorInt2String;
//import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
//import java.util.concurrent.locks.Lock;
//import java.util.concurrent.locks.ReentrantReadWriteLock;

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
    private AtomicBoolean idle;
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
    // 用于线程同步的锁和条件变量
    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private volatile boolean shouldTerminate = false; // 是否应该终止线程

    public Elevator(int id) {
        this.id = id;
        idle = new AtomicBoolean(true);
        target = new TreeSet<>();
        floor2req = new HashMap<>();
        currentFloor = initPos;
    }

    /**
     * 添加电梯需要到达的楼层.若电梯此刻为空闲,则唤醒电梯线程
     *
     * @param floor
     */
    public void assignFloor(Integer floor) {
        lock.lock();
        try{
            System.out.println("AssignFloor - target: " + target + ", idle: " + idle.get());
            target.add(floor);
            if (idle.compareAndSet(true, false)) {
                System.out.println("Signaling elevator " + id + Thread.currentThread().getId());
                notEmpty.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 模拟有人进入电梯
     *
     * @param request
     */
    public void addRequest(PersonRequest request, int floor) {
        lock.lock();
        try {
            TimableOutput.println("IN-" + request.getPersonId() + "-" + request.getFromFloor() + "-" + this.id);
            ArrayList<PersonRequest> list = floor2req.getOrDefault(floor, new ArrayList<>());
            list.add(request);
            floor2req.put(floor, list);
            target.add(floor);
        } finally {
            lock.unlock();
        }
    }

    public void setShouldTerminate(boolean shouldTerminate) {
        System.out.println("Elevator thread" + id + " shouldTerminate: " + this.shouldTerminate);
        lock.lock();
        try {
            this.shouldTerminate = shouldTerminate;
            notEmpty.signal();
        } finally {
            lock.unlock();
        }

    }

    @Override
    public void run() {
        try {
//            // 获取当前线程
//            Thread currentThread = Thread.currentThread();
//
//            // 打印线程信息
//            System.out.println("当前线程 ID: " + currentThread.getId());
            while (!shouldTerminate || !target.isEmpty()) {
                lock.lock();
                try {
                    // 判断电梯是否还有需要去往的楼层
                    while (target.isEmpty() && !shouldTerminate) {
                        // 这个电梯,不需要了
                        try {
                            System.out.println("Elevator thread" + id + " wait " + Thread.currentThread().getId());
                            notEmpty.await();
                            System.out.println("Elevator thread" + id + " awake " + Thread.currentThread().getId());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    if (target.isEmpty() && shouldTerminate) {
                        break;
                    }

                    // 根据上下乘客情况选择开关门
                    openAndCloseDoor();

                    if (target.isEmpty()) {
                        // 这个电梯,不需要了
                        idle.set(true);
                        if (shouldTerminate) {
                            break;
                        }
                        continue;
                    }

                    // 选取下一个目的楼层,确定运动方向
                    int targetFloor = target.first();
                    if (currentFloor < targetFloor) {
                        // 往上走
                        this.direction = Direction.UP;
                    } else if (currentFloor > targetFloor) {
                        // 往下走
                        this.direction = Direction.DOWN;
                    }



                    moveTo();
                } finally {
                    lock.unlock();
                }

            }
        } finally {
            System.out.println("Elevator thread" + id + " exiting");
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
//        synchronized (lock) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
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
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取电梯的唯一标识ID
     * @return 电梯的ID
     */
    public int getId() {
        return id;
    }

    /**
     * 检查电梯是否处于空闲状态
     * @return true表示电梯空闲，false表示电梯正在运行
     */
    public AtomicBoolean isIdle() {
        return this.idle;
    }

    /**
     * 获取电梯当前所在的楼层
     * @return 当前楼层，正数表示地上楼层，负数表示地下楼层
     */
    public int getCurrentFloor() {
        return currentFloor;
    }

    /**
     * 获取电梯当前承载的乘客数量
     * @return 当前乘客数量
     */
    public int getCurrentNum() {
        return currentNum;
    }

    /**
     * 检查电梯是否已满载
     * @return true表示电梯已满(达到最大承载人数)，false表示还有空位
     */
    public boolean full() {
        return this.currentNum == max_num;
    }

    public void setBusy() {
        this.idle.set(false);
    }
}
