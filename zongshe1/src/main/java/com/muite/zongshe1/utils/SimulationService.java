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
import com.muite.zongshe1.mapper.AlertLogMapper;
import com.muite.zongshe1.service.GoodsService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.Stack;

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
    private TaskRoutePointMapper taskRoutePointMapper;
    @Autowired
    private AlertLogMapper alertLogMapper;
    
    @Autowired
    private RouteSimplifier routeSimplifier;
    
    @Autowired
    private GoodsService goodsService;
    
    @Autowired
    @Lazy
    private OptimizationService optimizationService;

    private final Map<Integer, Integer> truckWaitCounters = new java.util.concurrent.ConcurrentHashMap();

    // 获取车辆等待计数器
    public Map<Integer, Integer> getTruckWaitCounters() {
        return truckWaitCounters;
    }

    // 获取车辆总里程
    public Map<Integer, Double> getTruckTotalDistance() {
        return truckTotalDistance;
    }

    private final Random random = new Random();

    private final Map<Integer, Long> lastLossCheckTime = new HashMap<>();

    private volatile boolean isSimulationRunning = true;
    
    // 仿真速度倍数（默认1倍）
    private volatile int simulationSpeed = 1;

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
    
    public void setSimulationSpeed(int speed) {
        int oldSpeed = this.simulationSpeed;
        
        if (oldSpeed != speed) {
            // 先更新速度，再同步车辆（resyncAllMovingTrucks需要使用新速度计算）
            this.simulationSpeed = speed;
            resyncAllMovingTrucks(oldSpeed, speed);
            
            // 广播速度变更事件给所有前端
            broadcastSpeedChangeEvent(oldSpeed, speed);
            
            log.info("仿真速度已调整为{}倍", speed);
        }
    }
    
    private void broadcastSpeedChangeEvent(int oldSpeed, int newSpeed) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("eventType", "SPEED_CHANGED");
            message.put("oldSpeed", oldSpeed);
            message.put("newSpeed", newSpeed);
            message.put("timestamp", System.currentTimeMillis());
            
            VehicleSimulationSocket.broadcast(new ObjectMapper().writeValueAsString(message));
            log.info("广播速度变更事件: {}x -> {}x", oldSpeed, newSpeed);
        } catch (Exception e) {
            log.error("广播速度变更事件失败", e);
        }
    }
    
    private void resyncAllMovingTrucks(int oldSpeed, int newSpeed) {
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<Integer, Long> entry : truckMovementStartTime.entrySet()) {
            Integer truckId = entry.getKey();
            Long startTime = entry.getValue();
            Double totalDistance = truckTotalDistance.get(truckId);
            List<TaskRoutePoint> routePoints = truckRouteCache.get(truckId);
            
            if (startTime != null && totalDistance != null && routePoints != null && totalDistance > 0) {
                long elapsed = currentTime - startTime;
                double speedKmh = 600.0 * oldSpeed;
                double speedMs = speedKmh * 1000 / 3600;
                double traveledDistance = speedMs * (elapsed / 1000.0);
                double progress = Math.min(traveledDistance / (totalDistance * 1000), 1.0);
                
                if (progress < 1.0) {
                    String currentLocation = interpolatePosition(routePoints, progress);
                    
                    // 关键修复：根据已行驶的进度，倒推一个新的startTime
                    // 使得在新速度下，从新的startTime开始计算，车辆会处于相同的位置
                    double newSpeedKmh = 600.0 * newSpeed;
                    double newSpeedMs = newSpeedKmh * 1000 / 3600;
                    // 已行驶距离 / 新速度 = 需要的时间（秒）
                    double timeNeededSeconds = traveledDistance / newSpeedMs;
                    // 新的startTime = 当前时间 - 需要的时间
                    long newStartTime = currentTime - (long)(timeNeededSeconds * 1000);
                    
                    truckMovementStartTime.put(truckId, newStartTime);
                    
                    Truck truck = truckMapper.selectByPrimaryKey(truckId.longValue());
                    if (truck != null) {
                        truck.setLocation(currentLocation);
                        truckMapper.updateByPrimaryKey(truck);
                        
                        log.info("车辆 {} 速度从{}x调整到{}x，已行驶{:.2f}%，重新锚定位置: {}", 
                            truckId, oldSpeed, newSpeed, progress * 100, currentLocation);
                    }
                }
            }
        }
    }
    
    public int getSimulationSpeed() {
        return simulationSpeed;
    }

    // 服务启动时恢复车辆进度
    @PostConstruct
    public void restoreTruckProgress() {
        log.info("开始恢复车辆进度...");
        
        List<Truck> trucks = truckMapper.selectAll();
        for (Truck truck : trucks) {
            Integer truckId = truck.getId();
            Integer currentSequence = truck.getCurrentPointSequence();
            
            // 只恢复有进度记录且正在运输或取货的车辆
            if (currentSequence != null && currentSequence != 0) {
                Task task = taskMapper.selectByTruckId(truckId);
                if (task == null) {
                    continue;
                }
                
                String status = truck.getStatus().toString();
                
                if (TruckStatusConstant.IN_TRANSIT.equals(status)) {
                    // 恢复运输状态
                    List<TaskRoutePoint> routePoints = taskRoutePointMapper.selectByTaskId(task.getId());
                    if (!routePoints.isEmpty()) {
                        routePoints.sort(Comparator.comparingInt(TaskRoutePoint::getSequence));
                        
                        // 根据保存的进度计算新的startTime
                        int savedIndex = Math.min(currentSequence, routePoints.size() - 1);
                        double progress = (double) savedIndex / routePoints.size();
                        
                        truckRouteCache.put(truckId, routePoints);
                        truckTotalDistance.put(truckId, calculateTotalRouteDistance(routePoints));
                        
                        // 倒推startTime，使车辆从保存的位置继续
                        double totalDist = truckTotalDistance.get(truckId);
                        double traveledDistance = progress * totalDist * 1000; // 米
                        double speedMs = 600.0 * simulationSpeed * 1000 / 3600;
                        long timeNeededMs = (long) (traveledDistance / speedMs * 1000);
                        long newStartTime = System.currentTimeMillis() - timeNeededMs;
                        
                        truckMovementStartTime.put(truckId, newStartTime);
                        
                        // 更新车辆位置
                        String location = interpolatePosition(routePoints, progress);
                        truck.setLocation(location);
                        truckMapper.updateByPrimaryKey(truck);
                        
                        log.info("车辆 {} 运输进度已恢复，当前位置: {}，进度: {:.2f}%", 
                            truckId, location, progress * 100);
                    }
                } else if (TruckStatusConstant.PICKUP.equals(status)) {
                    // 恢复取货状态
                    List<TaskRoutePoint> routePoints = taskRoutePointMapper.selectByTaskId(task.getId()).stream()
                            .filter(p -> p.getSequence() < 0)
                            .sorted(Comparator.comparingInt(TaskRoutePoint::getSequence))
                            .collect(Collectors.toList());
                    
                    if (!routePoints.isEmpty()) {
                        // 取货路径使用负数索引
                        int savedIndex = Math.min(-currentSequence, routePoints.size() - 1);
                        double progress = (double) savedIndex / routePoints.size();
                        
                        truckRouteCache.put(truckId, routePoints);
                        truckTotalDistance.put(truckId, calculateTotalRouteDistance(routePoints));
                        
                        double totalDist = truckTotalDistance.get(truckId);
                        double traveledDistance = progress * totalDist * 1000;
                        double speedMs = 600.0 * simulationSpeed * 1000 / 3600;
                        long timeNeededMs = (long) (traveledDistance / speedMs * 1000);
                        long newStartTime = System.currentTimeMillis() - timeNeededMs;
                        
                        truckMovementStartTime.put(truckId, newStartTime);
                        
                        String location = interpolatePosition(routePoints, progress);
                        truck.setLocation(location);
                        truckMapper.updateByPrimaryKey(truck);
                        
                        log.info("车辆 {} 取货进度已恢复，当前位置: {}，进度: {:.2f}%", 
                            truckId, location, progress * 100);
                    }
                }
            }
        }
        
        log.info("车辆进度恢复完成，共恢复 {} 辆车", truckMovementStartTime.size());
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
            } else {
                // 没有未分配任务时，也检查一下是否有空闲车辆
                List<Truck> idleTrucks = truckMapper.selectByStatus(TruckStatusConstant.IDLE);
                if (!idleTrucks.isEmpty()) {
                    log.info("当前有{}辆空闲车辆，等待新任务", idleTrucks.size());
                }
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
     * 判断任务是否为蝇头小利（货少里程长）
     * 价值密度 = 货物重量 / 运输里程（公斤/公里）
     * 如果价值密度低于阈值，则认为是蝇头小利
     */
    private boolean isNotWorthwhile(Task task) {
        // 获取任务重量
        double weight = 0;
        if (task.getWeight() != null) {
            weight = task.getWeight().doubleValue();
            log.debug("任务{} weight对象: {}, double值: {}", task.getId(), task.getWeight(), weight);
        } else {
            log.debug("任务{} weight字段为null", task.getId());
        }
        
        // 计算运输里程（从起点到终点的距离）
        String start = task.getStart();
        String destination = task.getDestination();
        double distance = 0;
        if (start != null && destination != null && !start.equals(destination)) {
            double startLat = DistanceUtils.parseLatitude(start);
            double startLon = DistanceUtils.parseLongitude(start);
            double destLat = DistanceUtils.parseLatitude(destination);
            double destLon = DistanceUtils.parseLongitude(destination);
            distance = DistanceUtils.calculateDistance(startLat, startLon, destLat, destLon);
        }
        
        // 如果距离为0，不算蝇头小利
        if (distance <= 0) {
            return false;
        }
        
        // 重量单位是吨，转换为公斤
        double weightInKg = weight * 1000;
        
        // 计算重量密度（公斤/公里）
        double weightDensity = weightInKg / distance;
        
        // 设置阈值：每公里至少运输0.5公斤才值得（可配置）
        double threshold = 0.5;
        
        boolean notWorthwhile = weightDensity < threshold;
        if (notWorthwhile) {
            log.info("任务{}被判定为蝇头小利：重量={}吨(={}kg), 里程={}km, 密度={}kg/km（阈值={}kg/km）", 
                task.getId(), weight, weightInKg, distance, String.format("%.2f", weightDensity), threshold);
        }
        
        return notWorthwhile;
    }

    /**
     * 一车多货任务分配方法
     * 实现逻辑：
     * 1. 过滤蝇头小利任务
     * 2. 为每个车辆分配多个任务（基于剩余容量）
     * 3. 实现先装后卸的装载顺序（使用栈结构）
     */
    public void assignTasks() {
        // 使用临时列表避免死循环
        List<Task> tasksToProcess = new ArrayList<>(pendingTasks);
        pendingTasks.clear(); // 清空队列，未匹配的会重新加入

        // 1. 过滤蝇头小利任务
        List<Task> worthwhileTasks = tasksToProcess.stream()
                .filter(task -> !isNotWorthwhile(task))
                .collect(Collectors.toList());
        
        List<Task> rejectedTasks = tasksToProcess.stream()
                .filter(this::isNotWorthwhile)
                .collect(Collectors.toList());
        
        log.info("本轮任务分配：总数={}, 值得运输={}, 蝇头小利={}", 
            tasksToProcess.size(), worthwhileTasks.size(), rejectedTasks.size());

        // 2. 获取所有空闲车辆
        List<Truck> idleTrucks = truckMapper.selectByStatus(TruckStatusConstant.IDLE);
        log.info("可用空闲车辆数: {}", idleTrucks.size());

        // 3. 为每个车辆分配多个任务
        for (Truck truck : idleTrucks) {
            // 装载栈：用于实现先装后卸
            Stack<Task> loadStack = new Stack<>();
            List<Task> assignedTasks = new ArrayList<>();
            
            // 记录已使用的任务索引，避免重复分配
            Set<Integer> assignedTaskIds = new HashSet<>();

            // 遍历任务，尝试装载
            for (Task task : worthwhileTasks) {
                // 跳过已分配的任务
                if (assignedTaskIds.contains(task.getId())) {
                    continue;
                }

                // 检查类型匹配
                if (!isTruckMatchTask(truck, task)) {
                    continue;
                }

                // 使用 Truck 实体的 canLoad 方法检查容量是否足够
                BigDecimal taskWeight = task.getWeight() != null ? task.getWeight() : BigDecimal.ZERO;
                BigDecimal taskVolume = task.getVolume() != null ? task.getVolume() : BigDecimal.ZERO;
                
                if (truck.canLoad(taskWeight, taskVolume)) {
                    // 装载任务
                    loadStack.push(task);
                    assignedTasks.add(task);
                    assignedTaskIds.add(task.getId());
                    
                    // 使用 Truck 实体的 load 方法更新装载状态
                    truck.load(taskWeight, taskVolume);
                    
                    log.info("车辆{}装载任务{}，剩余载重={:.2f}kg，剩余容积={:.2f}", 
                        truck.getId(), task.getId(), 
                        truck.getRemainingWeight(), truck.getRemainingVolume());
                }
            }

            // 如果该车辆成功装载了任务
            if (!assignedTasks.isEmpty()) {
                log.info("车辆{}共装载{}个任务", truck.getId(), assignedTasks.size());
                
                // 将任务ID添加到车辆的任务栈中
                for (Task task : loadStack) {
                    truck.getTaskStack().push(task.getId());
                }
                
                // 处理装载的任务：按先装后卸顺序规划路线
                processMultiTaskAssignment(truck, loadStack, assignedTasks);
            }
        }

        // 4. 处理未分配的任务（放回队列）
        for (Task task : worthwhileTasks) {
            if (!task.getStatus().equals(TaskStatusConstant.IN_TRANSIT) && task.getTruckId() == null) {
                pendingTasks.offer(task);
            }
        }
        
        // 5. 蝇头小利任务也放回队列（可能后续有更合适的车辆）
        for (Task task : rejectedTasks) {
            pendingTasks.offer(task);
        }
        
        // 如果有未分配的任务，打印一条汇总日志
        if (!pendingTasks.isEmpty()) {
            log.info("本轮分配结束，剩余{}个任务等待分配车辆", pendingTasks.size());
        }
    }

    /**
     * 处理多任务分配：规划路线并更新状态
     */
    private void processMultiTaskAssignment(Truck truck, Stack<Task> loadStack, List<Task> assignedTasks) {
        // 获取车辆当前位置
        String truckLocation = truck.getLocation();
        
        // 构建完整路线：取货路线 -> 运输路线（按先装后卸顺序）
        List<TaskRoutePoint> allRoutePoints = new ArrayList<>();
        int sequenceCounter = 0;

        // 1. 规划取货路线（按装载顺序）
        String currentLocation = truckLocation;
        List<Task> loadingOrder = new ArrayList<>(loadStack); // 装载顺序（栈底到栈顶）
        
        for (int i = 0; i < loadingOrder.size(); i++) {
            Task task = loadingOrder.get(i);
            String taskStart = task.getStart();
            
            if (!currentLocation.equals(taskStart)) {
                // 规划从当前位置到任务起点的路线
                TruckRoute pickupRoute = amapTruckRouteService.getTruckRoute(currentLocation, taskStart, truck);
                if (pickupRoute != null && pickupRoute.isSuccess()) {
                    for (Point p : pickupRoute.getPathPoints()) {
                        allRoutePoints.add(new TaskRoutePoint(
                                task.getId(),
                                p.getLocation(),
                                "取货-" + task.getId() + "-" + (i + 1),
                                sequenceCounter++
                        ));
                    }
                    currentLocation = taskStart;
                }
            }
            
            // 记录取货点
            allRoutePoints.add(new TaskRoutePoint(
                    task.getId(),
                    taskStart,
                    "取货完成-" + task.getId(),
                    sequenceCounter++
            ));
        }

        // 2. 规划运输路线（按先装后卸顺序，即栈顶到栈底）
        Stack<Task> unloadStack = (Stack<Task>) loadStack.clone();
        while (!unloadStack.isEmpty()) {
            Task task = unloadStack.pop();
            String taskDestination = task.getDestination();
            
            if (!currentLocation.equals(taskDestination)) {
                TruckRoute deliveryRoute = amapTruckRouteService.getTruckRoute(currentLocation, taskDestination, truck);
                if (deliveryRoute != null && deliveryRoute.isSuccess()) {
                    for (Point p : deliveryRoute.getPathPoints()) {
                        allRoutePoints.add(new TaskRoutePoint(
                                task.getId(),
                                p.getLocation(),
                                "运输-" + task.getId(),
                                sequenceCounter++
                        ));
                    }
                    currentLocation = taskDestination;
                }
            }
            
            // 记录卸货点
            allRoutePoints.add(new TaskRoutePoint(
                    task.getId(),
                    taskDestination,
                    "卸货完成-" + task.getId(),
                    sequenceCounter++
            ));
            
            // 更新任务状态
            task.setTruckId(truck.getId());
            task.setStatus(TaskStatusConstant.IN_TRANSIT);
            taskMapper.updateTaskTruckId(task);
            taskMapper.updateStatus(task);
            
            if (task.getGoodsId() != null) {
                try {
                    goodsService.assignGoodsToTask(task.getGoodsId(), task.getId(), truck.getId());
                } catch (Exception e) {
                    log.error("货物分配记录失败: {}", e.getMessage());
                }
            }
        }

        // 3. 批量保存路径点
        if (!allRoutePoints.isEmpty()) {
            taskRoutePointMapper.batchInsert(allRoutePoints);
        }

        // 4. 更新车辆状态
        truck.setStatus(TruckStatusConstant.PICKUP);
        truckMapper.updateByPrimaryKey(truck);

        // 5. 初始化车辆移动状态
        truckRouteCache.put(truck.getId(), allRoutePoints);
        truckMovementStartTime.put(truck.getId(), System.currentTimeMillis());
        truckTotalDistance.put(truck.getId(), calculateTotalRouteDistance(allRoutePoints));

        // 6. 推送事件给前端
        broadcastTruckEvent(truck, "MULTI_TASK_ASSIGNED", allRoutePoints);
        log.info("车辆{}多任务分配完成，共{}个任务，路径点{}个", 
            truck.getId(), assignedTasks.size(), allRoutePoints.size());
    }

    // 检验车辆与任务是否匹配（支持一车多货）
    private boolean isTruckMatchTask(Truck truck, Task task) {
        String truckType = truck.getType().toString();
        String goodsType = task.getGoodsType().toString();
        
        // 1. 类型匹配检查
        boolean typeMatch = (truckType.equals(TruckTypeConstant.REFRIGERATED_TRUCK) && goodsType.equals(GoodsTypeConstant.PERISHABLE_GOODS)) ||
                (truckType.equals(TruckTypeConstant.DANGEROUS_GOODS_TRUCK) && goodsType.equals(GoodsTypeConstant.DANGEROUS_GOODS)) ||
                (truckType.equals(TruckTypeConstant.PALLET_TRUCK) && (goodsType.equals(GoodsTypeConstant.GENERAL_GOODS) || goodsType.equals(GoodsTypeConstant.BULK_HEAVY_GOODS) || goodsType.equals(GoodsTypeConstant.BULK_GOODS))) ||
                (truckType.equals(TruckTypeConstant.VAN) && (goodsType.equals(GoodsTypeConstant.GENERAL_GOODS) || goodsType.equals(GoodsTypeConstant.GOODS_REQUIREING_VENTILATION) || goodsType.equals(GoodsTypeConstant.BULK_GOODS) || goodsType.equals(GoodsTypeConstant.PERISHABLE_GOODS))) ||
                (truckType.equals(TruckTypeConstant.WAREHOUSE_TRUCK) && (goodsType.equals(GoodsTypeConstant.GOODS_REQUIREING_VENTILATION) || goodsType.equals(GoodsTypeConstant.GENERAL_GOODS))) ||
                (truckType.equals(TruckTypeConstant.TANK_TRUCK) && goodsType.equals(GoodsTypeConstant.LIQUID_POWDERS)) ||
                (truckType.equals(TruckTypeConstant.DUMP_TRUCK) && (goodsType.equals(GoodsTypeConstant.BULK_HEAVY_GOODS) || goodsType.equals(GoodsTypeConstant.BULK_GOODS))) ||
                (truckType.equals(TruckTypeConstant.CONTAINER_TRUCK) && (goodsType.equals(GoodsTypeConstant.BULK_GOODS) || goodsType.equals(GoodsTypeConstant.GENERAL_GOODS) || goodsType.equals(GoodsTypeConstant.BULK_HEAVY_GOODS) || goodsType.equals(GoodsTypeConstant.PERISHABLE_GOODS)));

        // 2. 重量和体积匹配（一车多货场景：检查剩余容量是否足够）
        BigDecimal taskWeight = task.getWeight() != null ? task.getWeight() : BigDecimal.ZERO;
        BigDecimal taskVolume = task.getVolume() != null ? task.getVolume() : BigDecimal.ZERO;
        
        // 首先检查单个任务是否超过车辆最大容量（防止单个任务超重/超体积）
        boolean singleTaskWeightMatch = truck.getMaxWeight().compareTo(taskWeight) >= 0;
        boolean singleTaskVolumeMatch = truck.getMaxVol().compareTo(taskVolume) >= 0;
        
        // 然后检查车辆剩余容量是否足够装载此任务
        boolean weightMatch = singleTaskWeightMatch && truck.canLoad(taskWeight, BigDecimal.ZERO);
        boolean volumeMatch = singleTaskVolumeMatch && truck.canLoad(BigDecimal.ZERO, taskVolume);

        // 3. 所有条件满足才匹配
        boolean match = typeMatch && weightMatch && volumeMatch;
        if (!match) {
            log.debug("车辆{}类型{}与任务{}货物类型{}不匹配，剩余载重{} vs 重量{}，剩余容积{} vs 体积{}", 
                truck.getId(), truckType, task.getId(), goodsType, 
                truck.getRemainingWeight(), taskWeight, truck.getRemainingVolume(), taskVolume);
        }
        return match;
    }


    // 每30秒尝试分配一次任务，已在syncUnassignedTasksFromDb中执行
/*    @Scheduled(fixedRate = 30000)
    public void scheduleTaskAssignment() {
        assignTasks();
    }*/






    private final Map<Integer, Long> truckMovementStartTime = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Integer, Double> truckTotalDistance = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Integer, List<TaskRoutePoint>> truckRouteCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Scheduled(fixedRate = 1000)
    public void simulateMovement() {
        if (!isSimulationRunning) {
            return;
        }

        List<Truck> trucks = truckMapper.selectAll();
        Random random = new Random();
        
        log.debug("开始执行车辆移动仿真，当前车辆总数：{}", trucks.size());

        for (Truck truck : trucks) {
            String status = truck.getStatus().toString();
            Integer truckId = truck.getId();
            
            Task currentTask = taskMapper.selectByTruckId(truckId);
            if (currentTask != null) {
                truck.setTaskId(currentTask.getId());
            } else {
                truck.setTaskId(null);
            }

            if (truckWaitCounters.containsKey(truckId)) {
                int remaining = truckWaitCounters.get(truckId);
                remaining--;
                
                if (remaining > 0) {
                    truckWaitCounters.put(truckId, remaining);
                    broadcastTruckEvent(truck, "WAITING", null);
                } else {
                    truckWaitCounters.remove(truckId);
                    
                    if (TruckStatusConstant.LOADING.equals(status)) {
                        truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                        truckMapper.updateByPrimaryKey(truck);
                        
                        if (currentTask != null && currentTask.getGoodsId() != null) {
                            try {
                                goodsService.loadGoods(currentTask.getGoodsId());
                            } catch (Exception e) {
                                log.error("货物装载记录失败: {}", e.getMessage());
                            }
                        }
                        
                        List<TaskRoutePoint> routePoints = taskRoutePointMapper.selectByTaskId(truck.getTaskId());
                        if (!routePoints.isEmpty()) {
                            routePoints.sort(Comparator.comparingInt(TaskRoutePoint::getSequence));
                            truckRouteCache.put(truckId, routePoints);
                            truckMovementStartTime.put(truckId, System.currentTimeMillis());
                            truckTotalDistance.put(truckId, calculateTotalRouteDistance(routePoints));
                            
                            truck.setLocation(routePoints.get(0).getPointLocation());
                            truckMapper.updateByPrimaryKey(truck);
                            
                            broadcastTruckEvent(truck, "DEPARTURE", routePoints);
                            log.info("车辆 {} 装货完成，开始运输", truckId);
                        } else {
                            log.warn("车辆 {} 装货完成但无路径点，跳过运输", truckId);
                        }
                    } else if (TruckStatusConstant.UNLOADING.equals(status)) {
                        if (truck.getTaskId() != null) {
                            Task task = new Task();
                            task.setId(truck.getTaskId());
                            task.setStatus(TaskStatusConstant.COMPLETED);
                            taskMapper.updateStatus(task);
                            log.info("任务{}已完成", truck.getTaskId());
                            
                            // 删除已完成任务的路径点
                            try {
                                taskRoutePointMapper.deleteByTaskId(truck.getTaskId());
                                log.info("已删除任务{}的路径点数据", truck.getTaskId());
                            } catch (Exception e) {
                                log.error("删除任务路径点失败: {}", e.getMessage());
                            }
                            
                            if (currentTask != null && currentTask.getGoodsId() != null) {
                                try {
                                    goodsService.deliverGoods(currentTask.getGoodsId());
                                    goodsService.completeGoods(currentTask.getGoodsId());
                                } catch (Exception e) {
                                    log.error("货物送达记录失败: {}", e.getMessage());
                                }
                            }
                            
                            // 从车辆任务栈中移除已完成的任务
                            truck.completeCurrentTask();
                            
                            // 如果还有更多任务，继续处理下一个
                            if (truck.hasMoreTasks()) {
                                Integer nextTaskId = truck.peekNextTask();
                                Task nextTask = taskMapper.selectByPrimaryKey(nextTaskId);
                                if (nextTask != null) {
                                    // 更新车辆当前任务
                                    truck.setTaskId(nextTaskId);
                                    
                                    // 卸载当前货物重量和体积
                                    if (currentTask != null) {
                                        BigDecimal taskWeight = currentTask.getWeight() != null ? currentTask.getWeight() : BigDecimal.ZERO;
                                        BigDecimal taskVolume = currentTask.getVolume() != null ? currentTask.getVolume() : BigDecimal.ZERO;
                                        truck.unload(taskWeight, taskVolume);
                                    }
                                    
                                    log.info("车辆{}继续处理下一个任务{}，任务栈剩余{}个任务", 
                                        truckId, nextTaskId, truck.getTaskStack().size());
                                }
                            }
                        }
                        
                        // 检查是否还有更多任务
                        if (truck.hasMoreTasks()) {
                            // 还有任务，继续运输
                            // 重新获取路线点（可能需要重新规划）
                            List<TaskRoutePoint> remainingRoutePoints = truckRouteCache.get(truckId);
                            if (remainingRoutePoints != null && !remainingRoutePoints.isEmpty()) {
                                // 继续使用当前路线
                                truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                                truckMapper.updateByPrimaryKey(truck);
                                log.info("车辆{}继续运输，剩余{}个任务", truckId, truck.getTaskStack().size());
                            } else {
                                // 路线点已用完，需要重新规划
                                truck.setStatus(TruckStatusConstant.IDLE);
                                truckMapper.updateByPrimaryKey(truck);
                                log.warn("车辆{}任务栈非空但无路线点，重置为空闲状态", truckId);
                            }
                        } else {
                            // 没有更多任务，恢复空闲
                            truck.setStatus(TruckStatusConstant.IDLE);
                            truck.setCurrentPointSequence(null);
                            truck.setTaskId(null);
                            truckMapper.updateByPrimaryKey(truck);
                            
                            truckRouteCache.remove(truckId);
                            truckMovementStartTime.remove(truckId);
                            truckTotalDistance.remove(truckId);
                            
                            broadcastTruckEvent(truck, "ARRIVED", null);
                            log.info("车辆 {} 所有任务完成，恢复空闲", truckId);
                        }
                        continue;
                    } else if (TruckStatusConstant.TRAFFIC_JAM.equals(status)) {
                        truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                        truckMapper.updateByPrimaryKey(truck);
                        broadcastTruckEvent(truck, "JAM_CLEARED", null);
                        log.info("车辆 {} 拥堵结束，恢复运输", truckId);
                    }
                }
                continue;
            } else {
                if (TruckStatusConstant.TRAFFIC_JAM.equals(status)) {
                    truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                    truckMapper.updateByPrimaryKey(truck);
                } else if (TruckStatusConstant.LOADING.equals(status)) {
                    truck.setStatus(TruckStatusConstant.IN_TRANSIT);
                    truckMapper.updateByPrimaryKey(truck);
                } else if (TruckStatusConstant.UNLOADING.equals(status)) {
                    Task task = taskMapper.selectByTruckId(truckId);
                    if (task != null) {
                        task.setStatus(TaskStatusConstant.COMPLETED);
                        taskMapper.updateStatus(task);
                    }
                    truck.setStatus(TruckStatusConstant.IDLE);
                    truck.setCurrentPointSequence(null);
                    truck.setTaskId(null);
                    truckMapper.updateByPrimaryKey(truck);
                    continue;
                }
            }

            if (TruckStatusConstant.IN_TRANSIT.equals(status)) {
                if (random.nextDouble() < 0.02) {
                    truck.setStatus(TruckStatusConstant.TRAFFIC_JAM);
                    truckWaitCounters.put(truckId, 2);
                    truckMapper.updateByPrimaryKey(truck);
                    
                    AlertLog alertLog = new AlertLog();
                    alertLog.setTruckId(truckId);
                    alertLog.setTaskId(truck.getTaskId());
                    alertLog.setType("拥堵");
                    alertLog.setLevel("一般");
                    alertLog.setLocation(truck.getLocation());
                    alertLog.setCreateTime(new Date());
                    alertLog.setIsHandled(false);
                    alertLogMapper.insert(alertLog);
                    
                    broadcastTruckEvent(truck, "TRAFFIC_JAM", null);
                    log.info("车辆 {} 遭遇交通拥堵", truckId);
                    continue;
                }

                Task task = taskMapper.selectByTruckId(truck.getId());
                if (task == null) {
                    truck.setStatus(TruckStatusConstant.IDLE);
                    truckMapper.updateByPrimaryKey(truck);
                    log.warn("车辆 {} 状态为运输中但无任务，重置为空闲", truckId);
                    continue;
                }

                List<TaskRoutePoint> routePoints = truckRouteCache.get(truckId);
                if (routePoints == null) {
                    routePoints = taskRoutePointMapper.selectByTaskId(task.getId());
                    if (routePoints.isEmpty()) {
                        handleEmptyRoutePoints(task, truck);
                        continue;
                    }
                    routePoints.sort(Comparator.comparingInt(TaskRoutePoint::getSequence));
                    truckRouteCache.put(truckId, routePoints);
                    truckMovementStartTime.put(truckId, System.currentTimeMillis());
                    truckTotalDistance.put(truckId, calculateTotalRouteDistance(routePoints));
                }

                Long startTime = truckMovementStartTime.get(truckId);
                Double totalDist = truckTotalDistance.get(truckId);
                
                if (startTime != null && totalDist != null && totalDist > 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    double speedKmh = 600.0 * simulationSpeed;
                    double speedMs = speedKmh * 1000 / 3600;
                    double traveledDistance = speedMs * (elapsed / 1000.0);
                    
                    double progress = Math.min(traveledDistance / (totalDist * 1000), 1.0);
                    
                    if (progress >= 1.0) {
                        TaskRoutePoint lastPoint = routePoints.get(routePoints.size() - 1);
                        truck.setLocation(lastPoint.getPointLocation());
                        truck.setStatus(TruckStatusConstant.UNLOADING);
                        truckWaitCounters.put(truckId, 3);
                        truckMapper.updateByPrimaryKey(truck);
                        
                        broadcastTruckEvent(truck, "UNLOADING_START", null);
                        log.info("车辆 {} 到达终点，开始卸货", truckId);
                    } else {
                        String interpolatedLocation = interpolatePosition(routePoints, progress);
                        truck.setLocation(interpolatedLocation);
                        
                        int currentIndex = (int) (progress * routePoints.size());
                        truck.setCurrentPointSequence(currentIndex);
                        
                        truckMapper.updateByPrimaryKey(truck);
                        
                        if (currentTask != null && currentTask.getGoodsId() != null) {
                            checkAndRecordGoodsLoss(currentTask.getGoodsId(), task, truck);
                        }
                        
                        broadcastTruckEvent(truck, "POSITION_UPDATE", null);
                    }
                }
            } else if (TruckStatusConstant.IDLE.equals(status)) {
                log.debug("车辆 {} 空闲中，等待分配任务", truckId);
            } else if (TruckStatusConstant.PICKUP.equals(status)) {
                // 处理前往取货点的车辆
                Task task = taskMapper.selectByTruckId(truck.getId());
                if (task == null) {
                    truck.setStatus(TruckStatusConstant.IDLE);
                    truckMapper.updateByPrimaryKey(truck);
                    log.warn("车辆 {} 状态为取货中但无任务，重置为空闲", truckId);
                    continue;
                }

                List<TaskRoutePoint> routePoints = truckRouteCache.get(truckId);
                if (routePoints == null) {
                    routePoints = taskRoutePointMapper.selectByTaskId(task.getId()).stream()
                            .filter(p -> p.getSequence() < 0)
                            .sorted(Comparator.comparingInt(TaskRoutePoint::getSequence))
                            .collect(Collectors.toList());
                    if (routePoints.isEmpty()) {
                        log.warn("车辆 {} 无取货路径点，直接开始装货", truckId);
                        truck.setStatus(TruckStatusConstant.LOADING);
                        truckWaitCounters.put(truckId, 3);
                        truckMapper.updateByPrimaryKey(truck);
                        continue;
                    }
                    truckRouteCache.put(truckId, routePoints);
                    truckMovementStartTime.put(truckId, System.currentTimeMillis());
                    truckTotalDistance.put(truckId, calculateTotalRouteDistance(routePoints));
                }

                Long startTime = truckMovementStartTime.get(truckId);
                Double totalDist = truckTotalDistance.get(truckId);
                
                if (startTime != null && totalDist != null && totalDist > 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    double speedKmh = 600.0 * simulationSpeed;
                    double speedMs = speedKmh * 1000 / 3600;
                    double traveledDistance = speedMs * (elapsed / 1000.0);
                    
                    double progress = Math.min(traveledDistance / (totalDist * 1000), 1.0);
                    
                    if (progress >= 1.0) {
                        // 到达取货点，开始装货
                        TaskRoutePoint lastPoint = routePoints.get(routePoints.size() - 1);
                        truck.setLocation(lastPoint.getPointLocation());
                        truck.setStatus(TruckStatusConstant.LOADING);
                        truckWaitCounters.put(truckId, 3);
                        
                        // 清除取货路径缓存，准备运输路径
                        truckRouteCache.remove(truckId);
                        truckMovementStartTime.remove(truckId);
                        truckTotalDistance.remove(truckId);
                        
                        truckMapper.updateByPrimaryKey(truck);
                        
                        broadcastTruckEvent(truck, "ARRIVED_PICKUP", null);
                        log.info("车辆 {} 到达取货点，开始装货", truckId);
                    } else {
                        String interpolatedLocation = interpolatePosition(routePoints, progress);
                        truck.setLocation(interpolatedLocation);
                        
                        // 计算当前路径点索引并保存（取货路径使用负数索引）
                        int currentIndex = (int) (progress * routePoints.size());
                        truck.setCurrentPointSequence(-currentIndex);
                        
                        truckMapper.updateByPrimaryKey(truck);
                        
                        broadcastTruckEvent(truck, "POSITION_UPDATE", null);
                    }
                }
            }
        }
    }
    
    private double calculateTotalRouteDistance(List<TaskRoutePoint> routePoints) {
        double totalDistance = 0;
        for (int i = 1; i < routePoints.size(); i++) {
            TaskRoutePoint p1 = routePoints.get(i - 1);
            TaskRoutePoint p2 = routePoints.get(i);
            
            double dist = DistanceUtils.calculateDistance(
                DistanceUtils.parseLatitude(p1.getPointLocation()), 
                DistanceUtils.parseLongitude(p1.getPointLocation()),
                DistanceUtils.parseLatitude(p2.getPointLocation()), 
                DistanceUtils.parseLongitude(p2.getPointLocation())
            );
            totalDistance += dist;
        }
        return totalDistance;
    }
    
    private String interpolatePosition(List<TaskRoutePoint> routePoints, double progress) {
        if (routePoints.isEmpty()) return "116.397428,39.90923";
        if (progress <= 0) return routePoints.get(0).getPointLocation();
        if (progress >= 1) return routePoints.get(routePoints.size() - 1).getPointLocation();
        
        double totalDistance = calculateTotalRouteDistance(routePoints);
        double targetDistance = totalDistance * progress;
        
        double accumulatedDistance = 0;
        for (int i = 1; i < routePoints.size(); i++) {
            TaskRoutePoint p1 = routePoints.get(i - 1);
            TaskRoutePoint p2 = routePoints.get(i);
            
            double segmentDistance = DistanceUtils.calculateDistance(
                DistanceUtils.parseLatitude(p1.getPointLocation()), 
                DistanceUtils.parseLongitude(p1.getPointLocation()),
                DistanceUtils.parseLatitude(p2.getPointLocation()), 
                DistanceUtils.parseLongitude(p2.getPointLocation())
            );
            
            if (accumulatedDistance + segmentDistance >= targetDistance) {
                double segmentProgress = (targetDistance - accumulatedDistance) / segmentDistance;
                
                double lat1 = DistanceUtils.parseLatitude(p1.getPointLocation());
                double lon1 = DistanceUtils.parseLongitude(p1.getPointLocation());
                double lat2 = DistanceUtils.parseLatitude(p2.getPointLocation());
                double lon2 = DistanceUtils.parseLongitude(p2.getPointLocation());
                
                double interpLat = lat1 + (lat2 - lat1) * segmentProgress;
                double interpLon = lon1 + (lon2 - lon1) * segmentProgress;
                
                return String.format("%.6f,%.6f", interpLon, interpLat);
            }
            
            accumulatedDistance += segmentDistance;
        }
        
        return routePoints.get(routePoints.size() - 1).getPointLocation();
    }
    
    private void broadcastTruckEvent(Truck truck, String eventType, List<TaskRoutePoint> routePoints) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("eventType", eventType);
            message.put("truckId", truck.getId());
            message.put("location", truck.getLocation());
            message.put("status", truck.getStatus().toString());
            message.put("taskId", truck.getTaskId());
            message.put("timestamp", System.currentTimeMillis());
            message.put("simulationSpeed", simulationSpeed);
            
            if (routePoints != null) {
                message.put("routePoints", routePoints);
                message.put("totalDistance", truckTotalDistance.get(truck.getId()));
                message.put("startTime", truckMovementStartTime.get(truck.getId()));
            }
            
            VehicleSimulationSocket.broadcast(new ObjectMapper().writeValueAsString(message));
        } catch (Exception e) {
            log.error("推送车辆事件失败", e);
        }
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
        // 基础速度：60公里/小时，定时任务每1秒执行一次
        // 根据仿真速度倍数动态调整
        double step = calculateStep(60 * simulationSpeed, 1, totalDistance);

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

    @Autowired
    private com.muite.zongshe1.mapper.GoodsMapper goodsMapper;

    private void checkAndRecordGoodsLoss(Integer goodsId, Task task, Truck truck) {
        try {
            long currentTime = System.currentTimeMillis();
            Long lastCheck = lastLossCheckTime.get(goodsId);
            
            if (lastCheck != null && currentTime - lastCheck < 10000) {
                return;
            }
            
            lastLossCheckTime.put(goodsId, currentTime);
            
            double lossProbability = 0.15;
            if (random.nextDouble() < lossProbability) {
                Goods goods = goodsMapper.selectByPrimaryKey(goodsId);
                if (goods == null || goods.getWeight() == null) {
                    return;
                }
                
                String[] lossTypes = {"破损", "丢失", "变质", "受潮", "挤压"};
                String lossType = lossTypes[random.nextInt(lossTypes.length)];
                
                double lossPercentage = 1.0 + random.nextDouble() * 9.0;
                
                BigDecimal originalWeight = goods.getWeight();
                BigDecimal lossWeight = originalWeight.multiply(BigDecimal.valueOf(lossPercentage / 100.0));
                
                BigDecimal originalValue = goods.getValue();
                BigDecimal lossValue = originalValue != null 
                    ? originalValue.multiply(BigDecimal.valueOf(lossPercentage / 100.0)) 
                    : lossWeight.multiply(BigDecimal.valueOf(100));
                
                goodsService.recordLoss(goodsId, lossType, lossWeight, lossValue, truck.getId());
                
                log.info("货物 {} 发生{}损耗: {}% (重量: {}, 价值: {})", 
                    goodsId, lossType, String.format("%.2f", lossPercentage), 
                    lossWeight, lossValue);
            }
        } catch (Exception e) {
            log.error("记录货物损耗失败: {}", e.getMessage());
        }
    }

    public Map<String, Object> getTotalWaitingTime() {
        Map<String, Object> result = new HashMap<>();
        
        List<Truck> allTrucks = truckMapper.selectAll();
        long totalWaitingCycles = 0;
        int totalTrucks = allTrucks.size();
        
        for (Truck truck : allTrucks) {
            Integer waitCounter = truckWaitCounters.get(truck.getId());
            if (waitCounter != null && waitCounter > 0) {
                totalWaitingCycles += waitCounter;
            }
        }
        
        // 等待时间计算：乘以30，单位为分钟
        double waitingMinutes = totalWaitingCycles * 30.0;
        double waitingHours = waitingMinutes / 60.0;
        
        result.put("totalWaitingCycles", totalWaitingCycles);
        result.put("totalWaitingMinutes", waitingMinutes);
        result.put("totalWaitingHours", waitingHours);
        result.put("averageWaitingMinutesPerTruck", totalTrucks > 0 ? waitingMinutes / totalTrucks : 0);
        result.put("totalTrucks", totalTrucks);
        
        log.info("计算等待时间: 总周期={}, 总分钟={}, 总小时={}, 平均每车={}", 
            totalWaitingCycles, waitingMinutes, waitingHours, 
            totalTrucks > 0 ? waitingMinutes / totalTrucks : 0);
        
        return result;
    }

    /**
     * 定时优化任务分配
     * 每5分钟执行一次混合优化算法，对未分配任务进行重新优化
     */
    @Scheduled(fixedRate = 300000) // 5分钟 = 300000毫秒
    public void scheduledOptimization() {
        if (!isSimulationRunning) {
            return;
        }

        // 查询待分配的任务
        List<Task> unassignedTasks = taskMapper.findByTruckIdIsNull();
        if (unassignedTasks.isEmpty()) {
            log.debug("没有待分配任务，跳过优化");
            return;
        }

        // 查询所有空闲车辆
        List<Truck> idleTrucks = truckMapper.selectByStatus(TruckStatusConstant.IDLE);
        if (idleTrucks.isEmpty()) {
            log.debug("没有空闲车辆，跳过优化");
            return;
        }

        log.info("=== 开始定时优化任务分配 ===");
        log.info("待分配任务数: {}, 空闲车辆数: {}", unassignedTasks.size(), idleTrucks.size());

        try {
            // 使用混合优化算法（遗传算法 + 模拟退火）
            Map<Truck, List<Task>> optimizedResult = optimizationService.hybridOptimization(idleTrucks, unassignedTasks);

            // 应用优化结果
            applyOptimizationResult(optimizedResult);

            log.info("=== 定时优化完成 ===");
        } catch (Exception e) {
            log.error("定时优化失败: {}", e.getMessage());
        }
    }

    /**
     * 应用优化算法的分配结果
     * 将优化后的任务分配方案应用到实际调度中
     */
    private void applyOptimizationResult(Map<Truck, List<Task>> optimizedResult) {
        // 获取当前待分配队列中的任务ID
        Set<Integer> pendingTaskIds = new HashSet<>();
        for (Task t : pendingTasks) {
            if (t.getId() != null) {
                pendingTaskIds.add(t.getId());
            }
        }

        for (Map.Entry<Truck, List<Task>> entry : optimizedResult.entrySet()) {
            Truck truck = entry.getKey();
            List<Task> tasks = entry.getValue();

            if (!tasks.isEmpty()) {
                // 选择第一个任务进行分配（优化算法可能分配多个任务给一辆车）
                Task task = tasks.get(0);
                
                // 检查任务是否仍未分配且不在待分配队列中
                if (task.getId() != null && !pendingTaskIds.contains(task.getId())) {
                    Task currentTask = taskMapper.selectByPrimaryKey(task.getId());
                    if (currentTask != null && currentTask.getTruckId() == null) {
                        log.info("优化结果: 车辆{} 分配任务{}", truck.getId(), task.getId());
                        
                        // 将任务加入待分配队列，由贪心算法的定时任务处理实际分配
                        pendingTasks.offer(task);
                    }
                }
            }
        }
    }

}