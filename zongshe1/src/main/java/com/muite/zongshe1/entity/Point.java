package com.muite.zongshe1.entity;


/**
 * 
 * @TableName point
 */

public class Point {
    /**
     * 
     */
    private String dest;

    /**
     * 
     */
    private String name;

    /**
     * 
     */
    private String type;

    public Point() {
    }

    public Point(String dest, String name, String type) {
        this.dest = dest;
        this.name = name;
        this.type = type;
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
        Point other = (Point) that;
        return (this.getDest() == null ? other.getDest() == null : this.getDest().equals(other.getDest()))
            && (this.getName() == null ? other.getName() == null : this.getName().equals(other.getName()))
            && (this.getType() == null ? other.getType() == null : this.getType().equals(other.getType()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getDest() == null) ? 0 : getDest().hashCode());
        result = prime * result + ((getName() == null) ? 0 : getName().hashCode());
        result = prime * result + ((getType() == null) ? 0 : getType().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", dest=").append(dest);
        sb.append(", name=").append(name);
        sb.append(", type=").append(type);
        sb.append("]");
        return sb.toString();
    }

    /**
     * 获取
     * @return dest
     */
    public String getDest() {
        return dest;
    }

    /**
     * 设置
     * @param dest
     */
    public void setDest(String dest) {
        this.dest = dest;
    }

    /**
     * 获取
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * 设置
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取
     * @return type
     */
    public String getType() {
        return type;
    }

    /**
     * 设置
     * @param type
     */
    public void setType(String type) {
        this.type = type;
    }
}