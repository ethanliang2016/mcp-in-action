package com.ethanliang.mcp.cimd;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProtectedResourceController {
    @GetMapping("/api/hello")
    public String hello(HttpServletRequest req) {
        return "hello, " + req.getAttribute("clientName");
    }
}
