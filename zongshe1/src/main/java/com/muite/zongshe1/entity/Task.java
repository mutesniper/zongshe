package com.muite.zongshe1.entity;


import java.math.BigDecimal;

public class Task {
    private Integer id;
    private String start;
    private String destination;
    private Object goodsType;
    private Integer truckId;
    private Object status;

    private BigDecimal weight;
    private BigDecimal volume;


    public Task() {
    }

    public Task(Integer id, String start, String destination, Object goodsType, Integer truckId, Object status, BigDecimal weight, BigDecimal volume) {
        this.id = id;
        this.start = start;
        this.destination = destination;
        this.goodsType = goodsType;
        this.truckId = truckId;
        this.status = status;
        this.weight = weight;
        this.volume = volume;
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
     * @return goodsType
     */
    public Object getGoodsType() {
        return goodsType;
    }

    /**
     * 设置
     * @param goodsType
     */
    public void setGoodsType(Object goodsType) {
        this.goodsType = goodsType;
    }

    /**
     * 获取
     * @return truckId
     */
    public Integer getTruckId() {
        return truckId;
    }

    /**
     * 设置
     * @param truckId
     */
    public void setTruckId(Integer truckId) {
        this.truckId = truckId;
    }

    /**
     * 获取
     * @return status
     */
    public Object getStatus() {
        return status;
    }

    /**
     * 设置
     * @param status
     */
    public void setStatus(Object status) {
        this.status = status;
    }

    /**
     * 获取
     * @return weight
     */
    public BigDecimal getWeight() {
        return weight;
    }

    /**
     * 设置
     * @param weight
     */
    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    /**
     * 获取
     * @return volume
     */
    public BigDecimal getVolume() {
        return volume;
    }

    /**
     * 设置
     * @param volume
     */
    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public String toString() {
        return "Task{id = " + id + ", start = " + start + ", destination = " + destination + ", goodsType = " + goodsType + ", truckId = " + truckId + ", status = " + status + ", weight = " + weight + ", volume = " + volume + "}";
    }
}
