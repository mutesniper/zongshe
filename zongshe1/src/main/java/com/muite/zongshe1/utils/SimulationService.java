package com.muite.zongshe1.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muite.zongshe1.entity.Task;
import com.muite.zongshe1.entity.Truck;
import com.muite.zongshe1.mapper.TruckMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class SimulationService {
    @Autowired
    TruckMapper truckMapper;


    private final Queue<Task> pendingTasks = new ConcurrentLinkedQueue<>();

    // 每秒更新一次车辆位置（仿真移动）
    @Scheduled(fixedRate = 1000) // 1秒一次
    public void simulateMovement() {
        List<Truck> trucks = truckMapper.selectAll();
        ObjectMapper mapper = new ObjectMapper();
        try {
            String json = mapper.writeValueAsString(trucks);
            // 4. 推送给前端
            VehicleSimulationSocket.broadcast(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateStatus(Integer truckId){


    }





}
