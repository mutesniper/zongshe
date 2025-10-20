package com.muite.zongshe1.entity;



public class Task {
    private Integer id;
    private String start;
    private String destination;
    private Object goods_type;
    private Integer truck_id;


    public Task() {
    }

    public Task(Integer id, String start, String destination, Object goods_type, Integer truck_id) {
        this.id = id;
        this.start = start;
        this.destination = destination;
        this.goods_type = goods_type;
        this.truck_id = truck_id;
    }

    /**
     * 获取
     * @return id
     */
    public Integer getId() {
        return id;
    }

    /**
     * 设置
     * @param id
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * 获取
     * @return start
     */
    public String getStart() {
        return start;
    }

    /**
     * 设置
     * @param start
     */
    public void setStart(String start) {
        this.start = start;
    }

    /**
     * 获取
     * @return destination
     */
    public String getDestination() {
        return destination;
    }

    /**
     * 设置
     * @param destination
     */
    public void setDestination(String destination) {
        this.destination = destination;
    }

    /**
     * 获取
     * @return goods_type
     */
    public Object getGoods_type() {
        return goods_type;
    }

    /**
     * 设置
     * @param goods_type
     */
    public void setGoods_type(Object goods_type) {
        this.goods_type = goods_type;
    }

    /**
     * 获取
     * @return truck_id
     */
    public Integer getTruck_id() {
        return truck_id;
    }

    /**
     * 设置
     * @param truck_id
     */
    public void setTruck_id(Integer truck_id) {
        this.truck_id = truck_id;
    }

    public String toString() {
        return "Task{id = " + id + ", start = " + start + ", destination = " + destination + ", goods_type = " + goods_type + ", truck_id = " + truck_id + "}";
    }
}
