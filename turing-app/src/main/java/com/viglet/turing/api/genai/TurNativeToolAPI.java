package com.viglet.turing.api.genai;

import java.util.Collection;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.genai.tool.TurNativeToolService.NativeToolGroup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST API to list native tool callings available in Turing.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@RestController
@RequestMapping("/api/native-tool")
@Tag(name = "Native Tools", description = "Native Tool Calling API")
public class TurNativeToolAPI {

    private final TurNativeToolService turNativeToolService;

    public TurNativeToolAPI(TurNativeToolService turNativeToolService) {
        this.turNativeToolService = turNativeToolService;
    }

    @Operation(summary = "List all native tool groups with their tools")
    @GetMapping
    public Collection<NativeToolGroup> list() {
        return turNativeToolService.getToolGroups();
    }
}
