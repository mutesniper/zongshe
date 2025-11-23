package com.muite.zongshe1.utils;

import com.muite.zongshe1.constant.GoodsTypeConstant;
import com.muite.zongshe1.constant.PoiTypeConstant;
import com.muite.zongshe1.entity.Point;
import com.muite.zongshe1.entity.Task;
import com.muite.zongshe1.mapper.PointMapper;
import com.muite.zongshe1.mapper.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Component
public class TaskGenerator {
    private static final Logger log = LoggerFactory.getLogger(TaskGenerator.class);

    // 随机种子（确保每次生成结果可复现，也可去掉用系统时间）
    private final Random random = new Random(System.currentTimeMillis());

    // 候选货物类型（可根据你的车辆分类调整）
    private static final List<String> GOODS_TYPES = List.of(
            GoodsTypeConstant.DANGEROUS_GOODS,
            GoodsTypeConstant.GENERAL_GOODS,
            GoodsTypeConstant.GOODS_REQUIREING_VENTILATION,
            GoodsTypeConstant.PERISHABLE_GOODS,
            GoodsTypeConstant.LIQUID_POWDERS,
            GoodsTypeConstant.BULK_HEAVY_GOODS,
            GoodsTypeConstant.BULK_GOODS
    );

    private final PointMapper pointMapper;
    private final TaskMapper taskMapper;

    // 构造函数注入依赖
    public TaskGenerator(PointMapper pointMapper, TaskMapper taskMapper) {
        this.pointMapper = pointMapper;
        this.taskMapper = taskMapper;
    }

    /**
     * 随机生成任务
     * @param count 生成任务数量
     * @return 生成的任务列表
     */
    public List<Task> generateRandomTasks(int count) {
        // 1. 从数据库查询所有有效POI（作为起点/终点候选）
        List<Point> poiList = pointMapper.selectAll(); // 需实现PointMapper的selectAll方法
        if (poiList.size() < 2) {
            throw new RuntimeException("POI数据不足2条，无法生成任务（需不同起点和终点）");
        }

        List<Task> taskList = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // 2. 随机选择起点和终点（确保起点≠终点）

            // 筛选起点（仓库类型）
            List<Point> startCandidates = poiList.stream()
                    .filter(poi -> PoiTypeConstant.WAREHOUSE.equals(poi.getType()))
                    .collect(Collectors.toList());
            // 筛选终点（商场类型）
            List<Point> endCandidates = poiList.stream()
                    .filter(poi -> PoiTypeConstant.SHOPPING_MALL.equals(poi.getType()))
                    .collect(Collectors.toList());
            
            Point startPoi = startCandidates.get(random.nextInt(startCandidates.size()));
            Point endPoi = endCandidates.get(random.nextInt(endCandidates.size()));


            // 3. 随机选择货物类型
            String goodsType = GOODS_TYPES.get(random.nextInt(GOODS_TYPES.size()));


            // 4.按货物类型生成重量和体积（单位：吨、立方米）
            double weightDouble;
            double volumeDouble;
            switch (goodsType) {
                case GoodsTypeConstant.DANGEROUS_GOODS:
                    // 危险品通常单次运输量较小，且重量/体积适中
                    weightDouble = random.nextDouble() * 3 + 0.1; // 0.1-3.1吨
                    volumeDouble = random.nextDouble() * 5 + 0.5; // 0.5-5.5立方米
                    break;
                case GoodsTypeConstant.GENERAL_GOODS:
                    // 通用货物，重量和体积范围广
                    weightDouble = random.nextDouble() * 15 + 1; // 1-16吨
                    volumeDouble = random.nextDouble() * 30 + 5; // 5-35立方米
                    break;
                case GoodsTypeConstant.GOODS_REQUIREING_VENTILATION:
                    // 如农产品、纺织品，体积较大但重量较轻
                    weightDouble = random.nextDouble() * 8 + 0.5; // 0.5-8.5吨
                    volumeDouble = random.nextDouble() * 40 + 10; // 10-50立方米
                    break;
                case GoodsTypeConstant.PERISHABLE_GOODS:
                    // 如生鲜、食品，需冷藏，通常批量不大
                    weightDouble = random.nextDouble() * 6 + 0.3; // 0.3-6.3吨
                    volumeDouble = random.nextDouble() * 15 + 2; // 2-17立方米
                    break;
                case GoodsTypeConstant.LIQUID_POWDERS:
                    // 如化工液体、面粉，重量大但体积紧凑（罐式车装载）
                    weightDouble = random.nextDouble() * 20 + 5; // 5-25吨
                    volumeDouble = random.nextDouble() * 10 + 3; // 3-13立方米
                    break;
                case GoodsTypeConstant.BULK_HEAVY_GOODS:
                    // 如钢材、石材，重量大、体积相对小
                    weightDouble = random.nextDouble() * 50 + 30; // 30-80吨
                    volumeDouble = random.nextDouble() * 20 + 5; // 5-25立方米
                    break;
                case GoodsTypeConstant.BULK_GOODS:
                    // 如粮食、煤炭，体积大、重量中等
                    weightDouble = random.nextDouble() * 40 + 20; // 20-60吨
                    volumeDouble = random.nextDouble() * 80 + 50; // 50-130立方米
                    break;
                default:
                    weightDouble = 0.0;
                    volumeDouble = 0.0;
            }

            // 转换为BigDecimal，保留2位小数（四舍五入）
            BigDecimal weight = BigDecimal.valueOf(weightDouble)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal volume = BigDecimal.valueOf(volumeDouble)
                    .setScale(2, RoundingMode.HALF_UP);



            // 5. 构建Task对象（根据你的Task实体字段调整）
            Task task = new Task();
            task.setStart(startPoi.getLocation()); // 起点经纬度（复用POI的location字段）
            task.setDestination(endPoi.getLocation()); // 终点经纬度
            task.setGoodsType(goodsType);
            task.setStatus("待分配"); // 任务初始状态
            task.setTruckId(null); // 未分配车辆
            task.setWeight(weight); // 设置重量
            task.setVolume(volume); // 设置体积

            taskList.add(task);
        }

        // 6. 批量保存到数据库
        if (!taskList.isEmpty()) {
            taskMapper.batchInsert(taskList); // 需实现TaskMapper的批量插入方法
            log.info("成功随机生成 {} 条任务", taskList.size());
        }

        return taskList;
    }



    // TODO 修改间隔时间并打开
    /**
     * 定时自动生成任务（每小时生成5条）
     */
/*    @Scheduled(fixedRate = 3600000) // 间隔时间：3600000毫秒 = 1小时
    public void autoGenerateTasks() {
        try {
            // 每小时生成5条任务（可根据需求调整数量）
            generateRandomTasks(5);
            log.info("定时任务执行：自动生成5条任务");
        } catch (Exception e) {
            log.error("定时任务生成任务失败", e);
        }
    }
    */
}