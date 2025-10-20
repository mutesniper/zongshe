package com.muite.zongshe1.controller;


import com.muite.zongshe1.entity.Task;
import com.muite.zongshe1.mapper.TaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
public class TaskController {
    @Autowired
    TaskMapper taskMapper;
    @GetMapping("/task")
    public List<Task> selectAll() {
        return taskMapper.selectAll();
    }

}
