package com.muite.zongshe1.controller;

import com.muite.zongshe1.mapper.PointMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PointController {
    @Autowired
    PointMapper pointMapper;
    @GetMapping("/point/{name}")
    public String select(@PathVariable String name) {
        String dest= pointMapper.selectByName(name);
        return dest;
    }

}
