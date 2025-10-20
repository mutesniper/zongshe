package com.muite.zongshe1.controller;

import com.muite.zongshe1.entity.Point;
import com.muite.zongshe1.mapper.PointMapper;
import com.muite.zongshe1.service.PointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
public class PointController {
    @Autowired
    PointService pointService;

    @GetMapping("/point/{name}")
    public List<Point> select(@PathVariable String name) {
        List<Point> location= pointService.selectByName(name);
        return location;
    }

    @GetMapping("/point")
    public List<Point> selectAll(){
        List<Point> locations=pointService.selectAll();
        return locations;
    }

}
