/*
* Copyright 2015-2020 OpenCB
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

package org.opencb.opencga.client.rest;

import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.opencga.client.config.ClientConfiguration;
import org.opencb.opencga.client.exceptions.ClientException;
import org.opencb.opencga.core.models.admin.GroupSyncParams;
import org.opencb.opencga.core.models.admin.InstallationParams;
import org.opencb.opencga.core.models.admin.JWTParams;
import org.opencb.opencga.core.models.admin.UserCreateParams;
import org.opencb.opencga.core.models.admin.UserImportParams;
import org.opencb.opencga.core.models.panel.Panel;
import org.opencb.opencga.core.models.panel.PanelCreateParams;
import org.opencb.opencga.core.models.study.Group;
import org.opencb.opencga.core.models.user.User;
import org.opencb.opencga.core.response.RestResponse;


/**
 * This class contains methods for the Admin webservices.
 *    Client version: 2.0.0
 *    PATH: admin
 */
public class AdminClient extends AbstractParentClient {

    public AdminClient(String token, ClientConfiguration configuration) {
        super(token, configuration);
    }

    /**
     * Create a new user.
     * @param data JSON containing the parameters.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<User> createUsers(UserCreateParams data) throws ClientException {
        ObjectMap params = new ObjectMap();
        params.put("body", data);
        return execute("admin", null, "users", null, "create", params, POST, User.class);
    }

    /**
     * Install OpenCGA database.
     * @param data JSON containing the mandatory parameters.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ObjectMap> installCatalog(InstallationParams data) throws ClientException {
        ObjectMap params = new ObjectMap();
        params.put("body", data);
        return execute("admin", null, "catalog", null, "install", params, POST, ObjectMap.class);
    }

    /**
     * Import users or a group of users from LDAP or AAD.
     * @param data JSON containing the parameters.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<User> importUsers(UserImportParams data) throws ClientException {
        ObjectMap params = new ObjectMap();
        params.put("body", data);
        return execute("admin", null, "users", null, "import", params, POST, User.class);
    }

    /**
     * Synchronise groups of users with LDAP groups.
     * @param data JSON containing the parameters.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<Group> syncUsers(GroupSyncParams data) throws ClientException {
        ObjectMap params = new ObjectMap();
        params.put("body", data);
        return execute("admin", null, "users", null, "sync", params, POST, Group.class);
    }

    /**
     * Group by operation.
     * @param fields Comma separated list of fields by which to group by.
     * @param entity Entity to be grouped by.
     * @param params Map containing any additional optional parameters.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ObjectMap> groupByAudit(String fields, String entity, ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        params.putIfNotNull("fields", fields);
        params.putIfNotNull("entity", entity);
        return execute("admin", null, "audit", null, "groupBy", params, GET, ObjectMap.class);
    }

    /**
     * Sync Catalog into the Solr.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<Boolean> indexStatsCatalog() throws ClientException {
        ObjectMap params = new ObjectMap();
        return execute("admin", null, "catalog", null, "indexStats", params, POST, Boolean.class);
    }

    /**
     * Handle global panels.
     * @param data Panel parameters to be installed.
     * @param params Map containing any additional optional parameters.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<Panel> panelCatalog(PanelCreateParams data, ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        params.put("body", data);
        return execute("admin", null, "catalog", null, "panel", params, POST, Panel.class);
    }

    /**
     * Change JWT secret key.
     * @param data JSON containing the parameters.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ObjectMap> jwtCatalog(JWTParams data) throws ClientException {
        ObjectMap params = new ObjectMap();
        params.put("body", data);
        return execute("admin", null, "catalog", null, "jwt", params, POST, ObjectMap.class);
    }
}