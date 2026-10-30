package com.dispatchflow.example.adapter.outbound.persistence;

import com.dispatchflow.example.domain.Size;
import com.dispatchflow.example.domain.Widget;
import com.dispatchflow.example.domain.WidgetId;
import com.dispatchflow.example.domain.WidgetRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Example: the adapter behind {@link WidgetRepository}. It maps widgets to rows and back. */
@Component
class WidgetPersistenceAdapter implements WidgetRepository {

    private final WidgetJpaRepository rows;

    WidgetPersistenceAdapter(WidgetJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(Widget widget) {
        rows.save(new WidgetEntity(
                widget.id().value(), widget.size().width(), widget.size().height()));
    }

    @Override
    public Optional<Widget> findById(WidgetId id) {
        return rows.findById(id.value()).map(WidgetPersistenceAdapter::toWidget);
    }

    private static Widget toWidget(WidgetEntity row) {
        return Widget.restore(new WidgetId(row.id()), new Size(row.width(), row.height()));
    }
}
