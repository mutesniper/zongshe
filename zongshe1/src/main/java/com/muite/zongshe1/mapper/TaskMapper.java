package com.muite.zongshe1.mapper;

import com.muite.zongshe1.entity.Task;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TaskMapper {


    public List<Task> selectAll();
}
