package com.scut.wms.container;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/container-types")
public class ContainerTypeController {
    private final ContainerTypeService service;

    public ContainerTypeController(ContainerTypeService service) {
        this.service = service;
    }

    @GetMapping
    public List<ContainerType> listAll() {
        return service.listAll();
    }

    @PostMapping
    public ContainerType create(@Valid @RequestBody ContainerTypeRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ContainerType update(@PathVariable Long id, @Valid @RequestBody ContainerTypeRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/status")
    public void updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.updateStatus(id, body.get("status"));
    }
}
