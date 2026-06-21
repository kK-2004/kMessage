package com.kk2004.kmessage.api;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping("/")
    public String home() {
        return "redirect:/admin/";
    }

    @GetMapping({"/admin", "/admin/login.html"})
    public String admin() {
        return "redirect:/admin/";
    }

    @GetMapping("/admin/")
    public ResponseEntity<Resource> adminIndex() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/admin/index.html"));
    }
}
