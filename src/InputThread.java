import com.oocourse.elevator1.ElevatorInput;
import com.oocourse.elevator1.PersonRequest;
import com.oocourse.elevator1.Request;

public class InputThread implements Runnable{
    private static InputThread instance;
    private InputThread() {}
    public static InputThread getInstance() {
        if (instance == null) {
            instance = new InputThread();
        }
        return instance;
    }

    /**
     * 实现电梯系统的主要输入处理循环。
     * <p>
     * 此方法作为输入线程的入口点。它使用一个 {@link ElevatorInput} 持续从标准输入 (System.in) 读取 {@link Request} 对象。
     * 有效的 {@link PersonRequest} 对象会被提取出来，并通过 {@link Scheduler#newRequest(PersonRequest)} 分发给核心 {@link Scheduler}。
     * </p>
     * <p>
     * 该循环会一直持续，直到 {@code ElevatorInput} 通过返回 {@code null} 来标志输入结束。
     * 循环终止后，该方法执行清理和关闭步骤：
     * <ol>
     *     <li>关闭 {@code ElevatorInput}。关闭期间的异常会被捕获并打印其堆栈跟踪。</li>
     *     <li>使用 {@link Thread#sleep(long)} 短暂暂停（100毫秒）。这可能有助于让待处理的操作或输出完成。
     *         休眠期间的异常会被捕获并打印其堆栈跟踪。</li>
     *     <li>通过 {@link Scheduler#stopAllElevators()} 指示 {@link Scheduler} 停止所有电梯操作。</li>
     * </ol>
     * 此方法假设它在自己的线程中运行，并负责根据输入流的结束来发起电梯系统的关闭序列。
     *
     * @see ElevatorInput
     * @see Request
     * @see PersonRequest
     * @see Scheduler#newRequest(Passenger)
     * @see Scheduler#getInstance()
     * @see Scheduler#stopAllElevators()
     * @override // 表明此方法重写了父类或接口中的方法
     */
    @Override
    public void run() {
        ElevatorInput elevatorInput = new ElevatorInput(System.in);
        int enterTime = 0;
        while (true) {
            Request request = elevatorInput.nextRequest();
            if (request == null) {
                break;
            } else {
                // a new valid request
                if (request instanceof PersonRequest) {
                    PersonRequest personRequest = (PersonRequest) request;
                    Passenger passenger = new Passenger(enterTime++, personRequest);
                    Scheduler.newRequest(passenger);
                }
            }
        }
        try {
            elevatorInput.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            Thread.sleep(10);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Scheduler.getInstance().stopAllElevators();

    }
}
