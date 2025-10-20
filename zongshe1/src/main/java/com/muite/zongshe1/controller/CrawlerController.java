package com.muite.zongshe1.controller;

import com.muite.zongshe1.utils.PoiCrawler;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class CrawlerController {

    private final PoiCrawler poiCrawler;

    public CrawlerController(PoiCrawler poiCrawler) {
        this.poiCrawler = poiCrawler;
    }

    @GetMapping("/crawl")
    public String crawl() {
        poiCrawler.getData();
        return "爬取完成！";
    }
}