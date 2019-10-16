package org.opencb.opencga.catalog.managers;

import org.apache.commons.lang3.StringUtils;
import org.opencb.commons.datastore.core.Event;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.utils.ListUtils;
import org.opencb.opencga.catalog.audit.AuditManager;
import org.opencb.opencga.catalog.audit.AuditRecord;
import org.opencb.opencga.catalog.auth.authorization.AuthorizationManager;
import org.opencb.opencga.catalog.db.DBAdaptorFactory;
import org.opencb.opencga.catalog.db.api.ClinicalAnalysisDBAdaptor;
import org.opencb.opencga.catalog.db.api.DBIterator;
import org.opencb.opencga.catalog.db.api.InterpretationDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogAuthorizationException;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.io.CatalogIOManagerFactory;
import org.opencb.opencga.catalog.models.InternalGetDataResult;
import org.opencb.opencga.catalog.models.update.InterpretationUpdateParams;
import org.opencb.opencga.catalog.utils.Constants;
import org.opencb.opencga.catalog.utils.ParamUtils;
import org.opencb.opencga.catalog.utils.UUIDUtils;
import org.opencb.opencga.core.common.TimeUtils;
import org.opencb.opencga.core.config.Configuration;
import org.opencb.opencga.core.models.ClinicalAnalysis;
import org.opencb.opencga.core.models.Interpretation;
import org.opencb.opencga.core.models.Job;
import org.opencb.opencga.core.models.Study;
import org.opencb.opencga.core.models.acls.permissions.ClinicalAnalysisAclEntry;
import org.opencb.opencga.core.results.OpenCGAResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

public class InterpretationManager extends ResourceManager<Interpretation> {

    protected static Logger logger = LoggerFactory.getLogger(InterpretationManager.class);

    private UserManager userManager;
    private StudyManager studyManager;

    public static final QueryOptions INCLUDE_INTERPRETATION_IDS = new QueryOptions(QueryOptions.INCLUDE, Arrays.asList(
            InterpretationDBAdaptor.QueryParams.ID.key(), InterpretationDBAdaptor.QueryParams.UID.key(),
            InterpretationDBAdaptor.QueryParams.UUID.key(), InterpretationDBAdaptor.QueryParams.VERSION.key()));

    public InterpretationManager(AuthorizationManager authorizationManager, AuditManager auditManager, CatalogManager catalogManager,
                                 DBAdaptorFactory catalogDBAdaptorFactory, CatalogIOManagerFactory ioManagerFactory,
                                 Configuration configuration) {
        super(authorizationManager, auditManager, catalogManager, catalogDBAdaptorFactory, ioManagerFactory, configuration);

        this.userManager = catalogManager.getUserManager();
        this.studyManager = catalogManager.getStudyManager();
    }

    @Override
    AuditRecord.Resource getEntity() {
        return AuditRecord.Resource.INTERPRETATION;
    }

    @Override
    OpenCGAResult<Interpretation> internalGet(long studyUid, String entry, @Nullable Query query, QueryOptions options, String user)
            throws CatalogException {
        ParamUtils.checkIsSingleID(entry);
        Query queryCopy = query == null ? new Query() : new Query(query);
        queryCopy.put(InterpretationDBAdaptor.QueryParams.STUDY_UID.key(), studyUid);

        if (UUIDUtils.isOpenCGAUUID(entry)) {
            queryCopy.put(InterpretationDBAdaptor.QueryParams.UUID.key(), entry);
        } else {
            queryCopy.put(InterpretationDBAdaptor.QueryParams.ID.key(), entry);
        }

        QueryOptions queryOptions = new QueryOptions(ParamUtils.defaultObject(options, QueryOptions::new));

//        QueryOptions options = new QueryOptions(QueryOptions.INCLUDE, Arrays.asList(
//                InterpretationDBAdaptor.QueryParams.UUID.key(), InterpretationDBAdaptor.QueryParams.CLINICAL_ANALYSIS.key(),
//                InterpretationDBAdaptor.QueryParams.UID.key(), InterpretationDBAdaptor.QueryParams.STUDY_UID.key(),
//                InterpretationDBAdaptor.QueryParams.ID.key(), InterpretationDBAdaptor.QueryParams.STATUS.key()));
        OpenCGAResult<Interpretation> interpretationDataResult = interpretationDBAdaptor.get(queryCopy, queryOptions, user);
        if (interpretationDataResult.getNumResults() == 0) {
            interpretationDataResult = interpretationDBAdaptor.get(queryCopy, queryOptions);
            if (interpretationDataResult.getNumResults() == 0) {
                throw new CatalogException("Interpretation " + entry + " not found");
            } else {
                throw new CatalogAuthorizationException("Permission denied. " + user + " is not allowed to see the interpretation "
                        + entry);
            }
        } else if (interpretationDataResult.getNumResults() > 1) {
            throw new CatalogException("More than one interpretation found based on " + entry);
        } else {
            // We perform this query to check permissions because interpretations doesn't have ACLs
            try {
                catalogManager.getClinicalAnalysisManager().internalGet(studyUid,
                        interpretationDataResult.first().getClinicalAnalysisId(),
                        ClinicalAnalysisManager.INCLUDE_CLINICAL_IDS, user);
            } catch (CatalogException e) {
                throw new CatalogAuthorizationException("Permission denied. " + user + " is not allowed to see the interpretation "
                        + entry);
            }

            return interpretationDataResult;
        }
    }

    @Override
    InternalGetDataResult<Interpretation> internalGet(long studyUid, List<String> entryList, @Nullable Query query, QueryOptions options,
                                                      String user, boolean ignoreException) throws CatalogException {
        if (ListUtils.isEmpty(entryList)) {
            throw new CatalogException("Missing interpretation entries.");
        }
        List<String> uniqueList = ListUtils.unique(entryList);

        QueryOptions queryOptions = new QueryOptions(ParamUtils.defaultObject(options, QueryOptions::new));
        Query queryCopy = query == null ? new Query() : new Query(query);
        queryCopy.put(InterpretationDBAdaptor.QueryParams.STUDY_UID.key(), studyUid);

        Function<Interpretation, String> interpretationStringFunction = Interpretation::getId;
        InterpretationDBAdaptor.QueryParams idQueryParam = null;
        for (String entry : uniqueList) {
            InterpretationDBAdaptor.QueryParams param = InterpretationDBAdaptor.QueryParams.ID;
            if (UUIDUtils.isOpenCGAUUID(entry)) {
                param = InterpretationDBAdaptor.QueryParams.UUID;
                interpretationStringFunction = Interpretation::getUuid;
            }
            if (idQueryParam == null) {
                idQueryParam = param;
            }
            if (idQueryParam != param) {
                throw new CatalogException("Found uuids and ids in the same query. Please, choose one or do two different queries.");
            }
        }
        queryCopy.put(idQueryParam.key(), uniqueList);

        // Ensure the field by which we are querying for will be kept in the results
        queryOptions = keepFieldInQueryOptions(queryOptions, idQueryParam.key());

        OpenCGAResult<Interpretation> interpretationDataResult = interpretationDBAdaptor.get(queryCopy, queryOptions, user);

        if (interpretationDataResult.getNumResults() != uniqueList.size() && !ignoreException) {
            throw CatalogException.notFound("interpretations",
                    getMissingFields(uniqueList, interpretationDataResult.getResults(), interpretationStringFunction));
        }

        ArrayList<Interpretation> interpretationList = new ArrayList<>(interpretationDataResult.getResults());
        Iterator<Interpretation> iterator = interpretationList.iterator();
        while (iterator.hasNext()) {
            Interpretation interpretation = iterator.next();
            // Check if the user has access to the corresponding clinical analysis
            try {
                catalogManager.getClinicalAnalysisManager().internalGet(studyUid,
                        interpretation.getClinicalAnalysisId(), ClinicalAnalysisManager.INCLUDE_CLINICAL_IDS, user);
            } catch (CatalogAuthorizationException e) {
                if (ignoreException) {
                    // Remove interpretation. User will not have permissions
                    iterator.remove();
                } else {
                    throw new CatalogAuthorizationException("Permission denied. " + user + " is not allowed to see some or none of the"
                            + " interpretations", e);
                }
            }
        }

        interpretationDataResult.setResults(interpretationList);
        interpretationDataResult.setNumResults(interpretationList.size());
        interpretationDataResult.setNumMatches(interpretationList.size());

        return keepOriginalOrder(uniqueList, interpretationStringFunction, interpretationDataResult, ignoreException, false);
    }

    public OpenCGAResult<Job> queue(String studyStr, String interpretationTool, String clinicalAnalysisId, List<String> panelIds,
                                 ObjectMap analysisOptions, String token) throws CatalogException {
        String userId = userManager.getUserId(token);
        Study study = studyManager.resolveId(studyStr, userId);

        ClinicalAnalysis clinicalAnalysis = catalogManager.getClinicalAnalysisManager().internalGet(study.getUid(), clinicalAnalysisId,
                ClinicalAnalysisManager.INCLUDE_CLINICAL_IDS, userId).first();

        authorizationManager.checkClinicalAnalysisPermission(study.getUid(), clinicalAnalysis.getUid(), userId,
                ClinicalAnalysisAclEntry.ClinicalAnalysisPermissions.UPDATE);

        // Queue job
        Map<String, String> params = new HashMap<>();
        params.put("includeLowCoverage", analysisOptions.getString("includeLowCoverage"));
        params.put("maxLowCoverage", analysisOptions.getString("maxLowCoverage"));
        params.put("includeNoTier", analysisOptions.getString("includeNoTier"));
        params.put("clinicalAnalysisId", clinicalAnalysisId);
        params.put("panelIds", StringUtils.join(panelIds, ","));

        ObjectMap attributes = new ObjectMap();
        attributes.putIfNotNull(Job.OPENCGA_STUDY, study.getFqn());

        return catalogManager.getJobManager().queue(studyStr, "Interpretation analysis", "opencga-analysis", "",
                "interpretation " + interpretationTool, Job.Type.ANALYSIS, params, Collections.emptyList(), Collections.emptyList(), null,
                attributes, token);
    }

    public OpenCGAResult<Interpretation> create(String studyStr, Interpretation entry, QueryOptions options, String sessionId)
            throws CatalogException {
        if (StringUtils.isEmpty(entry.getClinicalAnalysisId())) {
            throw new IllegalArgumentException("Please call to create passing a clinical analysis id");
        }
        return create(studyStr, entry.getClinicalAnalysisId(), entry, options, sessionId);
    }

    public OpenCGAResult<Interpretation> create(String studyStr, String clinicalAnalysisStr, Interpretation interpretation,
                                             QueryOptions options, String token) throws CatalogException {
        // We check if the user can create interpretations in the clinical analysis
        String userId = userManager.getUserId(token);
        Study study = studyManager.resolveId(studyStr, userId);

        ObjectMap auditParams = new ObjectMap()
                .append("study", studyStr)
                .append("clinicalAnalysis", clinicalAnalysisStr)
                .append("interpretation", interpretation)
                .append("options", options)
                .append("token", token);

        try {
            ClinicalAnalysis clinicalAnalysis = catalogManager.getClinicalAnalysisManager().internalGet(study.getUid(), clinicalAnalysisStr,
                    ClinicalAnalysisManager.INCLUDE_CLINICAL_IDS, userId).first();

            authorizationManager.checkClinicalAnalysisPermission(study.getUid(), clinicalAnalysis.getUid(),
                    userId, ClinicalAnalysisAclEntry.ClinicalAnalysisPermissions.UPDATE);

            options = ParamUtils.defaultObject(options, QueryOptions::new);
            ParamUtils.checkObj(interpretation, "clinicalAnalysis");
            ParamUtils.checkAlias(interpretation.getId(), "id");

            interpretation.setClinicalAnalysisId(clinicalAnalysis.getId());

            interpretation.setCreationDate(TimeUtils.getTime());
            interpretation.setDescription(ParamUtils.defaultString(interpretation.getDescription(), ""));
            interpretation.setStatus(org.opencb.biodata.models.clinical.interpretation.Interpretation.Status.NOT_REVIEWED);
            interpretation.setAttributes(ParamUtils.defaultObject(interpretation.getAttributes(), Collections.emptyMap()));

            interpretation.setUuid(UUIDUtils.generateOpenCGAUUID(UUIDUtils.Entity.INTERPRETATION));

            OpenCGAResult result = interpretationDBAdaptor.insert(study.getUid(), interpretation, options);
            OpenCGAResult<Interpretation> queryResult = interpretationDBAdaptor.get(study.getUid(), interpretation.getId(),
                    QueryOptions.empty());
            queryResult.setTime(result.getTime() + queryResult.getTime());

            // Now, we add the interpretation to the clinical analysis
            ObjectMap parameters = new ObjectMap();
            parameters.put(ClinicalAnalysisDBAdaptor.QueryParams.INTERPRETATIONS.key(), Collections.singletonList(queryResult.first()));
            QueryOptions queryOptions = new QueryOptions();
            Map<String, Object> actionMap = new HashMap<>();
            actionMap.put(ClinicalAnalysisDBAdaptor.QueryParams.INTERPRETATIONS.key(), ParamUtils.UpdateAction.ADD.name());
            queryOptions.put(Constants.ACTIONS, actionMap);
            clinicalDBAdaptor.update(clinicalAnalysis.getUid(), parameters, queryOptions);

            auditManager.auditCreate(userId, AuditRecord.Resource.INTERPRETATION, interpretation.getId(), "", study.getId(),
                    study.getUuid(), auditParams, new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));

            return queryResult;
        } catch (CatalogException e) {
            auditManager.auditCreate(userId, AuditRecord.Resource.INTERPRETATION, interpretation.getId(), "", study.getId(),
                    study.getUuid(), auditParams, new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }
    }

    public OpenCGAResult<Interpretation> update(String studyStr, Query query, InterpretationUpdateParams updateParams, QueryOptions options,
                                             String token) throws CatalogException {
        return update(studyStr, query, updateParams, false, options, token);
    }

    public OpenCGAResult<Interpretation> update(String studyStr, Query query, InterpretationUpdateParams updateParams,
                                                boolean ignoreException, QueryOptions options, String token) throws CatalogException {
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        String userId = userManager.getUserId(token);
        Study study = studyManager.resolveId(studyStr, userId);

        String operationId = UUIDUtils.generateOpenCGAUUID(UUIDUtils.Entity.AUDIT);

        ObjectMap auditParams = new ObjectMap()
                .append("study", studyStr)
                .append("query", query)
                .append("updateParams", updateParams != null ? updateParams.getUpdateMap() : null)
                .append("ignoreException", ignoreException)
                .append("options", options)
                .append("token", token);

        Query finalQuery = new Query(ParamUtils.defaultObject(query, Query::new));

        DBIterator<Interpretation> iterator;
        try {
            finalQuery.append(InterpretationDBAdaptor.QueryParams.STUDY_UID.key(), study.getUid());
            iterator = interpretationDBAdaptor.iterator(finalQuery, INCLUDE_INTERPRETATION_IDS, userId);
        } catch (CatalogException e) {
            auditManager.auditUpdate(operationId, userId, AuditRecord.Resource.INTERPRETATION, "", "", study.getId(), study.getUuid(),
                    auditParams, new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }

        OpenCGAResult<Interpretation> result = OpenCGAResult.empty();
        while (iterator.hasNext()) {
            Interpretation interpretation = iterator.next();
            try {
                OpenCGAResult writeResult = update(study, interpretation, updateParams, options, userId);
                auditManager.auditUpdate(operationId, userId, AuditRecord.Resource.INTERPRETATION, interpretation.getId(),
                        interpretation.getUuid(), study.getId(), study.getUuid(), auditParams,
                        new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));

                result.append(writeResult);
            } catch (CatalogException e) {
                Event event = new Event(Event.Type.ERROR, interpretation.getId(), e.getMessage());
                result.getEvents().add(event);

                logger.error("Cannot update interpretation {}: {}", interpretation.getId(), e.getMessage(), e);
                auditManager.auditUpdate(operationId, userId, AuditRecord.Resource.INTERPRETATION, interpretation.getId(),
                        interpretation.getUuid(), study.getId(), study.getUuid(), auditParams,
                        new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            }
        }

        return endResult(result, ignoreException);
    }

    public OpenCGAResult<Interpretation> update(String studyStr, String interpretationId, InterpretationUpdateParams updateParams,
                                             QueryOptions options, String token) throws CatalogException {
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        String userId = userManager.getUserId(token);
        Study study = studyManager.resolveId(studyStr, userId);

        String operationId = UUIDUtils.generateOpenCGAUUID(UUIDUtils.Entity.AUDIT);

        ObjectMap auditParams = new ObjectMap()
                .append("study", studyStr)
                .append("interpretationId", interpretationId)
                .append("updateParams", updateParams != null ? updateParams.getUpdateMap() : null)
                .append("options", options)
                .append("token", token);

        OpenCGAResult<Interpretation> result = OpenCGAResult.empty();
        String interpretationUuid = "";
        try {
            OpenCGAResult<Interpretation> tmpResult = internalGet(study.getUid(), interpretationId, INCLUDE_INTERPRETATION_IDS, userId);
            if (tmpResult.getNumResults() == 0) {
                throw new CatalogException("Interpretation '" + interpretationId + "' not found");
            }
            Interpretation interpretation = tmpResult.first();

            // We set the proper values for the audit
            interpretationId = interpretation.getId();
            interpretationUuid = interpretation.getUuid();

            OpenCGAResult writeResult = update(study, interpretation, updateParams, options, userId);
            result.append(writeResult);

            auditManager.auditUpdate(operationId, userId, AuditRecord.Resource.INTERPRETATION, interpretation.getId(),
                    interpretation.getUuid(), study.getId(), study.getUuid(), auditParams,
                    new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));
        } catch (CatalogException e) {
            Event event = new Event(Event.Type.ERROR, interpretationId, e.getMessage());
            result.getEvents().add(event);

            logger.error("Cannot update interpretation {}: {}", interpretationId, e.getMessage(), e);
            auditManager.auditUpdate(operationId, userId, AuditRecord.Resource.INTERPRETATION, interpretationId, interpretationUuid,
                    study.getId(), study.getUuid(), auditParams, new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            throw e;
        }

        return result;
    }

    /**
     * Update interpretations from catalog.
     *
     * @param studyStr   Study id in string format. Could be one of [id|user@aliasProject:aliasStudy|aliasProject:aliasStudy|aliasStudy].
     * @param interpretationIds List of interpretation ids. Could be either the id or uuid.
     * @param updateParams Data model filled only with the parameters to be updated.
     * @param options      QueryOptions object.
     * @param token  Session id of the user logged in.
     * @return A OpenCGAResult.
     * @throws CatalogException if there is any internal error, the user does not have proper permissions or a parameter passed does not
     *                          exist or is not allowed to be updated.
     */
    public OpenCGAResult<Interpretation> update(String studyStr, List<String> interpretationIds, InterpretationUpdateParams updateParams,
                                             QueryOptions options, String token) throws CatalogException {
        return update(studyStr, interpretationIds, updateParams, false, options, token);
    }

    public OpenCGAResult<Interpretation> update(String studyStr, List<String> interpretationIds, InterpretationUpdateParams updateParams,
                                                boolean ignoreException, QueryOptions options, String token) throws CatalogException {
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        String userId = userManager.getUserId(token);
        Study study = studyManager.resolveId(studyStr, userId);

        String operationId = UUIDUtils.generateOpenCGAUUID(UUIDUtils.Entity.AUDIT);

        ObjectMap auditParams = new ObjectMap()
                .append("study", studyStr)
                .append("interpretationIds", interpretationIds)
                .append("updateParams", updateParams != null ? updateParams.getUpdateMap() : null)
                .append("ignoreException", ignoreException)
                .append("options", options)
                .append("token", token);

        OpenCGAResult<Interpretation> result = OpenCGAResult.empty();
        for (String id : interpretationIds) {
            String interpretationId = id;
            String interpretationUuid = "";

            try {
                OpenCGAResult<Interpretation> tmpResult = internalGet(study.getUid(), interpretationId, INCLUDE_INTERPRETATION_IDS, userId);
                if (tmpResult.getNumResults() == 0) {
                    throw new CatalogException("Interpretation '" + interpretationId + "' not found");
                }
                Interpretation interpretation = tmpResult.first();

                // We set the proper values for the audit
                interpretationId = interpretation.getId();
                interpretationUuid = interpretation.getUuid();

                OpenCGAResult writeResult = update(study, interpretation, updateParams, options, userId);
                result.append(writeResult);

                auditManager.auditUpdate(operationId, userId, AuditRecord.Resource.INTERPRETATION, interpretation.getId(),
                        interpretation.getUuid(), study.getId(), study.getUuid(), auditParams,
                        new AuditRecord.Status(AuditRecord.Status.Result.SUCCESS));
            } catch (CatalogException e) {
                Event event = new Event(Event.Type.ERROR, id, e.getMessage());
                result.getEvents().add(event);

                logger.error("Cannot update interpretation {}: {}", interpretationId, e.getMessage(), e);
                auditManager.auditUpdate(operationId, userId, AuditRecord.Resource.INTERPRETATION, interpretationId, interpretationUuid,
                        study.getId(), study.getUuid(), auditParams, new AuditRecord.Status(AuditRecord.Status.Result.ERROR, e.getError()));
            }
        }

        return endResult(result, ignoreException);
    }

    private OpenCGAResult update(Study study, Interpretation interpretation, InterpretationUpdateParams updateParams, QueryOptions options,
                              String userId) throws CatalogException {
        // Check if user has permissions to write clinical analysis
        ClinicalAnalysis clinicalAnalysis = catalogManager.getClinicalAnalysisManager().internalGet(study.getUid(),
                interpretation.getClinicalAnalysisId(), ClinicalAnalysisManager.INCLUDE_CLINICAL_IDS, userId).first();
        authorizationManager.checkClinicalAnalysisPermission(study.getUid(), clinicalAnalysis.getUid(), userId,
                ClinicalAnalysisAclEntry.ClinicalAnalysisPermissions.UPDATE);

        ObjectMap parameters = updateParams.getUpdateMap();

        if (ListUtils.isNotEmpty(interpretation.getPrimaryFindings()) && (parameters.size() > 1
                || !parameters.containsKey(InterpretationDBAdaptor.QueryParams.REPORTED_VARIANTS.key()))) {
            throw new CatalogException("Interpretation already has reported variants. Only array of reported variants can be updated.");
        }

        if (parameters.containsKey(InterpretationDBAdaptor.QueryParams.ID.key())) {
            ParamUtils.checkAlias(parameters.getString(InterpretationDBAdaptor.QueryParams.ID.key()),
                    InterpretationDBAdaptor.QueryParams.ID.key());
        }

        return interpretationDBAdaptor.update(interpretation.getUid(), parameters, options);
    }

    @Override
    public DBIterator<Interpretation> iterator(String studyStr, Query query, QueryOptions options, String sessionId)
            throws CatalogException {
        return null;
    }

    @Override
    public OpenCGAResult<Interpretation> search(String studyId, Query query, QueryOptions options, String token)
            throws CatalogException {
        query = ParamUtils.defaultObject(query, Query::new);
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        String userId = catalogManager.getUserManager().getUserId(token);
        Study study = catalogManager.getStudyManager().resolveId(studyId, userId);

        query.append(InterpretationDBAdaptor.QueryParams.STUDY_UID.key(), study.getUid());

        OpenCGAResult<Interpretation> queryResult = interpretationDBAdaptor.get(query, options, userId);

        List<Interpretation> results = new ArrayList<>(queryResult.getResults().size());
        for (Interpretation interpretation : queryResult.getResults()) {
            if (StringUtils.isNotEmpty(interpretation.getClinicalAnalysisId())) {
                try {
                    catalogManager.getClinicalAnalysisManager().internalGet(study.getUid(),
                            interpretation.getClinicalAnalysisId(), ClinicalAnalysisManager.INCLUDE_CLINICAL_IDS,
                            userId);
                    results.add(interpretation);
                } catch (CatalogException e) {
                    logger.debug("Removing interpretation " + interpretation.getUuid() + " from results. User " + userId + " does not have "
                            + "proper permissions");
                }
            }
        }

        queryResult.setResults(results);
        queryResult.setNumMatches(results.size());
        queryResult.setNumResults(results.size());
        return queryResult;
    }

    @Override
    public OpenCGAResult<Interpretation> count(String studyId, Query query, String token) throws CatalogException {
        return null;
    }

    @Override
    public OpenCGAResult delete(String studyStr, List<String> ids, ObjectMap params, String token) throws CatalogException {
        return null;
    }

    @Override
    public OpenCGAResult delete(String studyStr, Query query, ObjectMap params, String sessionId) {
        return null;
    }

    @Override
    public OpenCGAResult rank(String studyStr, Query query, String field, int numResults, boolean asc, String sessionId)
            throws CatalogException {
        return null;
    }

    @Override
    public OpenCGAResult groupBy(@Nullable String studyStr, Query query, List<String> fields, QueryOptions options, String sessionId)
            throws CatalogException {
        return null;
    }
}
