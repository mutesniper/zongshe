package com.muite.zongshe1;

import com.muite.zongshe1.entity.Point;
import com.muite.zongshe1.mapper.PointMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class Zongshe1ApplicationTests {
    @Autowired
    PointMapper pointMapper;

    @Test
    void contextLoads() {
        String dest = pointMapper.selectByName("阜通西");
        System.out.println(dest);
    }

}
