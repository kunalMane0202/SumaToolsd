package com.connectprojects.backend.reports.monthlylog;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/monthly-logs")
public class MonthlyLogController {
    private final MonthlyLogService service;

    public MonthlyLogController(MonthlyLogService service) {
        this.service = service;
    }

    @GetMapping
    public List<MonthlyLogImportResponse> list(@PathVariable Long projectId, Authentication authentication) {
        return service.list(projectId, authentication);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MonthlyLogReportResponse upload(@PathVariable Long projectId,
                                           @RequestParam("file") MultipartFile file,
                                           Authentication authentication) {
        return service.upload(projectId, file, authentication);
    }

    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MonthlyLogPreviewResponse preview(@PathVariable Long projectId,
                                             @RequestParam("file") MultipartFile file,
                                             Authentication authentication) {
        return service.preview(projectId, file, authentication);
    }

    @GetMapping("/{reportId}")
    public MonthlyLogReportResponse get(@PathVariable Long projectId,
                                        @PathVariable Long reportId,
                                        @RequestParam(defaultValue = "") String query,
                                        @RequestParam(defaultValue = "") String month,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "25") int pageSize,
                                        Authentication authentication) {
        return service.get(projectId, reportId, query, month, page, pageSize, authentication);
    }

    @DeleteMapping("/{reportId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long projectId, @PathVariable Long reportId, Authentication authentication) {
        service.delete(projectId, reportId, authentication);
    }
}
