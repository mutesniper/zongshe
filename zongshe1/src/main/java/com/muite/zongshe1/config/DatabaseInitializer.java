package com.muite.zongshe1.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始检查并初始化数据库表结构...");

        try {
            // 1. 创建 driver 表
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS driver (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(50)," +
                    "phone VARCHAR(20)," +
                    "license_type VARCHAR(10)," +
                    "status VARCHAR(20)," +
                    "truck_id INT" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            
            // 2. 创建 customer 表
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS customer (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(100)," +
                    "address VARCHAR(200)," +
                    "contact_person VARCHAR(50)," +
                    "phone VARCHAR(20)," +
                    "type VARCHAR(20)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

            // 3. 创建 maintenance_record 表
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS maintenance_record (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "truck_id INT," +
                    "start_date DATETIME," +
                    "end_date DATETIME," +
                    "type VARCHAR(20)," +
                    "description TEXT," +
                    "cost DECIMAL(10, 2)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

            // 4. 创建 alert_log 表
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS alert_log (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "truck_id INT," +
                    "driver_id INT," + // 新增：driver_id
                    "task_id INT," +
                    "type VARCHAR(20)," +
                    "level VARCHAR(10)," +
                    "location VARCHAR(50)," +
                    "create_time DATETIME," +
                    "is_handled BOOLEAN" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            
            // 尝试添加新列（如果表已存在）
            try {
                jdbcTemplate.execute("ALTER TABLE task ADD COLUMN sender_id INT");
                jdbcTemplate.execute("ALTER TABLE task ADD COLUMN receiver_id INT");
                jdbcTemplate.execute("ALTER TABLE task ADD COLUMN sender_name VARCHAR(100)");
                jdbcTemplate.execute("ALTER TABLE task ADD COLUMN receiver_name VARCHAR(100)");
            } catch (Exception e) {
                // 忽略已存在列的错误
            }
            try {
                jdbcTemplate.execute("ALTER TABLE alert_log ADD COLUMN driver_id INT");
            } catch (Exception e) {
                // 忽略
            }

            log.info("数据库表结构初始化完成");

            // 5. 初始化模拟数据（如果表为空）
            initMockData();
            
            // 6. 修复可能存在的经纬度格式错误 (lat,lng -> lng,lat)
            fixLocationData();
            
            // 7. (已移除) 强制重置所有车辆和任务
            // log.warn("执行深度清理：重置所有车辆位置至北京，并清空任务队列...");
            // ... (此前代码已注释或删除，保留现场)
            log.info("数据库检查与修复完成");

        } catch (Exception e) {
            log.error("数据库初始化失败", e);
        }
    }

    private void fixLocationData() {
        try {
            // 查出所有车辆
            java.util.List<java.util.Map<String, Object>> trucks = jdbcTemplate.queryForList("SELECT id, location FROM truck");
            for (java.util.Map<String, Object> truck : trucks) {
                Long id = ((Number) truck.get("id")).longValue();
                String location = (String) truck.get("location");
                
                if (location != null && location.contains(",")) {
                    String[] parts = location.split(",");
                    if (parts.length == 2) {
                        try {
                            double part1 = Double.parseDouble(parts[0].trim());
                            double part2 = Double.parseDouble(parts[1].trim());
                            
                            // 判断依据：中国纬度约 3-53，经度约 73-135
                            // 如果第一个数是纬度范围(10-60)，第二个数是经度范围(70-140)，说明是 lat,lng 格式，需要翻转
                            // 正常的 lng,lat 格式应该是 part1(70-140), part2(10-60)
                            if ((part1 >= 10 && part1 <= 60) && (part2 >= 70 && part2 <= 140)) {
                                String newLocation = String.format("%.6f,%.6f", part2, part1);
                                jdbcTemplate.update("UPDATE truck SET location = ? WHERE id = ?", newLocation, id);
                                log.info("修复车辆 {} 位置数据: {} -> {}", id, location, newLocation);
                            }
                        } catch (NumberFormatException e) {
                            // 忽略解析错误
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("修复车辆位置数据失败", e);
        }
    }

    private void initMockData() {
        // 初始化客户数据
        Integer customerCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer", Integer.class);
        if (customerCount != null && customerCount == 0) {
            log.info("初始化客户模拟数据...");
            jdbcTemplate.execute("INSERT INTO customer (name, address, contact_person, phone, type) VALUES " +
                    "('京东物流北京分拣中心', '北京市大兴区经海路', '张经理', '13800138001', '发货方')," +
                    "('顺丰速运通州集散点', '北京市通州区马驹桥', '李主管', '13900139002', '收货方')," +
                    "('盒马鲜生朝阳店', '北京市朝阳区十里堡', '王店长', '13700137003', '收货方')," +
                    "('中石化燕山石化工厂', '北京市房山区燕山', '赵工', '13600136004', '发货方')," +
                    "('北京新发地批发市场', '北京市丰台区新发地', '孙老板', '13500135005', '发货方')");
        }

        // 初始化司机数据
        // 判断司机是否少于车辆数，如果是则补充生成
        Integer driverCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM driver", Integer.class);
        Integer truckCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM truck", Integer.class);
        
        // 目标司机数量：至少比车辆多5人，或者固定50人
        int targetDriverCount = Math.max(truckCount != null ? truckCount + 5 : 50, 50);
        
        if (driverCount != null && driverCount < targetDriverCount) {
            log.info("检测到司机数量不足（当前{}，目标{}），开始补充生成...", driverCount, targetDriverCount);
            
            // 常见姓氏
            String[] surnames = {"赵", "钱", "孙", "李", "周", "吴", "郑", "王", "冯", "陈", "褚", "卫", "蒋", "沈", "韩", "杨", "朱", "秦", "尤", "许"};
            // 常见名字字符
            String[] namesChar = {"建", "国", "华", "强", "军", "平", "安", "康", "顺", "利", "明", "辉", "伟", "刚", "勇", "超", "杰", "涛", "斌", "波"};
            
            // 批量插入
            int needToCreate = targetDriverCount - driverCount;
            // 分批处理，避免SQL过长
            int batchSize = 20;
            for (int i = 0; i < needToCreate; i += batchSize) {
                 StringBuilder sql = new StringBuilder("INSERT INTO driver (name, phone, license_type, status) VALUES ");
                 int currentBatch = Math.min(batchSize, needToCreate - i);
                 
                 for (int j = 0; j < currentBatch; j++) {
                     if (j > 0) sql.append(",");
                     
                     // 随机生成姓名
                     String name = surnames[(int)(Math.random() * surnames.length)] + namesChar[(int)(Math.random() * namesChar.length)];
                     // 随机生成手机号
                     String phone = "13" + (int)(Math.random() * 9 + 1) + String.format("%08d", (int)(Math.random() * 100000000));
                     // 随机驾照 (A2占比40%, B2占比60%)
                     String license = Math.random() < 0.4 ? "A2" : "B2";
                     
                     sql.append(String.format("('%s', '%s', '%s', '空闲')", name, phone, license));
                 }
                 jdbcTemplate.execute(sql.toString());
            }
            log.info("成功补充生成 {} 位司机", needToCreate);
        }
        
        // 维保记录和告警日志通常随业务运行产生，暂不预置过多数据
    }
}
