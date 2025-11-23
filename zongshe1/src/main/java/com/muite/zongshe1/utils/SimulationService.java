package com.muite.zongshe1.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muite.zongshe1.constant.GoodsTypeConstant;
import com.muite.zongshe1.constant.TaskStatusConstant;
import com.muite.zongshe1.constant.TruckStatusConstant;
import com.muite.zongshe1.constant.TruckTypeConstant;
import com.muite.zongshe1.entity.*;
import com.muite.zongshe1.mapper.RouteMapper;
import com.muite.zongshe1.mapper.TaskMapper;
import com.muite.zongshe1.mapper.TaskRoutePointMapper;
import com.muite.zongshe1.mapper.TruckMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    @Autowired
    TruckMapper truckMapper;
    @Autowired
    TaskMapper taskMapper;
    @Autowired
    RouteMapper routeMapper;

    @Autowired
    AmapTruckRouteService amapTruckRouteService;

    @Autowired
    private TaskRoutePointMapper taskRoutePointMapper; // 新增依赖



    private final Queue<Task> pendingTasks = new ConcurrentLinkedQueue<>();

    public void addPendingTask(Task task) {
        pendingTasks.add(task);
    }


    // 定时任务：每隔 X 毫秒执行一次
    // fixedRate：以上次任务开始时间为基准，固定间隔执行
    @Scheduled(fixedRate = 10000)
    public void syncUnassignedTasksFromDb() {
        // 1. 查询数据库中 truck_id 为 null 的所有未分配任务
        List<Task> unassignedTasks = taskMapper.findByTruckIdIsNull();

        // 2. 清空原有 pendingTasks（覆盖式核心步骤）
        pendingTasks.clear();

        // 3. 将新查询到的未分配任务批量存入队列
        if (!unassignedTasks.isEmpty()) {
            pendingTasks.addAll(unassignedTasks);
            log.info("执行任务分配"+pendingTasks.size());

            //4. 执行任务分配
            assignTasks();
        }


    }

    // 可选：服务启动时立即同步一次（避免等待第一个定时周期）
    @PostConstruct
    public void initSync() {
        syncUnassignedTasksFromDb();
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
            List<Truck> idleTrucks = truckMapper.selectByStatus(TruckStatusConstant.IDLE);
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
            nearestTruck.setStatus(TruckStatusConstant.IN_TRANSIT);
            truckMapper.updateByPrimaryKey(nearestTruck);

            // 5. 规划路线并保存到任务中（新增逻辑）
            Route route = getOptimalRoute(task.getStart(), task.getDestination(), nearestTruck);
            if (route == null) {
                // 路线规划失败，任务放回队列
                log.warn("任务{}路线规划失败，放回队列", task.getId());
                pendingTasks.offer(task);
                // 恢复车辆状态为空闲
                nearestTruck.setStatus(TruckStatusConstant.IDLE);
                truckMapper.updateByPrimaryKey(nearestTruck);
                continue;
            }
            // 获取路径点列表（假设返回List<Point>）
            List<Point> routePoints = amapTruckRouteService.getTruckRoute(
                    task.getStart(), task.getDestination(), nearestTruck).getPathPoints();

            // 转换为TaskRoutePoint并批量插入关联表
            List<TaskRoutePoint> points = new ArrayList<>();
            for (int i = 0; i < routePoints.size(); i++) {
                Point p = routePoints.get(i);
                points.add(new TaskRoutePoint(
                        task.getId(), // 关联当前任务ID
                        p.getLocation(),
                        p.getName(),
                        i + 1 // 顺序从1开始
                ));
            }
            taskRoutePointMapper.batchInsert(points); // 保存到关联表

            task.setTruckId(nearestTruck.getId());
            task.setStatus(TaskStatusConstant.IN_TRANSIT);
            taskMapper.updateTaskTruckId(task);
            taskMapper.updateStatus(task);

        }
    }

    // 检验车辆与任务是否匹配
    private boolean isTruckMatchTask(Truck truck, Task task) {
        String truckType = truck.getType().toString();
        String goodsType = task.getGoodsType().toString();
        boolean typeMatch=( (truckType.equals(TruckTypeConstant.REFRIGERATED_TRUCK) && goodsType.equals(GoodsTypeConstant.PERISHABLE_GOODS)) ||
                (truckType.equals(TruckTypeConstant.DANGEROUS_GOODS_TRUCK) && goodsType.equals(GoodsTypeConstant.DANGEROUS_GOODS)) ||
                (truckType.equals(TruckTypeConstant.PALLET_TRUCK) && goodsType.equals(GoodsTypeConstant.GENERAL_GOODS))||
                (truckType.equals(TruckTypeConstant.VAN) && goodsType.equals(GoodsTypeConstant.GENERAL_GOODS))||
                (truckType.equals(TruckTypeConstant.WAREHOUSE_TRUCK) && goodsType.equals(GoodsTypeConstant.GOODS_REQUIREING_VENTILATION))||
                (truckType.equals(TruckTypeConstant.TANK_TRUCK) && goodsType.equals(GoodsTypeConstant.LIQUID_POWDERS))||
                (truckType.equals(TruckTypeConstant.DUMP_TRUCK) && goodsType.equals(GoodsTypeConstant.BULK_HEAVY_GOODS))||
                (truckType.equals(TruckTypeConstant.CONTAINER_TRUCK) && goodsType.equals(GoodsTypeConstant.BULK_GOODS))||
                truckType.equals(TruckTypeConstant.VAN) ); // 厢式车默认匹配多数货物

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






    // 每?秒更新一次车辆位置（仿真移动）
    @Scheduled(fixedRate = 5000)
    public void simulateMovement() {
        List<Truck> trucks = truckMapper.selectAll();
        for (Truck truck : trucks) {
            if (TruckStatusConstant.IN_TRANSIT.equals(truck.getStatus())) {
                // 获取车辆当前任务
                Task task = taskMapper.selectByTruckId(truck.getId());
                if (task == null) {
                    truck.setStatus(TruckStatusConstant.IDLE);
                    truckMapper.updateByPrimaryKey(truck);
                    continue;
                }

                // 从关联表查询路径点（按顺序）
                List<TaskRoutePoint> routePoints = taskRoutePointMapper.selectByTaskId(task.getId());
                // 处理路线点为空或索引异常的情况
                if (routePoints.isEmpty()) {
                    handleEmptyRoutePoints(task, truck);
                    continue;
                }


                // 按序号排序路径点
                routePoints.sort(Comparator.comparingInt(TaskRoutePoint::getSequence));
                Integer currentSequence = truck.getCurrentPointSequence();
                if (currentSequence == null) currentSequence = 1;

                // 移动到下一个路径点
                int step=1;
                if (currentSequence <= routePoints.size()) {
                    // 计算当前要移动到的路径点索引（避免超出范围）
                    int targetSequence = Math.min(currentSequence + step - 1, routePoints.size());
                    TaskRoutePoint currentPoint = routePoints.get(targetSequence - 1);
                    String location=currentPoint.getPointLocation();
                    String newLocation=location.split(",")[1]+","+location.split(",")[0];
                    truck.setLocation(newLocation);  // 更新车辆位置

                    // 推送位置更新到前端
                    try {
                        Map<String, Object> message = new HashMap<>();
                        message.put("truckId", truck.getId());
                        message.put("location", currentPoint.getPointLocation());
                        message.put("timestamp", new Date());
                        VehicleSimulationSocket.broadcast(new ObjectMapper().writeValueAsString(message));
                    } catch (Exception e) {
                        log.error("推送位置更新失败", e);
                    }

                    // 更新当前路径点序号
                    if (currentSequence == routePoints.size()) {
                        // 到达终点，任务完成
                        task.setStatus(TaskStatusConstant.COMPLETED);
                        taskMapper.updateStatus(task);
                        truck.setStatus(TruckStatusConstant.IDLE);
                        truck.setCurrentPointSequence(null);  // 重置序号
                        log.info("任务{}已完成", task.getId());
                    } else {
                        truck.setCurrentPointSequence(currentSequence + step);
                    }
                    truckMapper.updateByPrimaryKey(truck);
                } else {
                    // 序号异常，重置任务
                    task.setStatus(TaskStatusConstant.TO_BE_ASSIGNED);
                    task.setTruckId(null);
                    taskMapper.updateTaskTruckId(task);
                    taskMapper.updateStatus(task);
                    truck.setStatus(TruckStatusConstant.IDLE);
                    truck.setCurrentPointSequence(null);
                    truckMapper.updateByPrimaryKey(truck);
                }
            }

        }

        // 推送更新后的车辆信息到前端
        broadcastTruckStatus(trucks);
    }

    //基于起始点实现路径选择
    private Route getOptimalRoute(String start, String destination,Truck truck) {
        TruckRoute amapRoute = amapTruckRouteService.getTruckRoute(start, destination, truck);
        if (amapRoute.isSuccess()) {
            // 3. 将高德返回的路径转换为项目中的Route实体
            Route route = new Route();
            route.setStart(start);
            route.setDestination(destination);
            route.setTimeCost(new BigDecimal(amapRoute.getDuration()));  // 时间成本（秒）
            route.setPassable("1");  // 标记为可通行
            // 可额外保存路径点到数据库（可选）
            return route;
        }else {
            // 4. API调用失败时的处理（仅依赖高德，不查数据库）
            log.error("高德货车路径规划失败：{}，起点：{}，终点：{}",
                    amapRoute.getErrorMsg(), start, destination);

            // 方案1：返回null，由调用方处理“无路径”场景
            return null;

            // 方案2：抛出异常，强制中断流程（适用于必须有路径才能继续的场景）
            // throw new RuntimeException("路径规划失败：" + amapRoute.getErrorMsg());
        }
    }

    // 处理路径点为空的异常情况
    private void handleEmptyRoutePoints(Task task, Truck truck) {
        log.error("任务{}路径点为空，标记为异常", task.getId());
        // 1. 标记任务为路线异常
        task.setStatus(TaskStatusConstant.TO_BE_ASSIGNED);
        taskMapper.updateStatus(task);

        // 2. 车辆恢复空闲
        truck.setStatus(TruckStatusConstant.IDLE);
        truck.setCurrentPointSequence(null);
        truckMapper.updateByPrimaryKey(truck);

        // 3. 解除任务与车辆关联
        task.setTruckId(null);
        taskMapper.updateTaskTruckId(task);
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






    // todo  暂时没用到
    // 车辆故障时重新分配任务
    public void handleTruckFailure(Integer truckId) {
        Truck truck = truckMapper.selectByPrimaryKey(truckId.longValue());
        truck.setStatus(TruckStatusConstant.MALFUNCTION);
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
            case TruckStatusConstant.LOADING:
                truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                break;
            case TruckStatusConstant.IN_TRANSIT:
                truck.setStatus(TruckStatusConstant.UNLOADING);
                break;
            case TruckStatusConstant.UNLOADING:
                truck.setStatus(TruckStatusConstant.IDLE);
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
