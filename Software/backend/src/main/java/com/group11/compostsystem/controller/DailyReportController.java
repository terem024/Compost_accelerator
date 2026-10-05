package com.group11.compostsystem.controller;

import com.group11.compostsystem.dto.DailyReportResponse;
import com.group11.compostsystem.service.DailyReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/daily-reports")
public class DailyReportController {
    private final DailyReportService dailyReportService;

    public DailyReportController(DailyReportService dailyReportService) {
        this.dailyReportService = dailyReportService;
    }

    @GetMapping
    public ResponseEntity<List<DailyReportResponse>> getRecentReports() {
        return ResponseEntity.ok(dailyReportService.getRecentReports());
    }

    @PostMapping("/generate-yesterday")
    public ResponseEntity<DailyReportResponse> generateYesterdayReport() {
        return ResponseEntity.ok(dailyReportService.generateYesterdayReport());
    }
}
