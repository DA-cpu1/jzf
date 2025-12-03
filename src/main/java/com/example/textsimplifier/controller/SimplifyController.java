package com.example.textsimplifier.controller;

import com.example.textsimplifier.model.SimplifyRequest;
import com.example.textsimplifier.model.SimplifyResponse;
import com.example.textsimplifier.service.SimplifierService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SimplifyController {

    private final SimplifierService service;

    public SimplifyController(SimplifierService service) {
        this.service = service;
    }

    @PostMapping(path = "/simplify", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SimplifyResponse simplify(@RequestBody SimplifyRequest req) {
        return service.simplify(req);
    }
}