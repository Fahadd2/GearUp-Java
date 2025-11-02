package com.gearup.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaticResourceController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> serveIndex() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/index.html"));
    }

    @GetMapping(value = "/login.html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> serveLogin() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/login.html"));
    }

    @GetMapping(value = "/signup.html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> serveSignup() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/signup.html"));
    }

    @GetMapping(value = "/reset.html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> serveReset() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/reset.html"));
    }

    @GetMapping(value = "/staff-login.html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> serveStaffLogin() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/staff-login.html"));
    }

    @GetMapping(value = "/staff.html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> serveStaff() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/staff.html"));
    }

    @GetMapping(value = "/config.js", produces = "application/javascript")
    public ResponseEntity<Resource> serveConfig() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/javascript"))
                .body(new ClassPathResource("static/config.js"));
    }

    @GetMapping(value = "/styles.css", produces = "text/css")
    public ResponseEntity<Resource> serveStyles() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/css"))
                .body(new ClassPathResource("static/styles.css"));
    }

    @GetMapping(value = "/staff.css", produces = "text/css")
    public ResponseEntity<Resource> serveStaffCss() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/css"))
                .body(new ClassPathResource("static/staff.css"));
    }
}
