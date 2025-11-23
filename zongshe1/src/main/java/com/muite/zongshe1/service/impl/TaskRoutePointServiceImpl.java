package com.muite.zongshe1.service.impl;

import com.muite.zongshe1.entity.TaskRoutePoint;
import com.muite.zongshe1.mapper.TaskRoutePointMapper;
import com.muite.zongshe1.service.TaskRoutePointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskRoutePointServiceImpl implements TaskRoutePointService {

    @Autowired
    TaskRoutePointMapper taskRoutePointMapper;

    @Override
    public List<TaskRoutePoint> selectAll() {
        return taskRoutePointMapper.selectAll();
    }

    @Override
    public List<TaskRoutePoint> selectByTaskId(int taskId) {
        List<TaskRoutePoint> taskRoutePoints = taskRoutePointMapper.selectByTaskId(taskId);
        for (TaskRoutePoint taskRoutePoint : taskRoutePoints) {
            String location=taskRoutePoint.getPointLocation();
            String newLocation=location.split(",")[1]+","+location.split(",")[0];
            taskRoutePoint.setPointLocation(newLocation);
        }
        return taskRoutePoints;
    }
}
