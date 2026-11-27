package com.dispatchflow.archfixture.theta.application.service.audit;

import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import org.springframework.transaction.annotation.Transactional;

/** The audit port's implementation, which a class may hold by its own type. */
@Transactional
public class AuditLogService implements AuditLog {

    @Override
    public void succeeded() {}

    @Override
    public void refused() {}
}
