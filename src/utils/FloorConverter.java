package utils;

public class FloorConverter {
    /**
     * 将楼层字符串转换为整数。
     *
     * @param toFloorString 楼层字符串，例如 "F2", "B1"
     * @return 楼层整数，例如 2, -1
     * @throws IllegalArgumentException 如果楼层字符串格式不正确。
     */
    public static int floorString2Int(String toFloorString) {
        if (toFloorString.substring(0,1).equals("B")) {
            // 地下层
            return -1 * Integer.parseInt(toFloorString.substring(1));
        } else {
            return Integer.parseInt(toFloorString.substring(1));
        }
    }

    /**
     * 楼层转换工具类，用于在楼层字符串表示形式和整数表示形式之间进行转换。
     *
     * @param floor 楼层整型形式
     * @return 楼层字符串
     */
    public static String floorInt2String(int floor) {
        if (floor > 0) {
            return "F" + floor;
        } else {
            return "B" + (-floor);
        }
    }

    /**
     * 计算两个楼层整数形式之间的距离
     */
    public static int calFloorDistance(int x, int y) {
        if (x * y > 0) {
            return (x > y) ? (x - y) : (y - x);
        } else {
            return (x > y) ? (x - y - 1) : (y - x - 1);
        }
    }
}
