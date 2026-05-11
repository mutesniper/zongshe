package com.muite.zongshe1.controller;

import com.muite.zongshe1.entity.Truck;
import com.muite.zongshe1.utils.SimulationService;
import com.muite.zongshe1.utils.CostService;
import com.muite.zongshe1.service.TruckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@Tag(name="车辆相关接口")
public class TruckController {
    @Autowired
    TruckService truckService;
    @Autowired
    SimulationService simulationService;
    @Autowired
    CostService costService;

    @Operation(summary = "查询所有车辆")
    @GetMapping("/truck")
    public List<Truck> SelectAll() {
        List<Truck> trucks = truckService.selectAll();
        return trucks;
    }

    @Operation(summary = "获取车辆统计数据")
    @GetMapping("/truck/statistics")
    public Map<String, Object> getTruckStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            List<Truck> allTrucks = truckService.selectAll();
            stats.put("totalTrucks", allTrucks.size());
            
            Map<String, Long> statusCount = new HashMap<>();
            Map<String, Long> typeCount = new HashMap<>();
            
            for (Truck truck : allTrucks) {
                if (truck.getStatus() != null) {
                    statusCount.merge(String.valueOf(truck.getStatus()), 1L, Long::sum);
                }
                if (truck.getType() != null) {
                    typeCount.merge(String.valueOf(truck.getType()), 1L, Long::sum);
                }
            }
            
            stats.put("statusDistribution", statusCount);
            stats.put("typeDistribution", typeCount);
            
            Map<String, Object> waitingStats = simulationService.getTotalWaitingTime();
            stats.put("waitingTimeStats", waitingStats);
        } catch (Exception e) {
            stats.put("error", e.getMessage());
            e.printStackTrace();
            stats.put("errorType", e.getClass().getName());
        }
        
        return stats;
    }

    @Operation(summary = "获取等待时间统计")
    @GetMapping("/truck/waiting-time")
    public Map<String, Object> getWaitingTime() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("data", simulationService.getTotalWaitingTime());
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Operation(summary = "测试API")
    @GetMapping("/truck/test")
    public Map<String, Object> testApi() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("message", "API is working");
        return result;
    }

    @Operation(summary = "测试等待时间API")
    @GetMapping("/truck/test-waiting")
    public Map<String, Object> testWaitingApi() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("waitingStats", simulationService.getTotalWaitingTime());
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getName());
            result.put("stackTrace", getStackTraceAsString(e));
        }
        return result;
    }

    private String getStackTraceAsString(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    @Operation(summary = "获取成本计算数据")
    @GetMapping("/truck/cost")
    public Map<String, Object> getCostData() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("data", costService.getAllCosts());
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Operation(summary = "获取直接成本(A)")
    @GetMapping("/truck/cost/a")
    public Map<String, Object> getCostA() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("data", costService.calculateCostA());
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Operation(summary = "获取运货快成本(B)")
    @GetMapping("/truck/cost/b")
    public Map<String, Object> getCostB() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("data", costService.calculateCostB());
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Operation(summary = "获取效率+公平成本(C)")
    @GetMapping("/truck/cost/c")
    public Map<String, Object> getCostC() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("data", costService.calculateCostC());
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Operation(summary = "获取平台成本(D)")
    @GetMapping("/truck/cost/d")
    public Map<String, Object> getCostD() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("data", costService.calculateCostD());
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Operation(summary = "获取风险成本(E)")
    @GetMapping("/truck/cost/e")
    public Map<String, Object> getCostE() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("data", costService.calculateCostE("A"));
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @Operation(summary = "获取大综合成本(F)")
    @GetMapping("/truck/cost/f")
    public Map<String, Object> getCostF() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("data", costService.calculateCostF());
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
}
