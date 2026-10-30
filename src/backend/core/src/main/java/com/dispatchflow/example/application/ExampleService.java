package com.dispatchflow.example.application;

import com.dispatchflow.example.api.CreateWidgetCommand;
import com.dispatchflow.example.api.ExampleApi;
import com.dispatchflow.example.api.WidgetRejectedException;
import com.dispatchflow.example.api.WidgetView;
import com.dispatchflow.example.domain.InvalidWidgetException;
import com.dispatchflow.example.domain.Size;
import com.dispatchflow.example.domain.Widget;
import com.dispatchflow.example.domain.WidgetId;
import com.dispatchflow.example.domain.WidgetRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Example: implements {@link ExampleApi}. The class is package-private, so nothing outside the
 * context can depend on it instead of the interface.
 */
@Service
class ExampleService implements ExampleApi {

    private final WidgetRepository widgets;

    ExampleService(WidgetRepository widgets) {
        this.widgets = widgets;
    }

    @Override
    @Transactional
    public WidgetView create(CreateWidgetCommand command) {
        Widget widget = Widget.create(toSize(command));
        widgets.save(widget);
        return toView(widget);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WidgetView> find(UUID widgetId) {
        return widgets.findById(new WidgetId(widgetId)).map(ExampleService::toView);
    }

    // The domain's exception stays inside the context: callers see only the api's.
    private static Size toSize(CreateWidgetCommand command) {
        try {
            return new Size(command.width(), command.height());
        } catch (InvalidWidgetException rejected) {
            throw new WidgetRejectedException(rejected.getMessage(), rejected);
        }
    }

    private static WidgetView toView(Widget widget) {
        return new WidgetView(
                widget.id().value(), widget.size().width(), widget.size().height());
    }
}
