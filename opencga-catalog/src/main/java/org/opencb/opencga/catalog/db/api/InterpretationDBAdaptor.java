package org.opencb.opencga.catalog.db.api;

import org.apache.commons.collections.map.LinkedMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryParam;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.core.models.Interpretation;
import org.opencb.opencga.core.response.OpenCGAResult;

import java.util.Map;

import static org.opencb.commons.datastore.core.QueryParam.Type.*;

public interface InterpretationDBAdaptor extends DBAdaptor<Interpretation> {

    enum QueryParams implements QueryParam {
        ID("id", TEXT, ""),
        UID("uid", INTEGER, ""),
        UUID("uuid", TEXT, ""),
        CLINICAL_ANALYSIS("clinicalAnalysisId", TEXT, ""),
        DESCRIPTION("description", TEXT, ""),
        STATUS("status", TEXT, ""),
        VERSION("version", INTEGER, ""),
        CREATION_DATE("creationDate", DATE, ""),
        MODIFICATION_DATE("modificationDate", DATE, ""),

        PANELS("panels", TEXT_ARRAY, ""),
        SOFTWARE("software", TEXT_ARRAY, ""),
        ANALYST("analyst", TEXT_ARRAY, ""),
        DEPENDENCIES("dependencies", TEXT_ARRAY, ""),
        FILTERS("filters", TEXT_ARRAY, ""),
        REPORTED_VARIANTS("reportedVariants", TEXT_ARRAY, ""),
        REPORTED_LOW_COVERAGE("reportedLowCoverages", TEXT_ARRAY, ""),
        COMMENTS("comments", TEXT_ARRAY, ""),


        ATTRIBUTES("attributes", TEXT, ""), // "Format: <key><operation><stringValue> where <operation> is [<|<=|>|>=|==|!=|~|!~]"
        NATTRIBUTES("nattributes", DECIMAL, ""), // "Format: <key><operation><numericalValue> where <operation> is [<|<=|>|>=|==|!=|~|!~]"
        BATTRIBUTES("battributes", BOOLEAN, ""), // "Format: <key><operation><true|false> where <operation> is [==|!=]"

        STUDY_UID("studyUid", INTEGER_ARRAY, ""),
        STUDY("study", INTEGER_ARRAY, ""); // Alias to studyId in the database. Only for the webservices.

        private static Map<String, QueryParams> map;
        static {
            map = new LinkedMap();
            for (QueryParams params : QueryParams.values()) {
                map.put(params.key(), params);
            }
        }

        private final String key;
        private Type type;
        private String description;

        QueryParams(String key, Type type, String description) {
            this.key = key;
            this.type = type;
            this.description = description;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public Type type() {
            return type;
        }

        @Override
        public String description() {
            return description;
        }

        public static Map<String, QueryParams> getMap() {
            return map;
        }

        public static QueryParams getParam(String key) {
            return map.get(key);
        }
    }

    default boolean exists(long interpretationId) throws CatalogDBException {
        return count(new Query(QueryParams.UID.key(), interpretationId)).getNumMatches() > 0;
    }

    default void checkId(long interpretationId) throws CatalogDBException {
        if (interpretationId < 0) {
            throw CatalogDBException.newInstance("Interpretation id '{}' is not valid: ", interpretationId);
        }

        if (!exists(interpretationId)) {
            throw CatalogDBException.newInstance("Interpretation id '{}' does not exist", interpretationId);
        }
    }

    OpenCGAResult nativeInsert(Map<String, Object> interpretation, String userId) throws CatalogDBException;

    OpenCGAResult insert(long studyId, Interpretation interpretation, QueryOptions options) throws CatalogDBException;

    OpenCGAResult<Interpretation> get(long interpretationUid, QueryOptions options) throws CatalogDBException;

    OpenCGAResult<Interpretation> get(long studyUid, String interpretationId, QueryOptions options) throws CatalogDBException;

    long getStudyId(long interpretationId) throws CatalogDBException;

}
