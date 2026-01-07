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

    // 存储车辆状态倒计时（Key: truckId, Value: 剩余周期数）
    private final Map<Integer, Integer> truckWaitCounters = new java.util.concurrent.ConcurrentHashMap<>();

    // 仿真运行状态控制
    private volatile boolean isSimulationRunning = true;

    public void startSimulation() {
        this.isSimulationRunning = true;
        log.info("仿真已启动/恢复");
    }

    public void pauseSimulation() {
        this.isSimulationRunning = false;
        log.info("仿真已暂停");
    }

    public boolean isSimulationRunning() {
        return isSimulationRunning;
    }

    private final Queue<Task> pendingTasks = new ConcurrentLinkedQueue<>();

    public void addPendingTask(Task task) {
        pendingTasks.add(task);
    }


    // 定时任务：每隔 X 毫秒执行一次
    // 加快任务分配频率：每1秒检查一次
    @Scheduled(fixedRate = 1000)
    public void syncUnassignedTasksFromDb() {
        // 1. 查询数据库中 truck_id 为 null 的所有未分配任务
        // 建议在SQL层面加LIMIT，或者这里做截断，防止一次性处理太多导致API超限
        List<Task> unassignedTasks = taskMapper.findByTruckIdIsNull();

        // 2. 清空原有 pendingTasks（覆盖式核心步骤）
        pendingTasks.clear();

        // 3. 将新查询到的未分配任务批量存入队列（限制每次最多处理5个，避免高德API阻塞）
        if (!unassignedTasks.isEmpty()) {
            int limit = Math.min(unassignedTasks.size(), 5);
            List<Task> batchTasks = unassignedTasks.subList(0, limit);
            pendingTasks.addAll(batchTasks);
            log.info("执行任务分配，当前积压: {}, 本次处理: {}", unassignedTasks.size(), batchTasks.size());

            //4. 执行任务分配
            assignTasks();
        }


    }

    // 可选：服务启动时立即同步一次（避免等待第一个定时周期）
    // @PostConstruct
    // public void initSync() {
    //     try {
    //         syncUnassignedTasksFromDb();
    //     } catch (Exception e) {
    //         log.error("启动时同步任务失败", e);
    //     }
    // }


    /**
     * 向类型匹配且距离最近的车分配任务
     */
    public void assignTasks() {
        // 使用临时列表避免死循环
        List<Task> tasksToProcess = new ArrayList<>(pendingTasks);
        pendingTasks.clear(); // 清空队列，未匹配的会重新加入

        for (Task task : tasksToProcess) {
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
                // 降低日志级别或频率，避免刷屏
                // log.debug("任务{}暂时无法分配：没有匹配的空闲车辆", task.getId());
                
                pendingTasks.offer(task); // 放回队列等待下次尝试
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
            // 初始状态设为装货，模拟装货过程（持续3个周期约15秒）
            nearestTruck.setStatus(TruckStatusConstant.LOADING);
            truckWaitCounters.put(nearestTruck.getId(), 3);
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
            
            log.info("任务{}已分配给车辆{}", task.getId(), nearestTruck.getId());
        }
        
        // 如果有未分配的任务，打印一条汇总日志而不是每条都打印
        if (!pendingTasks.isEmpty()) {
            log.info("本轮分配结束，剩余{}个任务等待分配车辆", pendingTasks.size());
        }
    }

    // 检验车辆与任务是否匹配
    private boolean isTruckMatchTask(Truck truck, Task task) {
        String truckType = truck.getType().toString();
        String goodsType = task.getGoodsType().toString();
        boolean typeMatch=( (truckType.equals(TruckTypeConstant.REFRIGERATED_TRUCK) && goodsType.equals(GoodsTypeConstant.PERISHABLE_GOODS)) ||
                (truckType.equals(TruckTypeConstant.DANGEROUS_GOODS_TRUCK) && goodsType.equals(GoodsTypeConstant.DANGEROUS_GOODS)) ||
                (truckType.equals(TruckTypeConstant.PALLET_TRUCK) && (goodsType.equals(GoodsTypeConstant.GENERAL_GOODS) || goodsType.equals(GoodsTypeConstant.BULK_HEAVY_GOODS)))||
                (truckType.equals(TruckTypeConstant.VAN) && goodsType.equals(GoodsTypeConstant.GENERAL_GOODS))||
                (truckType.equals(TruckTypeConstant.WAREHOUSE_TRUCK) && goodsType.equals(GoodsTypeConstant.GOODS_REQUIREING_VENTILATION))||
                (truckType.equals(TruckTypeConstant.TANK_TRUCK) && goodsType.equals(GoodsTypeConstant.LIQUID_POWDERS))||
                (truckType.equals(TruckTypeConstant.DUMP_TRUCK) && goodsType.equals(GoodsTypeConstant.BULK_HEAVY_GOODS))||
                (truckType.equals(TruckTypeConstant.CONTAINER_TRUCK) && (goodsType.equals(GoodsTypeConstant.BULK_GOODS) || goodsType.equals(GoodsTypeConstant.GENERAL_GOODS)))||
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






    // 每5秒更新一次车辆位置（仿真移动）
    // 加快刷新频率：每0.5秒执行一次，使移动更流畅
    @Scheduled(fixedRate = 500)
    public void simulateMovement() {
        // 如果仿真暂停，则跳过本次执行
        if (!isSimulationRunning) {
            return;
        }

        List<Truck> trucks = truckMapper.selectAll();
        Random random = new Random();
        
        log.info("开始执行车辆移动仿真，当前车辆总数：{}", trucks.size());

        for (Truck truck : trucks) {
            String status = truck.getStatus().toString();
            Integer truckId = truck.getId();
            
            // 每次循环都查询当前车辆关联的任务ID，确保前端能获取到
            Task currentTask = taskMapper.selectByTruckId(truckId);
            if (currentTask != null) {
                truck.setTaskId(currentTask.getId());
            }

            // 启用日志以便调试
            log.info("车辆 {} 状态: {}, 倒计时: {}", truckId, status, truckWaitCounters.get(truckId));

            // 1. 处理需等待的状态（装货、卸货、拥堵）
            if (truckWaitCounters.containsKey(truckId)) {
                int remaining = truckWaitCounters.get(truckId);
                remaining--;
                log.info("车辆 {} 等待中，剩余周期: {}", truckId, remaining);
                
                if (remaining > 0) {
                    truckWaitCounters.put(truckId, remaining);
                    // 状态不变，只更新计数器
                } else {
                    truckWaitCounters.remove(truckId);
                    // 等待结束，状态流转
                    if (TruckStatusConstant.LOADING.equals(status)) {
                        truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                        truckMapper.updateByPrimaryKey(truck);
                        status = TruckStatusConstant.IN_TRANSIT; // 更新当前局部变量状态
                        log.info("车辆 {} 装货完成，开始运输", truckId);
                    } else if (TruckStatusConstant.UNLOADING.equals(status)) {
                        // 卸货完成，任务结束
                        Task task = taskMapper.selectByTruckId(truckId);
                        if (task != null) {
                            task.setStatus(TaskStatusConstant.COMPLETED);
                            taskMapper.updateStatus(task);
                            log.info("任务{}已完成", task.getId());
                        }
                        truck.setStatus(TruckStatusConstant.IDLE);
                        truck.setCurrentPointSequence(null);
                        truckMapper.updateByPrimaryKey(truck);
                        log.info("车辆 {} 卸货完成，恢复空闲", truckId);
                        continue;
                    } else if (TruckStatusConstant.TRAFFIC_JAM.equals(status)) {
                        truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                        truckMapper.updateByPrimaryKey(truck);
                        status = TruckStatusConstant.IN_TRANSIT;
                        log.info("车辆 {} 拥堵结束，恢复运输", truckId);
                    }
                }
                // 无论是继续等待还是状态切换，本周期都不移动
                // 但需要推送到前端，让前端知道还在装/卸/堵
                continue;
            } else {
                // 异常状态恢复机制：如果车辆状态是需要等待的（拥堵/装卸货），但没有倒计时器（可能是服务重启导致丢失）
                // 则自动恢复为正常状态或直接完成当前动作
                if (TruckStatusConstant.TRAFFIC_JAM.equals(status)) {
                    log.warn("车辆 {} 处于拥堵状态但无倒计时，自动恢复运输", truckId);
                    truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                    truckMapper.updateByPrimaryKey(truck);
                    status = TruckStatusConstant.IN_TRANSIT;
                } else if (TruckStatusConstant.LOADING.equals(status)) {
                     log.warn("车辆 {} 处于装货状态但无倒计时，自动开始运输", truckId);
                     truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                     truckMapper.updateByPrimaryKey(truck);
                     status = TruckStatusConstant.IN_TRANSIT;
                } else if (TruckStatusConstant.UNLOADING.equals(status)) {
                     log.warn("车辆 {} 处于卸货状态但无倒计时，自动完成卸货", truckId);
                     // 卸货完成逻辑
                     Task task = taskMapper.selectByTruckId(truckId);
                     if (task != null) {
                         task.setStatus(TaskStatusConstant.COMPLETED);
                         taskMapper.updateStatus(task);
                     }
                     truck.setStatus(TruckStatusConstant.IDLE);
                     truck.setCurrentPointSequence(null);
                     truckMapper.updateByPrimaryKey(truck);
                     continue;
                }
            }

            if (TruckStatusConstant.IN_TRANSIT.equals(status)) {
                // 模拟交通拥堵（降低概率到2%）
                if (random.nextDouble() < 0.02) {
                    truck.setStatus(TruckStatusConstant.TRAFFIC_JAM);
                    truckWaitCounters.put(truckId, 2); // 拥堵10秒
                    truckMapper.updateByPrimaryKey(truck);
                    log.info("车辆 {} 遭遇交通拥堵", truckId);
                    continue;
                }

                // 获取车辆当前任务
                Task task = taskMapper.selectByTruckId(truck.getId());
                if (task == null) {
                    truck.setStatus(TruckStatusConstant.IDLE);
                    truckMapper.updateByPrimaryKey(truck);
                    log.warn("车辆 {} 状态为运输中但无任务，重置为空闲", truckId);
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

                // 改进移动逻辑：基于实际距离计算步长，保证速度均匀
                // 调整速度：为了演示效果，将仿真速度提升至约 1000km/h (3倍速)
                // 时间间隔：0.5秒 (fixedRate=500)
                // 目标移动距离：150米/0.5秒 (即 300米/秒 = 1080km/h)
                double targetDistance = 150.0; 
                
                // 查找累积距离达到目标距离的点
                int targetSequence = currentSequence;
                double accumulatedDistance = 0;
                
                // 从当前点开始向后遍历
                for (int i = currentSequence; i < routePoints.size(); i++) {
                    TaskRoutePoint p1 = routePoints.get(i - 1);
                    TaskRoutePoint p2 = routePoints.get(i);
                    
                    String loc1 = p1.getPointLocation();
                    String loc2 = p2.getPointLocation();
                    
                    double dist = DistanceUtils.calculateDistance(
                        DistanceUtils.parseLatitude(loc1), DistanceUtils.parseLongitude(loc1),
                        DistanceUtils.parseLatitude(loc2), DistanceUtils.parseLongitude(loc2)
                    ) * 1000; // 转换为米
                    
                    accumulatedDistance += dist;
                    targetSequence = i + 1; // 更新目标序号
                    
                    if (accumulatedDistance >= targetDistance) {
                        break;
                    }
                }
                
                // 如果已经到达终点或者剩余距离不足，直接移动到终点
                if (targetSequence >= routePoints.size()) {
                    targetSequence = routePoints.size();
                }

                if (currentSequence <= routePoints.size()) {
                    TaskRoutePoint currentPoint = routePoints.get(targetSequence - 1);
                    String location=currentPoint.getPointLocation();
                    
                    log.info("车辆 {} 移动到: {} (seq: {} -> {}, dist: {}m)", truckId, location, currentSequence, targetSequence, (int)accumulatedDistance);
                    
                    truck.setLocation(location);  // 更新车辆位置

                    // 恢复单条推送，但前端需要智能处理
                    try {
                        Map<String, Object> message = new HashMap<>();
                        message.put("truckId", truck.getId());
                        message.put("location", location); 
                        message.put("status", status); // 显式推送状态
                        message.put("plateNumber", truck.getType()); // 暂时用车类型当车牌显示
                        
                        message.put("timestamp", new Date());
                        // 修正：VehicleSimulationSocket.broadcast 接受字符串
                        VehicleSimulationSocket.broadcast(new ObjectMapper().writeValueAsString(message));
                    } catch (Exception e) {
                        log.error("推送位置更新失败", e);
                    }

                    // 更新当前路径点序号
                    if (targetSequence >= routePoints.size()) {
                        // 到达终点 -> 进入卸货状态
                        truck.setStatus(TruckStatusConstant.UNLOADING);
                        truckWaitCounters.put(truckId, 3); // 卸货15秒
                        truckMapper.updateByPrimaryKey(truck);
                        log.info("车辆 {} 到达终点，开始卸货", truckId);
                    } else {
                        truck.setCurrentPointSequence(targetSequence); // 更新为新的序号
                        truckMapper.updateByPrimaryKey(truck);
                    }
                } else {
                    // 序号异常，重置任务
                    task.setStatus(TaskStatusConstant.TO_BE_ASSIGNED);
                    task.setTruckId(null);
                    taskMapper.updateTaskTruckId(task);
                    taskMapper.updateStatus(task);
                    truck.setStatus(TruckStatusConstant.IDLE);
                    truck.setCurrentPointSequence(null);
                    truckMapper.updateByPrimaryKey(truck);
                    log.error("车辆 {} 路径序号异常，重置", truckId);
                }
            } else if (TruckStatusConstant.IDLE.equals(status)) {
                 log.info("车辆 {} 空闲中，等待分配任务", truckId);
            }

        }

        // 推送更新后的车辆信息到前端
        broadcastTruckStatus(trucks);
        log.info("已推送车辆状态更新");
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
        // 修正：输出格式必须为 "经度,纬度" 以匹配高德API标准
        return String.format("%.6f,%.6f", nextLon, nextLat);
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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate; // 注入JdbcTemplate

    /**
     * 随机生成车辆并保存到数据库
     * @param count 生成数量
     * @return 生成的车辆列表
     */
    public List<Truck> generateRandomVehicles(int count) {
        List<Truck> generatedTrucks = new ArrayList<>();
        Random random = new Random();
        
        // 车辆类型列表
        String[] truckTypes = {
            TruckTypeConstant.VAN,
            TruckTypeConstant.REFRIGERATED_TRUCK,
            TruckTypeConstant.DANGEROUS_GOODS_TRUCK,
            TruckTypeConstant.PALLET_TRUCK,
            TruckTypeConstant.WAREHOUSE_TRUCK,
            TruckTypeConstant.TANK_TRUCK,
            TruckTypeConstant.DUMP_TRUCK,
            TruckTypeConstant.CONTAINER_TRUCK
        };

        // 北京周边的经纬度范围
        double minLat = 39.80;
        double maxLat = 40.00;
        double minLon = 116.30;
        double maxLon = 116.50;

        for (int i = 0; i < count; i++) {
            Truck truck = new Truck();
            
            // 随机载重 (10-100吨)，覆盖更多任务需求
            truck.setMaxWeight(BigDecimal.valueOf(10 + random.nextInt(91)));
            
            // 随机体积 (20-150立方米)
            truck.setMaxVol(BigDecimal.valueOf(20 + random.nextInt(131)));
            
            // 状态默认为空闲
            truck.setStatus(TruckStatusConstant.IDLE);
            
            // 随机类型
            truck.setType(truckTypes[random.nextInt(truckTypes.length)]);
            
            // 随机位置
            double lat = minLat + (maxLat - minLat) * random.nextDouble();
            double lon = minLon + (maxLon - minLon) * random.nextDouble();
            // 修正：存储格式必须为 "经度,纬度"
            truck.setLocation(String.format("%.6f,%.6f", lon, lat));
            
            // 随机长度和自重 (简单模拟)
            truck.setLength(BigDecimal.valueOf(4.0 + random.nextDouble() * 10.0)); // 4-14米
            truck.setWeight(BigDecimal.valueOf(5.0 + random.nextDouble() * 10.0)); // 自重5-15吨
            
            // 插入数据库
            truckMapper.insertSelective(truck);
            generatedTrucks.add(truck);
            
            // 自动绑定空闲司机（简单策略：每生成一辆车，尝试绑定一个空闲司机）
            try {
                // 查询一个空闲司机
                String sql = "SELECT id FROM driver WHERE status = '空闲' AND truck_id IS NULL LIMIT 1";
                List<Integer> driverIds = jdbcTemplate.queryForList(sql, Integer.class);
                
                if (!driverIds.isEmpty()) {
                    Integer driverId = driverIds.get(0);
                    // 绑定司机和车辆
                    jdbcTemplate.update("UPDATE driver SET truck_id = ?, status = '驾驶中' WHERE id = ?", truck.getId(), driverId);
                    log.info("车辆 {} 已绑定司机 {}", truck.getId(), driverId);
                } else {
                    // 如果没有空闲司机，则自动创建一个新司机并绑定
                    String randomName = "司机" + System.currentTimeMillis() % 10000;
                    String randomPhone = "138" + String.format("%08d", random.nextInt(100000000));
                    jdbcTemplate.update("INSERT INTO driver (name, phone, license_type, status, truck_id) VALUES (?, ?, 'A2', '驾驶中', ?)", 
                            randomName, randomPhone, truck.getId());
                    log.info("车辆 {} 无空闲司机，已自动创建并绑定新司机", truck.getId());
                }
            } catch (Exception e) {
                log.error("自动绑定司机失败", e);
            }
        }
        
        log.info("已随机生成 {} 辆车", count);
        return generatedTrucks;
    }

}
