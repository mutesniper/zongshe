package com.muite.zongshe1.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muite.zongshe1.entity.Point;
import com.muite.zongshe1.entity.Truck;
import com.muite.zongshe1.entity.TruckRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class AmapTruckRouteService {
    @Value("${amap.key}")
    private String amapKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // 引入日志，打印原始响应方便排查
    private static final Logger log = LoggerFactory.getLogger(AmapTruckRouteService.class);

    public AmapTruckRouteService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用高德货车路径规划API
     * @param start 起点经纬度（格式："经度,纬度"，注意高德坐标系为GCJ02）
     * @param destination 终点经纬度（格式同上）
     * @param truck 货车信息（用于限制车型、重量等参数）
     * @return 路径规划结果（封装为项目中的TruckRoute实体）
     */
    public TruckRoute getTruckRoute(String start, String destination, Truck truck) {
        // todo 调整参数
        // 1. 构建API请求参数（参考高德API文档）
        String url = String.format(
                "https://restapi.amap.com/v3/direction/driving?key=%s&origin=%s&destination=%s&strategy=0",
                amapKey,
                start,  // 注意：高德API中起点格式为"经度,纬度"，需与现有"纬度,经度"格式转换
                destination
        );

        try {
            // 2. 调用API
            log.info("调用高德货车路径规划API，URL：{}", url);
            String response = restTemplate.getForObject(url, String.class);

            //log.info("高德API返回原始响应：{}", response);
            JsonNode root = objectMapper.readTree(response);
            if (root == null || !root.has("status")) {
                return new TruckRoute(null, null, null, false, "API返回无status字段，响应：" + response);
            }

            // 3. 解析响应（根据高德API返回格式调整）
            String status = root.get("status").asText();
            if ("1".equals(status)) {
                if (root.has("route") && root.get("route").has("paths") && root.get("route").get("paths").size() > 0) {
                    JsonNode path = root.get("route").get("paths").get(0);
                    int distance = path.get("distance").asInt();
                    int duration = path.get("duration").asInt();

                    // 解析路径点
                    List<Point> pathPoints = new ArrayList<>();
                    StringBuilder polylineBuilder = new StringBuilder();

                    // 1. 优先提取paths节点下的polyline（如果存在）
                    if (path.has("polyline")) {
                        polylineBuilder.append(path.get("polyline").asText()).append(";");
                    }

                    // 2. 提取steps数组中的所有polyline（关键补充）
                    if (path.has("steps") && path.get("steps").isArray()) {
                        for (JsonNode step : path.get("steps")) {
                            if (step.has("polyline")) {
                                polylineBuilder.append(step.get("polyline").asText()).append(";");
                            }
                        }
                    }

                    // 3. 处理合并后的路径点字符串
                    String fullPolyline = polylineBuilder.toString();
                    if (!fullPolyline.isEmpty()) {
                        // 分割所有路径点（去除末尾可能的空字符串）
                        String[] points = fullPolyline.split(";");
                        for (String p : points) {
                            if (p.trim().isEmpty()) continue; // 跳过空值
                            String[] lonLat = p.split(",");
                            if (lonLat.length == 2) {
                                // 关键修复：高德返回格式为"经度,纬度"，需转为项目使用的"纬度,经度"
                                String location = lonLat[1].trim() + "," + lonLat[0].trim();
                                Point point = new Point();
                                point.setLocation(location);
                                point.setName("");
                                point.setType("route_point");
                                pathPoints.add(point);
                            }
                        }
                    }

                    // 4. 日志打印路径点数量，便于调试
                    log.info("解析到的路径点数量：{}", pathPoints.size());
                    return new TruckRoute(distance, duration, pathPoints, true, null);
                } else {
                    return new TruckRoute(null, null, null, false, "无可用路径");
                }
            } else {
                String errorMsg = root.has("info") ? root.get("info").asText() : "规划失败";
                return new TruckRoute(null, null, null, false, errorMsg);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new TruckRoute(null, null, null, false, "API调用异常：" + e.getMessage());
        }
    }
}

