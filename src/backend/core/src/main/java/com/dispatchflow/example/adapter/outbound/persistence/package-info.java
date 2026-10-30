/**
 * Example: an outbound adapter. It implements {@link
 * com.dispatchflow.example.domain.WidgetRepository} on PostgreSQL through Spring Data JPA. The JPA
 * entity lives only here: the domain never sees it, and this adapter maps between the two.
 */
package com.dispatchflow.example.adapter.outbound.persistence;
