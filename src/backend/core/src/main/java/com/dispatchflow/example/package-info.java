/**
 * <b>Example only.</b> A placeholder bounded context that shows the structure every real context in
 * {@code core} follows (ADR-001). {@code Widget} and everything around it are dummy names, not
 * DispatchFlow concepts. The team designs the real contexts in issue #5. When the first one exists,
 * delete this package, its tests, its migration ({@code V0_1__create_example_widget.sql}) and its
 * operations in {@code contracts/client-api/v1}.
 *
 * <p>Dependencies point inward only:
 *
 * <ul>
 *   <li>{@code api}: the context's public surface. Other contexts, and this context's inbound
 *       adapters, use nothing else.
 *   <li>{@code domain}: the model and its invariants. It imports no framework, I/O or transport
 *       type.
 *   <li>{@code application}: the service layer. Its use cases implement {@code api} on top of
 *       {@code domain}.
 *   <li>{@code adapter}: the connections to the outside world. Inbound adapters call {@code api};
 *       outbound adapters implement the ports {@code domain} declares.
 * </ul>
 */
package com.dispatchflow.example;
