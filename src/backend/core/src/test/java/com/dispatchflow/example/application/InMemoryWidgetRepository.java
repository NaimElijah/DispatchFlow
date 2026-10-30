package com.dispatchflow.example.application;

import com.dispatchflow.example.domain.Widget;
import com.dispatchflow.example.domain.WidgetId;
import com.dispatchflow.example.domain.WidgetRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Example: a fake of our own port, so the use cases are tested without a database. */
class InMemoryWidgetRepository implements WidgetRepository {

    private final Map<WidgetId, Widget> widgets = new HashMap<>();

    @Override
    public void save(Widget widget) {
        widgets.put(widget.id(), widget);
    }

    @Override
    public Optional<Widget> findById(WidgetId id) {
        return Optional.ofNullable(widgets.get(id));
    }

    int size() {
        return widgets.size();
    }
}
