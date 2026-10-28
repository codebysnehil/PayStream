package com.paystream.gateway.scenario;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    public record ScenarioView(String id, String title, String description, String expected) {
    }

    private final ScenarioService scenarios;

    public ScenarioController(ScenarioService scenarios) {
        this.scenarios = scenarios;
    }

    @GetMapping
    public List<ScenarioView> list() {
        return Arrays.stream(Scenario.values())
                .map(s -> new ScenarioView(s.name(), s.title(), s.description(), s.expected()))
                .toList();
    }

    @PostMapping("/{id}")
    public ScenarioService.ScenarioRun run(@PathVariable String id) {
        Scenario scenario;
        try {
            scenario = Scenario.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown scenario");
        }
        return scenarios.run(scenario);
    }
}
