package com.mcp_gateway_k8s_store.backend.controller;

import com.mcp_gateway_k8s_store.backend.service.WeatherMcpService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherMcpService weatherMcpService;

    public WeatherController(WeatherMcpService weatherMcpService) { this.weatherMcpService = weatherMcpService; }

    @GetMapping("/tools")
    public List<String> getAvailableTools() {
        return weatherMcpService.getAvailableTools();
    }

    @GetMapping("/status")
    public Map<String, Object> getConnectionStatus() {
        return weatherMcpService.getConnectionStatus();
    }

    @GetMapping("/location")
    public Object searchLocation(
            @RequestParam String location
    ) {
        return weatherMcpService.searchLocation(location);
    }

    @GetMapping("/current")
    public Object getCurrentWeather(
            @RequestParam String location
    ) {
        return weatherMcpService.getCurrentWeather(location);
    }

    @GetMapping("/forecast")
    public Object getWeatherForecast(
            @RequestParam String location,
            @RequestParam(defaultValue = "5") int days
    ) {
        return weatherMcpService.getWeatherForecast(location, days);
    }
}
