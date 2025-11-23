package com.muite.zongshe1.entity;

import java.math.BigDecimal;

/**
 * 
 * @TableName truck
 */

public class Truck {
    /**
     * 
     */
    private Integer id;

    /**
     * 
     */
    private BigDecimal maxWeight;

    /**
     * 
     */
    private BigDecimal maxVol;

    /**
     * 
     */
    private Object status;

    /**
     * 
     */
    private Object type;

    /**
     * 
     */
    private String location;


    private BigDecimal length;
    private BigDecimal weight;

    private transient double distanceToTask; // 临时距离（不存入数据库）

    // 新增字段：当前行驶到的路径点顺序（从1开始）
    private Integer currentPointSequence;

    // 新增getter和setter
    public Integer getCurrentPointSequence() {
        return currentPointSequence;
    }

    public void setCurrentPointSequence(Integer currentPointSequence) {
        this.currentPointSequence = currentPointSequence;
    }

    public Truck() {
    }

    public Truck(Integer id, BigDecimal maxWeight, BigDecimal maxVol, Object status, Object type, String location) {
        this.id = id;
        this.maxWeight = maxWeight;
        this.maxVol = maxVol;
        this.status = status;
        this.type = type;
        this.location = location;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        Truck other = (Truck) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getMaxWeight() == null ? other.getMaxWeight() == null : this.getMaxWeight().equals(other.getMaxWeight()))
            && (this.getMaxVol() == null ? other.getMaxVol() == null : this.getMaxVol().equals(other.getMaxVol()))
            && (this.getStatus() == null ? other.getStatus() == null : this.getStatus().equals(other.getStatus()))
            && (this.getType() == null ? other.getType() == null : this.getType().equals(other.getType()))
            && (this.getLocation() == null ? other.getLocation() == null : this.getLocation().equals(other.getLocation()));

    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getMaxWeight() == null) ? 0 : getMaxWeight().hashCode());
        result = prime * result + ((getMaxVol() == null) ? 0 : getMaxVol().hashCode());
        result = prime * result + ((getStatus() == null) ? 0 : getStatus().hashCode());
        result = prime * result + ((getType() == null) ? 0 : getType().hashCode());
        result = prime * result + ((getLocation() == null) ? 0 : getLocation().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", maxWeight=").append(maxWeight);
        sb.append(", maxVol=").append(maxVol);
        sb.append(", status=").append(status);
        sb.append(", type=").append(type);
        sb.append(", location=").append(location);
        sb.append("]");
        return sb.toString();
    }

    public Double getDistanceToTask() {
        return distanceToTask;
    }
    public void setDistanceToTask(Double distanceToTask) {
        this.distanceToTask = distanceToTask;
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
     * @return maxWeight
     */
    public BigDecimal getMaxWeight() {
        return maxWeight;
    }

    /**
     * 设置
     * @param maxWeight
     */
    public void setMaxWeight(BigDecimal maxWeight) {
        this.maxWeight = maxWeight;
    }

    /**
     * 获取
     * @return maxVol
     */
    public BigDecimal getMaxVol() {
        return maxVol;
    }

    /**
     * 设置
     * @param maxVol
     */
    public void setMaxVol(BigDecimal maxVol) {
        this.maxVol = maxVol;
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
     * @return type
     */
    public Object getType() {
        return type;
    }

    /**
     * 设置
     * @param type
     */
    public void setType(Object type) {
        this.type = type;
    }

    /**
     * 获取
     * @return location
     */
    public String getLocation() {
        return location;
    }

    /**
     * 设置
     * @param location
     */
    public void setLocation(String location) {
        this.location = location;
    }



}