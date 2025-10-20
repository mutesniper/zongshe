package com.muite.zongshe1.entity;

import java.math.BigDecimal;

public class Route {
    private Integer id;
    private String start;
    private String destination;
    private BigDecimal time_cost;
    private String passable;

    public Route() {
    }

    public Route(Integer id, String start, String destination, BigDecimal time_cost, String passable) {
        this.id = id;
        this.start = start;
        this.destination = destination;
        this.time_cost = time_cost;
        this.passable = passable;
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
     * @return time_cost
     */
    public BigDecimal getTime_cost() {
        return time_cost;
    }

    /**
     * 设置
     * @param time_cost
     */
    public void setTime_cost(BigDecimal time_cost) {
        this.time_cost = time_cost;
    }

    /**
     * 获取
     * @return passable
     */
    public String getPassable() {
        return passable;
    }

    /**
     * 设置
     * @param passable
     */
    public void setPassable(String passable) {
        this.passable = passable;
    }

    public String toString() {
        return "Route{id = " + id + ", start = " + start + ", destination = " + destination + ", time_cost = " + time_cost + ", passable = " + passable + "}";
    }
}
