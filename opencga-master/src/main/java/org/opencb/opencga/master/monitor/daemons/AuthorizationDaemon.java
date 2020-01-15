package org.opencb.opencga.master.monitor.daemons;

import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.catalog.auth.authorization.AuthorizationManager;
import org.opencb.opencga.catalog.db.api.DBIterator;
import org.opencb.opencga.catalog.db.api.StudyDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.managers.CatalogManager;
import org.opencb.opencga.core.common.TimeUtils;
import org.opencb.opencga.core.models.study.PermissionRule;
import org.opencb.opencga.core.models.study.Study;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AuthorizationDaemon extends MonitorParentDaemon {

    private final String INTERNAL_DELIMITER = "__";

    // FIXME: This should not be used directly! All the queries MUST go through the CatalogManager
    @Deprecated
    private StudyDBAdaptor studyDBAdaptor;
    private AuthorizationManager authorizationManager;

    public AuthorizationDaemon(int interval, String sessionId, CatalogManager catalogManager) throws CatalogDBException {
        super(interval, sessionId, catalogManager);
        this.studyDBAdaptor = dbAdaptorFactory.getCatalogStudyDBAdaptor();
        this.authorizationManager = catalogManager.getAuthorizationManager();
    }

    @Override
    public void run() {

        Query allStudies = new Query();
        QueryOptions options = new QueryOptions(QueryOptions.INCLUDE,
                        Arrays.asList(StudyDBAdaptor.QueryParams.PERMISSION_RULES.key(), StudyDBAdaptor.QueryParams.ID.key(),
                                StudyDBAdaptor.QueryParams.UID.key()));

        while (!exit) {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                if (!exit) {
                    e.printStackTrace();
                }
            }
            logger.info("----- AUTHORIZATION DAEMON -----", TimeUtils.getTimeMillis());

            try (DBIterator<Study> iterator = studyDBAdaptor.iterator(allStudies, options)) {
                while (iterator.hasNext()) {
                    applyPermissionRules(iterator.next());
                }
            } catch (Exception e) {
                logger.error("{}", e.getMessage(), e);
            }
        }
    }

    private void applyPermissionRules(Study study) {
        if (study.getPermissionRules().isEmpty()) {
            return;
        }

        logger.info("Analysing study {} ({})", study.getId(), study.getUid());

        for (Map.Entry<Study.Entity, List<PermissionRule>> myMap : study.getPermissionRules().entrySet()) {
            Study.Entity entry = myMap.getKey();
            for (PermissionRule permissionRule : myMap.getValue()) {
                try {
                    String[] split = permissionRule.getId().split(INTERNAL_DELIMITER, 2);
                    if (split.length == 1) {
                        // Apply rules
                        logger.info("Attempting to apply permission rule {} in {}", permissionRule.getId(), entry);
                        authorizationManager.applyPermissionRule(study.getUid(), permissionRule, entry);
                    } else {
                        // Remove permission rule
                        PermissionRule.DeleteAction deleteAction = PermissionRule.DeleteAction.valueOf(split[1].split("_")[1]);
                        switch (deleteAction) {
                            case NONE:
                                logger.info("Removing permission rule {}", permissionRule.getId().split(INTERNAL_DELIMITER)[0],
                                        entry);
                                authorizationManager.removePermissionRule(study.getUid(), permissionRule.getId(), entry);
                                break;
                            case REVERT:
                                logger.info("Removing permission rule {} and reverting applied permissions for {}",
                                        permissionRule.getId().split(INTERNAL_DELIMITER)[0], entry);
                                authorizationManager.removePermissionRuleAndRestorePermissions(study, permissionRule.getId(), entry);
                                break;
                            case REMOVE:
                            default:
                                logger.info("Removing permission rule {} and removing applied permissions for {}",
                                        permissionRule.getId().split(INTERNAL_DELIMITER)[0], entry);
                                authorizationManager.removePermissionRuleAndRemovePermissions(study, permissionRule.getId(), entry);
                                break;
                        }
                    }

                } catch (CatalogException e) {
                    logger.error("{}", e.getMessage(), e);
                }
            }
        }
    }
}
