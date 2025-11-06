package com.muite.zongshe1.controller;


import com.muite.zongshe1.entity.Task;
import com.muite.zongshe1.mapper.TaskMapper;
import com.muite.zongshe1.service.TaskService;
import com.muite.zongshe1.utils.SimulationService;
import com.muite.zongshe1.utils.TaskGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@Tag(name="任务相关接口")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    @Autowired
    TaskService taskService;
    @Autowired
    SimulationService simulationService;

    @Autowired
    TaskGenerator taskGenerator;

    @Operation(summary = "查询所有任务")
    @GetMapping("/task")
    public List<Task> selectAll() {
        return taskService.selectAll();
    }

    @Operation(summary = "添加任务")
    @PostMapping("/task")
    public void createTask(@RequestBody Task task) {
        taskService.insert(task);
        simulationService.addPendingTask(task);
    }

    /**
     * 随机生成任务接口
     * @param count 生成任务数量（默认10条，最大100条）
     * @return 生成结果
     */
    @GetMapping("/generate/random")
    @Operation(summary = "随机生成任务", description = "基于POI数据随机生成运输任务，支持指定数量")
    public String generateRandomTasks(
            @Parameter(description = "生成任务数量（1-100）", example = "10")
            @RequestParam(defaultValue = "10") int count) {

        try {
            // 限制最大生成数量，避免数据库压力
            if (count < 1) count = 1;
            if (count > 100) count = 100;

            taskGenerator.generateRandomTasks(count);
            return "成功生成 " + count + " 条随机任务";
        } catch (Exception e) {
            log.error("生成随机任务失败", e);
            return "生成失败：" + e.getMessage();
        }
    }
}
