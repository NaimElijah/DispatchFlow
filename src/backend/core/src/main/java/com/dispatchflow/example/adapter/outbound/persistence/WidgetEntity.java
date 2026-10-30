package com.dispatchflow.example.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Example: a row of the {@code example_widget} table, which {@code V0_1__create_example_widget.sql} creates. */
@Entity
@Table(name = "example_widget")
class WidgetEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    /** For JPA only. */
    protected WidgetEntity() {}

    WidgetEntity(UUID id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
    }

    UUID id() {
        return id;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }
}
