package com.dispatchflow.example.adapter.inbound.web;

import com.dispatchflow.example.api.CreateWidgetCommand;
import com.dispatchflow.example.api.ExampleApi;
import com.dispatchflow.example.api.WidgetView;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Example: serves {@code /api/v1/examples/widgets}, the {@code createExampleWidget} and {@code
 * getExampleWidget} operations. The client API carries its major version in the path (ADR-006).
 */
@RestController
@RequestMapping("/api/v1/examples/widgets")
class WidgetController {

    private final ExampleApi example;

    WidgetController(ExampleApi example) {
        this.example = example;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<WidgetResponse> create(@Valid @RequestBody CreateWidgetRequest request) {
        WidgetView widget = example.create(new CreateWidgetCommand(request.width(), request.height()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{widgetId}")
                .buildAndExpand(widget.id())
                .toUri();
        return ResponseEntity.created(location).body(WidgetResponse.from(widget));
    }

    @GetMapping(value = "/{widgetId}", produces = MediaType.APPLICATION_JSON_VALUE)
    WidgetResponse get(@PathVariable UUID widgetId) {
        return example.find(widgetId).map(WidgetResponse::from).orElseThrow(() -> notFound(widgetId));
    }

    private static ErrorResponseException notFound(UUID widgetId) {
        return new ErrorResponseException(
                HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No widget has the id " + widgetId),
                null);
    }
}
