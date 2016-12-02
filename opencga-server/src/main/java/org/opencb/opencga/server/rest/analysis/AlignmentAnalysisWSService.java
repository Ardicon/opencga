/*
 * Copyright 2015-2016 OpenCB
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

package org.opencb.opencga.server.rest.analysis;

import io.swagger.annotations.*;
import org.apache.commons.lang3.StringUtils;
import org.ga4gh.models.ReadAlignment;
import org.opencb.biodata.models.alignment.RegionCoverage;
import org.opencb.biodata.tools.alignment.stats.AlignmentGlobalStats;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResponse;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.core.exception.VersionException;
import org.opencb.opencga.server.rest.FileWSServer;
import org.opencb.opencga.storage.core.alignment.AlignmentDBAdaptor;
import org.opencb.opencga.storage.core.local.AlignmentStorageManager;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by imedina on 17/08/16.
 */
@Path("/{version}/analysis/alignment")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "Analysis - Alignment", position = 4, description = "Methods for working with 'files' endpoint")
public class AlignmentAnalysisWSService extends AnalysisWSService {

    public AlignmentAnalysisWSService(@Context UriInfo uriInfo, @Context HttpServletRequest httpServletRequest)
            throws IOException, VersionException {
        super(uriInfo, httpServletRequest);
    }

    public AlignmentAnalysisWSService(String version, @Context UriInfo uriInfo, @Context HttpServletRequest httpServletRequest)
            throws IOException, VersionException {
        super(version, uriInfo, httpServletRequest);
    }

    @GET
    @Path("/{fileId}/index")
    @ApiOperation(value = "Index alignment files", position = 14, response = QueryResponse.class)
    public Response index(@ApiParam("Comma separated list of file ids (files or directories)") @PathParam(value = "fileId") String fileIdStr,
                          // FIXME: Study id is not ingested by the analysis index command line. No longer needed.
                          @ApiParam("Study id") @QueryParam("studyId") String studyId,
                          @ApiParam("Boolean indicating that only the transform step will be run") @DefaultValue("false") @QueryParam("transform") boolean transform,
                          @ApiParam("Boolean indicating that only the load step will be run") @DefaultValue("false") @QueryParam("load") boolean load) {

        Map<String, String> params = new LinkedHashMap<>();
        addParamIfNotNull(params, "studyId", studyId);
        addParamIfTrue(params, "transform", transform);
        addParamIfTrue(params, "load", load);

        logger.info("ObjectMap: {}", params);

        try {
            List<String> fileIds = FileWSServer.convertPathList(fileIdStr, sessionId);
            // TODO: Indexing bam files is not working !!
            QueryResult queryResult = catalogManager.getFileManager().index(StringUtils.join(fileIds, ","), "BAM", params, sessionId);
            return createOkResponse(queryResult);
        } catch(Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{fileId}/query")
    @ApiOperation(value = "Fetch alignments from a BAM file", position = 15, response = ReadAlignment[].class)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "include", value = "Fields included in the response, whole JSON path must be provided", example = "name,attributes", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "exclude", value = "Fields excluded in the response, whole JSON path must be provided", example = "id,status", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "limit", value = "Number of results to be returned in the queries", dataType = "integer", paramType = "query"),
            @ApiImplicitParam(name = "skip", value = "Number of results to skip in the queries", dataType = "integer", paramType = "query"),
            @ApiImplicitParam(name = "count", value = "Total number of results", dataType = "boolean", paramType = "query")
    })
    public Response getAlignments(@ApiParam(value = "Id of the alignment file in catalog", required = true) @PathParam("fileId")
                                          String fileIdStr,
                                  @ApiParam(value = "Study id", required = false) @QueryParam("studyId") String studyId,
                                  @ApiParam(value = "Region 'chr:start-end'", required = false) @QueryParam("region") String region,
                                  @ApiParam(value = "Minimum mapping quality", required = false) @QueryParam("minMapQ") Integer minMapQ,
                                  @ApiParam(value = "Only alignments completely contained within boundaries of region", required = false)
                                  @QueryParam("contained") Boolean contained,
                                  @ApiParam(value = "Force SAM MD optional field to be set with the alignments", required = false)
                                  @QueryParam("mdField") Boolean mdField,
                                  @ApiParam(value = "Compress the nucleotide qualities by using 8 quality levels", required = false)
                                  @QueryParam("binQualities") Boolean binQualities) {
        try {
            Query query = new Query();
            query.putIfNotNull(AlignmentDBAdaptor.QueryParams.REGION.key(), region);
            query.putIfNotNull(AlignmentDBAdaptor.QueryParams.MIN_MAPQ.key(), minMapQ);

            QueryOptions queryOptions = new QueryOptions();
            queryOptions.putIfNotNull(AlignmentDBAdaptor.QueryParams.LIMIT.key(), limit);
            queryOptions.putIfNotNull(AlignmentDBAdaptor.QueryParams.SKIP.key(), skip);
            queryOptions.putIfNotNull("count", count);
            queryOptions.putIfNotNull(AlignmentDBAdaptor.QueryParams.CONTAINED.key(), contained);
            queryOptions.putIfNotNull(AlignmentDBAdaptor.QueryParams.MD_FIELD.key(), mdField);
            queryOptions.putIfNotNull(AlignmentDBAdaptor.QueryParams.BIN_QUALITIES.key(), binQualities);

            AlignmentStorageManager alignmentStorageManager = new AlignmentStorageManager(catalogManager, storageConfiguration);
            return createOkResponse(
                    alignmentStorageManager.query(studyId, fileIdStr, query, queryOptions, sessionId)
            );
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{fileId}/stats")
    @ApiOperation(value = "Fetch the stats of an alignment file", position = 15, response = AlignmentGlobalStats.class)
    public Response getStats(@ApiParam(value = "Id of the alignment file in catalog", required = true) @PathParam("fileId")
                                          String fileIdStr,
                                  @ApiParam(value = "Study id", required = false) @QueryParam("studyId") String studyId,
                             @ApiParam(value = "Region 'chr:start-end'", required = false) @QueryParam("region") String region,
                             @ApiParam(value = "Minimum mapping quality", required = false) @QueryParam("minMapQ") Integer minMapQ,
                             @ApiParam(value = "Only alignments completely contained within boundaries of region", required = false)
                                 @QueryParam("contained") Boolean contained) {
        try {
            Query query = new Query();
            query.putIfNotNull(AlignmentDBAdaptor.QueryParams.REGION.key(), region);
            query.putIfNotNull(AlignmentDBAdaptor.QueryParams.MIN_MAPQ.key(), minMapQ);

            QueryOptions queryOptions = new QueryOptions();
            queryOptions.putIfNotNull(AlignmentDBAdaptor.QueryParams.CONTAINED.key(), contained);

            AlignmentStorageManager alignmentStorageManager = new AlignmentStorageManager(catalogManager, storageConfiguration);
            return createOkResponse(
                    alignmentStorageManager.stats(studyId, fileIdStr, query, queryOptions, sessionId));
//
//            String userId = catalogManager.getUserManager().getId(sessionId);
//            Long fileId = catalogManager.getFileManager().getId(userId, fileIdStr);
//
//            Query query = new Query();
//            query.putIfNotNull(AlignmentDBAdaptor.QueryParams.REGION.key(), region);
//            query.putIfNotNull(AlignmentDBAdaptor.QueryParams.MIN_MAPQ.key(), minMapQ);
//
//            QueryOptions queryOptions = new QueryOptions();
//            queryOptions.putIfNotNull(AlignmentDBAdaptor.QueryParams.CONTAINED.key(), contained);
//
//            QueryOptions options = new QueryOptions(QueryOptions.INCLUDE, FileDBAdaptor.QueryParams.URI.key());
//            QueryResult<File> fileQueryResult = catalogManager.getFileManager().get(fileId, options, sessionId);
//
//            if (fileQueryResult != null && fileQueryResult.getNumResults() != 1) {
//                // This should never happen
//                throw new CatalogException("Critical error: File " + fileId + " could not be found in catalog.");
//            }
//            String path = fileQueryResult.first().getUri().getRawPath();
//
//            AlignmentStorageManager alignmentStorageManager = storageManagerFactory.getAlignmentStorageManager();
//            AlignmentGlobalStats stats = alignmentStorageManager.getDBAdaptor().stats(path, query, queryOptions);
//            QueryResult<AlignmentGlobalStats> queryResult = new QueryResult<>("get stats", -1, 1, 1, "", "", Arrays.asList(stats));
//            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{fileId}/coverage")
    @ApiOperation(value = "Fetch the coverage of an alignment file", position = 15, response = RegionCoverage.class)
    public Response getCoverage(@ApiParam(value = "Id of the alignment file in catalog", required = true) @PathParam("fileId")
                                     String fileIdStr,
                                @ApiParam(value = "Study id", required = false) @QueryParam("studyId") String studyId,
                                @ApiParam(value = "Region 'chr:start-end'", required = false) @QueryParam("region") String region,
                                @ApiParam(value = "Minimum mapping quality", required = false) @QueryParam("minMapQ") Integer minMapQ,
                                @ApiParam(value = "Only alignments completely contained within boundaries of region", required = false)
                                    @QueryParam("contained") Boolean contained) {
        try {
            Query query = new Query();
            query.putIfNotNull(AlignmentDBAdaptor.QueryParams.REGION.key(), region);
            query.putIfNotNull(AlignmentDBAdaptor.QueryParams.MIN_MAPQ.key(), minMapQ);

            QueryOptions queryOptions = new QueryOptions();
            queryOptions.putIfNotNull(AlignmentDBAdaptor.QueryParams.CONTAINED.key(), contained);

            AlignmentStorageManager alignmentStorageManager = new AlignmentStorageManager(catalogManager, storageConfiguration);

            return createOkResponse(
                    alignmentStorageManager.coverage(studyId, fileIdStr, query, queryOptions, sessionId)
            );
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

}
