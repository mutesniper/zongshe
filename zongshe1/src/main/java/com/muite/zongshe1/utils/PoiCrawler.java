package com.muite.zongshe1.utils;

import com.muite.zongshe1.entity.Point;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PoiCrawler {

    @Value("${amap.key}")
    private String amapKey;

    @Value("${poi.crawler.auto-run}")
    private boolean autoRun;

    @Value("${poi.crawler.keywords}")
    private String keywords;

    @Value("${poi.crawler.city}")
    private String city;

    @Value("${poi.crawler.max-pages}")
    private int maxPages;

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PoiCrawler(JdbcTemplate jdbcTemplate, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (autoRun) {
            System.out.println(" 启动时自动爬取 POI 数据...");
            getData();
        }
    }

    public void getData() {
        List<Point> allPois = new ArrayList<>();

        try {
            for (int page = 1; page <= maxPages; page++) {
                String url = String.format(
                        "https://restapi.amap.com/v3/place/text?key=%s&keywords=%s&city=%s&offset=20&page=%d&extensions=all",
                        amapKey, keywords, city, page
                );

                String response = restTemplate.getForObject(url, String.class);
                List<Point> pois = parsePoiResponse(response);
                if (pois.isEmpty()) break;
                allPois.addAll(pois);
                System.out.println(" 第 " + page + " 页，获取 " + pois.size() + " 条 POI");

                Thread.sleep(1000); // 避免触发 API 限流
            }
            System.out.println(allPois);
            saveToDatabase(allPois);

        } catch (Exception e) {
            System.err.println("爬取失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<Point> parsePoiResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        if (!"1".equals(root.get("status").asText())) {
            System.err.println("API 错误: " + json);
            return new ArrayList<>();
        }

        List<Point> list = new ArrayList<>();
        JsonNode pois = root.get("pois");
        if (pois != null && pois.isArray()) {
            for (JsonNode node : pois) {
                String name = node.get("name").asText("").trim();
                String location = node.get("location").asText("").trim();
                String type = node.get("type").asText("").split(";")[0];

                if (!name.isEmpty() && !location.isEmpty()) {
                    list.add(new Point(name, location, type));
                }
            }
        }
        return list;
    }

    private void saveToDatabase(List<Point> pois) {
        // 过滤有效数据
        List<Point> validPois = pois.stream()
                .filter(poi -> {
                    String loc = poi.getLocation();
                    if (loc == null || loc.isEmpty()) return false;
                    String[] parts = loc.split(",");
                    return parts.length == 2 && isNumeric(parts[0]) && isNumeric(parts[1]);
                })
                .collect(Collectors.toList());

        // 关键：只有 validPois 非空才执行插入
        if (validPois.isEmpty()) {
            System.out.println(" 无有效 POI 数据可保存");
            return; // ← 直接退出，不执行后续插入！
        }

        // 执行插入
        String sql = "INSERT INTO poi (name, location, type) VALUES (?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, validPois, validPois.size(), (ps, poi) -> {
            String[] coords = poi.getLocation().split(",");
            String wkt = String.format("POINT(%s %s)", coords[0], coords[1]);
            ps.setString(1, poi.getName());
            ps.setString(2, poi.getLocation());
            ps.setString(3, poi.getType());
        });

        // 只打印 validPois 的数量
        System.out.println(" 共保存 " + validPois.size() + " 条 POI 到 MySQL");
    }

    private boolean isNumeric(String str) {
        if (str == null || str.trim().isEmpty()) return false;
        try {
            Double.parseDouble(str.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}