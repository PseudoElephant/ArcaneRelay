package com.pseudoelephant.arcanerelay.core.adapters;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.event.EntityEventType;
import com.hypixel.hytale.component.event.EntityHolderEventType;
import com.hypixel.hytale.component.event.WorldEventType;
import com.hypixel.hytale.component.system.EcsEvent;

/**
 * Base adapter that delegates all ComponentAccessor method calls to an underlying delegate.
 */
public abstract class AbstractComponentAccessorAdapter<T> implements ComponentAccessor<T> {
    protected final ComponentAccessor<T> delegate;

    protected AbstractComponentAccessorAdapter(@Nonnull ComponentAccessor<T> delegate) {
        this.delegate = delegate;
    }

    @Nullable
    @Override
    public <C extends Component<T>> C getComponent(@Nonnull Ref<T> ref, @Nonnull ComponentType<T, C> componentType) {
        return delegate.getComponent(ref, componentType);
    }

    @Nonnull
    @Override
    public <C extends Component<T>> C ensureAndGetComponent(@Nonnull Ref<T> ref, @Nonnull ComponentType<T, C> componentType) {
        return delegate.ensureAndGetComponent(ref, componentType);
    }

    @Nonnull
    @Override
    public Archetype<T> getArchetype(@Nonnull Ref<T> ref) {
        return delegate.getArchetype(ref);
    }

    @Nonnull
    @Override
    public <R extends Resource<T>> R getResource(@Nonnull ResourceType<T, R> resourceType) {
        return delegate.getResource(resourceType);
    }

    @Nonnull
    @Override
    public T getExternalData() {
        return delegate.getExternalData();
    }

    @Override
    public <C extends Component<T>> void putComponent(@Nonnull Ref<T> ref, @Nonnull ComponentType<T, C> componentType, @Nonnull C component) {
        delegate.putComponent(ref, componentType, component);
    }

    @Override
    public <C extends Component<T>> void addComponent(@Nonnull Ref<T> ref, @Nonnull ComponentType<T, C> componentType, @Nonnull C component) {
        delegate.addComponent(ref, componentType, component);
    }

    @Override
    public <C extends Component<T>> C addComponent(@Nonnull Ref<T> ref, @Nonnull ComponentType<T, C> componentType) {
        return delegate.addComponent(ref, componentType);
    }

    @Override
    public Ref<T>[] addEntities(@Nonnull Holder<T>[] holders, @Nonnull AddReason reason) {
        return delegate.addEntities(holders, reason);
    }

    @Nullable
    @Override
    public Ref<T> addEntity(@Nonnull Holder<T> holder, @Nonnull AddReason reason) {
        return delegate.addEntity(holder, reason);
    }

    @Nonnull
    @Override
    public Holder<T> removeEntity(@Nonnull Ref<T> ref, @Nonnull Holder<T> holder, @Nonnull RemoveReason reason) {
        return delegate.removeEntity(ref, holder, reason);
    }

    @Override
    public <C extends Component<T>> void removeComponent(@Nonnull Ref<T> ref, @Nonnull ComponentType<T, C> componentType) {
        delegate.removeComponent(ref, componentType);
    }

    @Override
    public <C extends Component<T>> void tryRemoveComponent(@Nonnull Ref<T> ref, @Nonnull ComponentType<T, C> componentType) {
        delegate.tryRemoveComponent(ref, componentType);
    }

    @Override
    public <Event extends EcsEvent> void invoke(@Nonnull Ref<T> ref, @Nonnull Event event) {
        delegate.invoke(ref, event);
    }

    @Override
    public <Event extends EcsEvent> void invoke(@Nonnull EntityEventType<T, Event> eventType, @Nonnull Ref<T> ref, @Nonnull Event event) {
        delegate.invoke(eventType, ref, event);
    }

    @Override
    public <Event extends EcsEvent> void invoke(@Nonnull Event event) {
        delegate.invoke(event);
    }

    @Override
    public <Event extends EcsEvent> void invoke(@Nonnull WorldEventType<T, Event> eventType, @Nonnull Event event) {
        delegate.invoke(eventType, event);
    }

    @Override
    public <Event extends EcsEvent> void invoke(@Nonnull Holder<T> holder, @Nonnull Event event) {
        delegate.invoke(holder, event);
    }

    @Override
    public <Event extends EcsEvent> void invoke(@Nonnull EntityHolderEventType<T, Event> entityHolderEventType, @Nonnull Holder<T> holder, @Nonnull Event event) {
        delegate.invoke(entityHolderEventType, holder, event);
    }
}