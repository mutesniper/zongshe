package com.muite.zongshe1.mapper;

import com.muite.zongshe1.entity.Task;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskMapper {


    public List<Task> selectAll();

    /**
     * 更新任务的truckId
     * @param task
     */
    void updateTaskTruckId(Task task);

    /**
     * 获取车辆当前任务
     * @param truckId
     * @return
     */
    @Select("select * from task where truck_id=#{truckId}")
    Task selectByTruckId(Integer truckId);

    /**
     * 插入任务
     * @param task
     */
    void insert(Task task);

    /**
     * 批量插入任务
     * @param taskList
     */
    void batchInsert(List<Task> taskList);


    /**
     * 查询所有未分配车辆的任务（truck_id 为 null）
     * @return 未分配任务列表
     */
    @Select("SELECT * FROM task WHERE truck_id =-1")
    List<Task> findByTruckIdIsNull();

    /**
     * 更新任务的status
     * @param task
     */
    void updateStatus(Task task);


}
