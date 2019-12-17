package org.opencb.opencga.core.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import org.apache.commons.lang3.StringUtils;
import org.opencb.commons.datastore.core.ObjectMap;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

public abstract class ToolParams {
    private Map<String, Class<?>> internalPropertiesMap = null;

    public String toJson() {
        ObjectMapper objectMapper = getObjectMapper();
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ObjectMap toObjectMap() {
        return new ObjectMap(toJson());
    }

    public ObjectMap toObjectMap(Map<String, ?> other) {
        return toObjectMap().appendAll(other);
    }

    public Map<String, Object> toParams() {
        ObjectMap objectMap = toObjectMap();
        Map<String, Object> map = new HashMap<>(objectMap.size());
        addParams(map, objectMap);
        return map;
    }

    public Map<String, Object> toParams(ObjectMap otherParams) {
        Map<String, Object> map = toParams();
        addParams(map, otherParams);
        return map;
    }


    public void updateParams(Map<String, Object> params) {
        ObjectMapper objectMapper = getObjectMapper();
        try {
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            objectMapper.updateValue(this, params);
            params.putAll(this.toObjectMap());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> T fromParams(Class<T> clazz, Map<String, ?> params) {
        ObjectMapper objectMapper = getObjectMapper();
        return objectMapper.convertValue(params, clazz);
    }

    private static ObjectMapper getObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return objectMapper;
    }

    private void addParams(Map<String, Object> map, ObjectMap params) {
        if (internalPropertiesMap == null) {
            loadPropertiesMap();
        }
        for (String key : params.keySet()) {
            Class<?> fieldClass = internalPropertiesMap.get(key);
            String value = params.getString(key);
            if (StringUtils.isNotEmpty(value)) {
                // native boolean fields are "flags"
                if (fieldClass == boolean.class) {
                    if (value.equals("true")) {
                        map.put(key, "");
                    }
                } else if (fieldClass != null &&  Map.class.isAssignableFrom(fieldClass)) {
                    map.put(key, params.getMap(key));
                } else {
                    map.put(key, value);
                }
            }
        }
    }

    private void loadPropertiesMap() {
        ObjectMapper objectMapper = getObjectMapper();
        BeanDescription beanDescription = objectMapper.getSerializationConfig().introspect(objectMapper.constructType(this.getClass()));
        internalPropertiesMap = new HashMap<>(beanDescription.findProperties().size());
        for (BeanPropertyDefinition property : beanDescription.findProperties()) {
            internalPropertiesMap.put(property.getName(), property.getRawPrimaryType());
        }
    }
}
