package com.sumasoft.tracking.imports;

import com.sumasoft.tracking.imports.ImportDtos.ImportBatchResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Web addresses (API) for importing tickets from Excel / CSV.
 *   POST /api/imports/tickets      (form upload: file, projectId, mode = DRY_RUN or IMPORT,
 *                                   sheet = name of the month sheet, e.g. Nov26 (optional))
 *                                   DRY_RUN = check only, nothing is saved except the check report
 *   GET  /api/imports?projectId=1  -> list of imports (newest first)
 *   GET  /api/imports/{id}         -> one import with every issue found
 */
@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final TicketImportService service;

    public ImportController(TicketImportService service) {
        this.service = service;
    }

    @PostMapping(value = "/tickets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportBatchResponse importTickets(@RequestParam("file") MultipartFile file,
                                             @RequestParam("projectId") Long projectId,
                                             @RequestParam(value = "mode", defaultValue = "DRY_RUN") String mode,
                                             @RequestParam(value = "sheet", required = false) String sheet) {
        return service.importTickets(projectId, mode, sheet, file);
    }

    @GetMapping
    public List<ImportBatchResponse> list(@RequestParam(value = "projectId", required = false) Long projectId) {
        return service.list(projectId);
    }

    @GetMapping("/{id}")
    public ImportBatchResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}