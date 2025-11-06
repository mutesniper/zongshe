package com.muite.zongshe1.controller;

import com.muite.zongshe1.entity.Truck;
import com.muite.zongshe1.utils.SimulationService;
import com.muite.zongshe1.service.TruckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@Tag(name="车辆相关接口")
public class TruckController {
    @Autowired
    TruckService truckService;
    @Autowired
    SimulationService simulationService;

    @Operation(summary = "查询所有车辆")
    @GetMapping("/truck")
    public List<Truck> SelectAll() {
        List<Truck> trucks = truckService.selectAll();
        return trucks;
    }
}
