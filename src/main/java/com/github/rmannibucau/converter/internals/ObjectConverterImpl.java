package com.github.rmannibucau.converter.internals;

import com.github.rmannibucau.converter.api.ObjectConverter;
import org.apache.deltaspike.core.api.provider.BeanProvider;

import javax.enterprise.inject.spi.Bean;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ObjectConverterImpl<FROM, TO> implements ObjectConverter<FROM, TO> {
    private final Bean<Object> bean;
    private final Method convertMethod;
    private final Map<String, Field> fieldCache = new ConcurrentHashMap<String, Field>();

    public ObjectConverterImpl(final Bean<Object> bean, final Method convertMethod) {
        this.bean = bean;
        this.convertMethod = convertMethod;
    }

    @Override
    public TO to(final FROM from) {
        if (from == null) {
            return null;
        }

        try {
            return (TO) convertMethod.invoke(instance(), from);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Object instance() {
        if (bean == null) { // static method
            return null;
        }
        return BeanProvider.getContextualReference(bean.getBeanClass(), bean.getQualifiers().toArray(new Annotation[bean.getQualifiers().size()]));
    }

    @Override
    public Collection<TO> to(final Collection<FROM> froms) {
        if (froms == null) {
            return null;
        }

        final Collection<TO> out = new ArrayList<TO>();
        for (FROM from : froms) {
            out.add(to(from));
        }
        return out;
    }

    @Override
    public <K> Map<K, TO> to(final Collection<FROM> froms, final String keyAttribute) {
        if (froms == null) {
            return null;
        }

        final Map<K, TO> out = new HashMap<K, TO>();
        for (FROM from : froms) {
            out.put((K) attribute(keyAttribute, from), to(from));
        }
        return out;
    }

    private Object attribute(final String keyAttribute, final FROM from) {
        Class<?> current = from.getClass();
        final String key = current.getName() + '#' + keyAttribute;
        Field field = fieldCache.get(key);

        if (field == null) {
            while (current != null && !Object.class.equals(current)) {
                try {
                    field = current.getDeclaredField(keyAttribute);
                    field.setAccessible(true);
                } catch (NoSuchFieldException e) {
                    // no-op
                }
                current = current.getSuperclass();
            }
            if (field == null) {
                throw new IllegalArgumentException("Can't find field " + keyAttribute + " in " + from.getClass().getName());
            }
            fieldCache.put(key, field);
        }

        try {
            return field.get(from);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
