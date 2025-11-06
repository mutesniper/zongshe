package com.muite.zongshe1.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muite.zongshe1.constant.TruckStatusConstant;
import com.muite.zongshe1.entity.Route;
import com.muite.zongshe1.entity.Task;
import com.muite.zongshe1.entity.Truck;
import com.muite.zongshe1.mapper.RouteMapper;
import com.muite.zongshe1.mapper.TaskMapper;
import com.muite.zongshe1.mapper.TruckMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Service
public class SimulationService {
    @Autowired
    TruckMapper truckMapper;
    @Autowired
    TaskMapper taskMapper;
    @Autowired
    RouteMapper routeMapper;


    private final Queue<Task> pendingTasks = new ConcurrentLinkedQueue<>();

    public void addPendingTask(Task task) {
        pendingTasks.add(task);
    }


    // 定时任务：每隔 X 毫秒执行一次
    // fixedRate：以上次任务开始时间为基准，固定间隔执行
    @Scheduled(fixedRate = 30000)
    public void syncUnassignedTasksFromDb() {
        // 1. 查询数据库中 truck_id 为 null 的所有未分配任务
        List<Task> unassignedTasks = taskMapper.findByTruckIdIsNull();

        // 2. 清空原有 pendingTasks（覆盖式核心步骤）
        pendingTasks.clear();

        // 3. 将新查询到的未分配任务批量存入队列
        if (!unassignedTasks.isEmpty()) {
            pendingTasks.addAll(unassignedTasks);
        }
        //4. 执行任务分配
        assignTasks();
    }

    // 可选：服务启动时立即同步一次（避免等待第一个定时周期）
    @PostConstruct
    public void initSync() {
        syncUnassignedTasksFromDb();
    }


    // 每秒更新一次车辆位置（仿真移动）
    @Scheduled(fixedRate = 1000)
    public void simulateMovement() {
        List<Truck> trucks = truckMapper.selectAll();
        for (Truck truck : trucks) {
            if ("运输中".equals(truck.getStatus())) {
                // 获取车辆当前任务
                Task task = taskMapper.selectByTruckId(truck.getId());
                if (task != null) {
                    Route route = getOptimalRoute(task.getStart(), task.getDestination());
                    if (route != null) {
                        // 模拟位置逐步移动（示例：从起点向终点更新经纬度）
                        String newLocation = calculateNextLocation(truck.getLocation(), route);
                        truck.setLocation(newLocation);
                        truckMapper.updateByPrimaryKey(truck);

                        // 若到达终点，更新状态为"卸货"
                        if (isArrived(newLocation, task.getDestination())) {
                            truck.setStatus("卸货");
                            truckMapper.updateByPrimaryKey(truck);
                        }
                    }
                }
            }
        }
        // 推送更新后的车辆信息到前端
        broadcastTruckStatus(trucks);
    }

    /**
     * 基于经纬度计算车辆的下一个位置（模拟向终点移动）
     * @param currentLoc 车辆当前位置（格式："纬度,经度"）
     * @param route 路径信息（包含终点经纬度）
     * @return 下一个位置（格式："纬度,经度"）
     */
    private String calculateNextLocation(String currentLoc, Route route) {
        // 1. 解析当前位置和终点的经纬度
        double currentLat = DistanceUtils.parseLatitude(currentLoc);
        double currentLon = DistanceUtils.parseLongitude(currentLoc);
        String destination = route.getDestination(); // 终点格式："纬度,经度"
        double destLat = DistanceUtils.parseLatitude(destination);
        double destLon = DistanceUtils.parseLongitude(destination);

        // 2. 计算当前位置到终点的经纬度总差值
        double latDiff = destLat - currentLat; // 纬度差值（正数表示向北移动，负数向南）
        double lonDiff = destLon - currentLon; // 经度差值（正数表示向东移动，负数向西）

        // 3. 定义移动步长
        // 计算当前位置到终点的总距离（公里）
        double totalDistance = DistanceUtils.calculateDistance(currentLat, currentLon, destLat, destLon);
        // 假设车辆速度为60公里/小时，定时任务每1秒执行一次
        double step = calculateStep(60, 1, totalDistance);

        // 4. 计算下一个位置的经纬度（当前位置 + 步长*总差值）
        double nextLat = currentLat + latDiff * step;
        double nextLon = currentLon + lonDiff * step;

        // 5. 边界处理：如果下一步超过终点，则直接到达终点
        // 判断是否已接近终点（步长内可到达）
        boolean isNearLat = Math.abs(nextLat - destLat) < Math.abs(latDiff * step);
        boolean isNearLon = Math.abs(nextLon - destLon) < Math.abs(lonDiff * step);
        if (isNearLat && isNearLon) {
            nextLat = destLat;
            nextLon = destLon;
        }

        // 6. 格式化经纬度为字符串（保留6位小数，精度约10厘米）
        return String.format("%.6f,%.6f", nextLat, nextLon);
    }

    /**
     * 按速度和时间间隔计算步长（更真实的移动模拟）
     * @param speed 车辆速度（单位：公里/小时）
     * @param interval 模拟时间间隔（单位：秒，如定时任务每1秒执行一次）
     * @param totalDistance 总距离（公里，从当前位置到终点）
     * @return 步长（总距离的比例）
     */
    private double calculateStep(double speed, int interval, double totalDistance) {
        if (totalDistance <= 0) return 1.0; // 已到达终点
        // 计算在interval秒内车辆能行驶的距离（公里）
        double distancePerStep = speed * (interval / 3600.0);
        // 步长 = 每步行驶距离 / 总距离
        return Math.min(distancePerStep / totalDistance, 1.0); // 步长不超过1（避免超过终点）
    }

    private boolean isArrived(String currentLoc, String destination) {
        return currentLoc.contains(destination);
    }

    private void broadcastTruckStatus(List<Truck> trucks) {
        try {
            String json = new ObjectMapper().writeValueAsString(trucks);
            VehicleSimulationSocket.broadcast(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //基于起始点实现路径选择
    private Route getOptimalRoute(String start, String destination) {
        // 从数据库查询符合条件的路径（需实现RouteMapper）
        List<Route> candidateRoutes = routeMapper.selectByStartAndDestination(start, destination);
        // 筛选通行状态为"1"（可通行）的路径，并按时间成本排序
        return candidateRoutes.stream()
                .filter(route -> "1".equals(route.getPassable()))
                .min(Comparator.comparing(Route::getTimeCost))
                .orElse(null);
    }



    /**
     * 向类型匹配且距离最近的车分配任务
     */
    public void assignTasks() {
        while (!pendingTasks.isEmpty()) {
            Task task = pendingTasks.poll();

            // 1. 解析任务起点的经纬度（从task.start字段提取）
            String taskStartStr = task.getStart(); // 格式："纬度,经度"
            double taskStartLat = DistanceUtils.parseLatitude(taskStartStr);
            double taskStartLon = DistanceUtils.parseLongitude(taskStartStr);

            // 2. 筛选类型匹配的空闲车辆
            List<Truck> idleTrucks = truckMapper.selectByStatus("空闲");
            List<Truck> matchedTrucks = idleTrucks.stream()
                    .filter(truck -> isTruckMatchTask(truck, task))
                    .collect(Collectors.toList());

            if (matchedTrucks.isEmpty()) {
                pendingTasks.offer(task);
                continue;
            }

            // 3. 计算匹配车辆与任务起点的距离，并按距离排序
            List<Truck> sortedTrucks = matchedTrucks.stream()
                    .map(truck -> {
                        // 解析车辆位置的经纬度（从truck.location字段提取）
                        String truckLocStr = truck.getLocation(); // 格式："纬度,经度"
                        double truckLat = DistanceUtils.parseLatitude(truckLocStr);
                        double truckLon = DistanceUtils.parseLongitude(truckLocStr);

                        // 计算距离
                        double distance = DistanceUtils.calculateDistance(
                                truckLat, truckLon,
                                taskStartLat, taskStartLon
                        );
                        truck.setDistanceToTask(distance); // 临时存储距离用于排序
                        return truck;
                    })
                    .sorted(Comparator.comparingDouble(Truck::getDistanceToTask)) // 按距离升序
                    .collect(Collectors.toList());

            // 4. 分配给最近的车辆
            Truck nearestTruck = sortedTrucks.get(0);
            nearestTruck.setStatus("运输中");
            truckMapper.updateByPrimaryKey(nearestTruck);

            task.setTruckId(nearestTruck.getId());
            taskMapper.updateTaskTruckId(task);
        }
    }

    // 检验车辆与任务是否匹配
    private boolean isTruckMatchTask(Truck truck, Task task) {
        String truckType = truck.getType().toString();
        String goodsType = task.getGoodsType().toString();
        boolean typeMatch=( (truckType.equals("冷藏车") && goodsType.equals("易腐货物")) ||
                (truckType.equals("危险品车") && goodsType.equals("危险品")) ||
                (truckType.equals("板车") && goodsType.equals("普通货物"))||
                (truckType.equals("厢式车") && goodsType.equals("普通货物"))||
                (truckType.equals("仓栅式车") && goodsType.equals("需通风货物"))||
                (truckType.equals("罐式车") && goodsType.equals("液体粉末"))||
                (truckType.equals("自卸车") && goodsType.equals("散装重物"))||
                (truckType.equals("集装箱货车") && goodsType.equals("大宗货物"))||
                truckType.equals("厢式车") ); // 厢式车默认匹配多数货物

        // 2. 重量和体积匹配（车辆载重≥货物重量，车辆容积≥货物体积）
        boolean weightMatch = truck.getMaxWeight().compareTo(task.getWeight()) >= 0;
        boolean volumeMatch = truck.getMaxVol().compareTo(task.getVolume()) >=0 ;

        // 3. 所有条件满足才匹配
        return typeMatch && weightMatch && volumeMatch;
    }


    // 每30秒尝试分配一次任务，已在syncUnassignedTasksFromDb中执行
/*    @Scheduled(fixedRate = 30000)
    public void scheduleTaskAssignment() {
        assignTasks();
    }*/



    // todo  暂时没用到
    // 车辆故障时重新分配任务
    public void handleTruckFailure(Integer truckId) {
        Truck truck = truckMapper.selectByPrimaryKey(truckId.longValue());
        truck.setStatus("故障");
        truckMapper.updateByPrimaryKey(truck);

        // 将原任务放回待分配队列
        Task task = taskMapper.selectByTruckId(truckId);
        if (task != null) {
            task.setTruckId(null);
            taskMapper.updateTaskTruckId(task);
            pendingTasks.offer(task);
        }
    }


    // todo 暂时没用到
    //更新车辆状态
    public void updateStatus(Integer truckId) {
        Truck truck = truckMapper.selectByPrimaryKey(truckId.longValue());
        if (truck == null) return;

        switch (truck.getStatus().toString()) {
            case "装货":
                truck.setStatus("运输中");
                break;
            case "运输中":
                truck.setStatus("卸货");
                break;
            case "卸货":
                truck.setStatus("空闲");
                // 清除任务关联
                Task task = taskMapper.selectByTruckId(truckId);
                if (task != null) {
                    task.setTruckId(null);
                    taskMapper.updateTaskTruckId(task);
                }
                break;
            // TODO 其他状态转换...
        }
        truckMapper.updateByPrimaryKey(truck);
    }

}
