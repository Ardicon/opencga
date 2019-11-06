/*
 * Copyright 2015-2017 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.storage.core;

import org.apache.commons.lang3.StringUtils;
import org.opencb.opencga.storage.core.alignment.AlignmentStorageEngine;
import org.opencb.opencga.storage.core.config.StorageConfiguration;
import org.opencb.opencga.storage.core.exceptions.StorageEngineException;
import org.opencb.opencga.storage.core.variant.VariantStorageEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates StorageManagers by reflexion.
 * The StorageEngine's className is read from <opencga-home>/conf/storage-configuration.yml
 */
public final class StorageEngineFactory {

    private static StorageEngineFactory storageEngineFactory;
    private static StorageConfiguration storageConfigurationDefault;
    private StorageConfiguration storageConfiguration;

    private Map<String, AlignmentStorageEngine> alignmentStorageManagerMap = new ConcurrentHashMap<>();
    private Map<String, VariantStorageEngine> variantStorageManagerMap = new ConcurrentHashMap<>();
    protected static Logger logger = LoggerFactory.getLogger(StorageConfiguration.class);

    private StorageEngineFactory(StorageConfiguration storageConfiguration) {
        this.storageConfiguration = storageConfiguration;
    }

    public static void configure(StorageConfiguration configuration) {
        storageConfigurationDefault = configuration;
        if (storageEngineFactory != null) {
            storageEngineFactory.storageConfiguration = configuration;
        }
    }

    private enum Type {
        ALIGNMENT,
        VARIANT
    }

    @Deprecated
    public static StorageEngineFactory get() {
//        if (storageConfigurationDefault == null) {
//            try {
//                storageConfigurationDefault = StorageConfiguration.load();
//            } catch (IOException e) {
//                logger.error("Unable to get StorageManagerFactory");
//                throw new UncheckedIOException(e);
//            }
//        }
        return get(null);
    }

    public static StorageEngineFactory get(StorageConfiguration storageConfiguration) {
        if (storageEngineFactory == null) {
            if (storageConfiguration != null) {
                configure(storageConfiguration);
            } else {
                storageConfiguration = storageConfigurationDefault;
            }
            Objects.requireNonNull(storageConfiguration, "Storage configuration needed");
            storageEngineFactory = new StorageEngineFactory(storageConfiguration);
            return storageEngineFactory;

        }
        return storageEngineFactory;
    }

    public AlignmentStorageEngine getAlignmentStorageEngine(String dbName)
            throws StorageEngineException {
        return getStorageEngine(Type.ALIGNMENT, null, AlignmentStorageEngine.class, alignmentStorageManagerMap, dbName);
    }

    public VariantStorageEngine getVariantStorageEngine() throws StorageEngineException {
        return getVariantStorageEngine(null, "");
    }

    public VariantStorageEngine getVariantStorageEngine(String storageEngineName, String dbName)
            throws StorageEngineException {
        return getStorageEngine(Type.VARIANT, storageEngineName, VariantStorageEngine.class, variantStorageManagerMap, dbName);
    }

    private synchronized <T extends StorageEngine> T getStorageEngine(Type type, String storageEngineId, Class<T> superClass,
                                                                      Map<String, T> storageManagerMap, String dbName)
            throws StorageEngineException {
        /*
         * This new block of code use new StorageConfiguration system, it must replace older one
         */
        if (this.storageConfiguration == null) {
            throw new NullPointerException();
        }
        if (StringUtils.isEmpty(storageEngineId)) {
            storageEngineId = getDefaultStorageEngineId();
        }
        if (dbName == null) {
            dbName = "";
        }
        String key = buildStorageEngineKey(storageEngineId, dbName);
        if (!storageManagerMap.containsKey(key)) {
            String clazz;
            switch (type) {
                case ALIGNMENT:
                    clazz = this.storageConfiguration.getAlignment().getEngine();
                    break;
                case VARIANT:
                    clazz = this.storageConfiguration.getVariantEngine(storageEngineId).getEngine();
                    break;
                default:
                    throw new IllegalArgumentException("Unknown type " + type);
            }

            try {
                T storageEngine = Class.forName(clazz).asSubclass(superClass).newInstance();
                storageEngine.setConfiguration(this.storageConfiguration, storageEngineId, dbName);

                storageManagerMap.put(key, storageEngine);
                return storageEngine;
            } catch (IllegalAccessException | InstantiationException | ClassNotFoundException e) {
                throw new StorageEngineException("Error instantiating StorageEngine '" + clazz + "'", e);
            }
        } else {
            return storageManagerMap.get(key);
        }
    }

    private String buildStorageEngineKey(String storageEngineName, String dbName) {
        return storageEngineName + '_' + dbName;
    }

    public String getDefaultStorageEngineId() {
        return storageConfiguration.getVariant().getDefaultEngine();
    }

    public StorageConfiguration getStorageConfiguration() {
        return storageConfiguration;
    }

    public void registerVariantStorageEngine(VariantStorageEngine variantStorageEngine) {
        String key = buildStorageEngineKey(variantStorageEngine.getStorageEngineId(), variantStorageEngine.dbName);
        variantStorageManagerMap.put(key, variantStorageEngine);
    }

    public void unregisterVariantStorageEngine(String storageEngineId) {
        Map<String, VariantStorageEngine> map = this.variantStorageManagerMap;
        unregister(storageEngineId, map);
    }

    public void registerAlignmentStorageEngine(AlignmentStorageEngine alignmentStorageEngine) {
        String key = buildStorageEngineKey(alignmentStorageEngine.getStorageEngineId(), alignmentStorageEngine.dbName);
        alignmentStorageManagerMap.put(key, alignmentStorageEngine);
    }

    public void unregisterAlignmentStorageManager(String storageEngineId) {
        unregister(storageEngineId, alignmentStorageManagerMap);
    }

    private <T extends StorageEngine> void unregister(String storageEngineId, Map<String, T> map) {
        for (Iterator<Map.Entry<String, T>> iterator = map.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<String, T> entry = iterator.next();
            if (entry.getKey().startsWith(storageEngineId + '_')) {
                iterator.remove();
            }
        }
    }
}
