package com.sumasoft.tracking.settings;

import com.sumasoft.tracking.settings.SettingsService.PublicSettings;
import com.sumasoft.tracking.settings.SettingsService.SaveRequest;
import com.sumasoft.tracking.settings.SettingsService.Setting;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Settings page (Step 38).
 *   GET /api/settings          all settings (Super Admin)
 *   PUT /api/settings          save changed settings (Super Admin)
 *   GET /api/settings/public   company name and default time zone (everyone signed in)
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    @GetMapping
    public List<Setting> list() {
        return service.list();
    }

    @PutMapping
    public List<Setting> save(@Valid @RequestBody SaveRequest request) {
        return service.save(request);
    }

    @GetMapping("/public")
    public PublicSettings publicSettings() {
        return service.publicSettings();
    }
}