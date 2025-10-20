package com.muite.zongshe1.controller;

import com.muite.zongshe1.entity.Truck;
import com.muite.zongshe1.utils.SimulationService;
import com.muite.zongshe1.service.TruckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
public class TruckController {
    @Autowired
    TruckService truckService;
    @Autowired
    SimulationService simulationService;


    @GetMapping("/truck")
    public List<Truck> SelectAll() {
        List<Truck> trucks = truckService.selectAll();
        return trucks;
    }
}
