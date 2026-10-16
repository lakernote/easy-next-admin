package com.laker.observability.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the dashboard entry page from this starter's JAR. */
@RestController
public class ObservabilityDashboardController {
    private static final Resource INDEX = new ClassPathResource("META-INF/resources/easy-observability/index.html");

    @GetMapping({"/easy-observability", "/easy-observability/"})
    public ResponseEntity<Resource> index() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(INDEX);
    }
}
