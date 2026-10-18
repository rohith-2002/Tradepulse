package com.tradepulse.tradepulse.alert.controller;

import com.tradepulse.tradepulse.alert.entity.Alert;
import com.tradepulse.tradepulse.alert.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Manage trading alerts")
public class AlertController {

    private final AlertService alertService;

    public record CreateAlertRequest(@jakarta.validation.constraints.NotBlank String symbol, @jakarta.validation.constraints.NotBlank String condition, @jakarta.validation.constraints.Positive @jakarta.validation.constraints.NotNull BigDecimal threshold, String channel) {}

    @GetMapping
    @Operation(summary = "Get user alerts")
    public List<Alert> getUserAlerts(@AuthenticationPrincipal UserDetails userDetails) {
        return alertService.getUserAlerts(userDetails.getUsername());
    }

    @PostMapping
    @Operation(summary = "Create a new alert")
    public Alert createAlert(
            @AuthenticationPrincipal UserDetails userDetails,
            @jakarta.validation.Valid @RequestBody CreateAlertRequest request) {
        return alertService.createAlert(userDetails.getUsername(), request.symbol(), request.condition(), request.threshold(), request.channel());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an alert")
    public void deleteAlert(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        alertService.deleteAlert(id, userDetails.getUsername());
    }
}
