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

package org.opencb.opencga.client.rest.clients;

import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.opencga.client.config.ClientConfiguration;
import org.opencb.opencga.client.exceptions.ClientException;
import org.opencb.opencga.client.rest.AbstractParentClient;
import org.opencb.opencga.core.response.RestResponse;


/*
* WARNING: AUTOGENERATED CODE
*
* This code was generated by a tool.
* Autogenerated on: 2020-06-02 10:48:55
*
* Manual changes to this file may cause unexpected behavior in your application.
* Manual changes to this file will be overwritten if the code is regenerated.
*/


/**
 * This class contains methods for the Meta webservices.
 *    Client version: 2.0.0
 *    PATH: meta
 */
public class MetaClient extends AbstractParentClient {

    public MetaClient(String token, ClientConfiguration configuration) {
        super(token, configuration);
    }

    /**
     * Returns info about current OpenCGA code.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ObjectMap> about() throws ClientException {
        ObjectMap params = new ObjectMap();
        return execute("meta", null, null, null, "about", params, GET, ObjectMap.class);
    }

    /**
     * API.
     * @param params Map containing any of the following optional parameters.
     *       category: List of categories to get API from.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ObjectMap> api(ObjectMap params) throws ClientException {
        params = params != null ? params : new ObjectMap();
        return execute("meta", null, null, null, "api", params, GET, ObjectMap.class);
    }

    /**
     * Ping Opencga webservices.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ObjectMap> fail() throws ClientException {
        ObjectMap params = new ObjectMap();
        return execute("meta", null, null, null, "fail", params, GET, ObjectMap.class);
    }

    /**
     * Ping Opencga webservices.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ObjectMap> ping() throws ClientException {
        ObjectMap params = new ObjectMap();
        return execute("meta", null, null, null, "ping", params, GET, ObjectMap.class);
    }

    /**
     * Database status.
     * @return a RestResponse object.
     * @throws ClientException ClientException if there is any server error.
     */
    public RestResponse<ObjectMap> status() throws ClientException {
        ObjectMap params = new ObjectMap();
        return execute("meta", null, null, null, "status", params, GET, ObjectMap.class);
    }
}
