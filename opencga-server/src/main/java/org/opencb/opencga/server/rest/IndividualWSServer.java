package org.opencb.opencga.server.rest;

import io.swagger.annotations.*;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.catalog.db.api.CatalogIndividualDBAdaptor;
import org.opencb.opencga.catalog.models.AnnotationSet;
import org.opencb.opencga.catalog.models.Individual;
import org.opencb.opencga.catalog.models.Variable;
import org.opencb.opencga.catalog.models.VariableSet;
import org.opencb.opencga.core.exception.VersionException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by jacobo on 22/06/15.
 */

@Path("/{version}/individuals")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "Individuals", position = 6, description = "Methods for working with 'individuals' endpoint")
public class IndividualWSServer extends OpenCGAWSServer {


    public IndividualWSServer(@Context UriInfo uriInfo, @Context HttpServletRequest httpServletRequest)
            throws IOException, VersionException {
        super(uriInfo, httpServletRequest);
    }

    @GET
    @Path("/create")
    @ApiOperation(value = "Create sample", position = 1, response = Individual.class)
    public Response createIndividual(@ApiParam(value = "studyId", required = true) @QueryParam("studyId") String studyIdStr,
                                 @ApiParam(value = "name", required = true) @QueryParam("name") String name,
                                 @ApiParam(value = "family", required = false) @QueryParam("family") String family,
                                 @ApiParam(value = "fatherId", required = false) @QueryParam("fatherId") long fatherId,
                                 @ApiParam(value = "motherId", required = false) @QueryParam("motherId") long motherId,
                                 @ApiParam(value = "gender", required = false) @QueryParam("gender") @DefaultValue("UNKNOWN") Individual.Gender gender) {
        try {
            long studyId = catalogManager.getStudyId(studyIdStr, sessionId);
            QueryResult<Individual> queryResult = catalogManager.createIndividual(studyId, name, family, fatherId, motherId, gender, queryOptions, sessionId);
            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{individualIds}/info")
    @ApiOperation(value = "Get individual information", position = 2, response = Individual.class)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "include", value = "Fields included in the response, whole JSON path must be provided", example = "name,attributes", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "exclude", value = "Fields excluded in the response, whole JSON path must be provided", example = "id,status", dataType = "string", paramType = "query"),
    })
    public Response infoIndividual(@ApiParam(value = "Comma separated list of individual names or ids", required = true) @PathParam("individualId") String individualStr) {
        try {
            List<QueryResult<Individual>> queryResults = new LinkedList<>();
            List<Long> individualIds = catalogManager.getIndividualIds(individualStr, sessionId);
            for (Long individualId : individualIds) {
                queryResults.add(catalogManager.getIndividual(individualId, queryOptions, sessionId));
            }
            return createOkResponse(queryResults);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/search")
    @ApiOperation(value = "Search for individuals", position = 3, response = Individual[].class)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "include", value = "Fields included in the response, whole JSON path must be provided", example = "name,attributes", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "exclude", value = "Fields excluded in the response, whole JSON path must be provided", example = "id,status", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "limit", value = "Number of results to be returned in the queries", dataType = "integer", paramType = "query"),
            @ApiImplicitParam(name = "skip", value = "Number of results to skip in the queries", dataType = "integer", paramType = "query"),
            @ApiImplicitParam(name = "count", value = "Total number of results", dataType = "boolean", paramType = "query")
    })
    public Response searchIndividuals(@ApiParam(value = "studyId", required = true) @QueryParam("studyId") String studyIdStr,
                                      @ApiParam(value = "id", required = false) @QueryParam("id") String id,
                                      @ApiParam(value = "name", required = false) @QueryParam("name") String name,
                                      @ApiParam(value = "fatherId", required = false) @QueryParam("fatherId") String fatherId,
                                      @ApiParam(value = "motherId", required = false) @QueryParam("motherId") String motherId,
                                      @ApiParam(value = "family", required = false) @QueryParam("family") String family,
                                      @ApiParam(value = "gender", required = false) @QueryParam("gender") String gender,
                                      @ApiParam(value = "race", required = false) @QueryParam("race") String race,
                                      @ApiParam(value = "species", required = false) @QueryParam("species") String species,
                                      @ApiParam(value = "population", required = false) @QueryParam("population") String population,
                                      @ApiParam(value = "variableSetId", required = false) @QueryParam("variableSetId") long variableSetId,
                                      @ApiParam(value = "annotationSetName", required = false) @QueryParam("annotationSetName") String annotationSetName,
                                      @ApiParam(value = "annotation", required = false) @QueryParam("annotation") String annotation) {
        try {
            long studyId = catalogManager.getStudyId(studyIdStr, sessionId);
            QueryOptions qOptions = new QueryOptions(queryOptions);
            parseQueryParams(params, CatalogIndividualDBAdaptor.QueryParams::getParam, query, qOptions);
            QueryResult<Individual> queryResult = catalogManager.getAllIndividuals(studyId, query, qOptions, sessionId);
            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }
    @Deprecated
    @POST
    @Path("/{individualId}/annotate")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Annotate an individual [DEPRECATED]", position = 4)
    public Response annotateSamplePOST(@ApiParam(value = "individualId", required = true) @PathParam("individualId") String individualStr,
                                       @ApiParam(value = "Annotation set name. Must be unique for the individual", required = true) @QueryParam("annotateSetName") String annotateSetName,
                                       @ApiParam(value = "VariableSetId", required = false) @QueryParam("variableSetId") long variableSetId,
                                       @ApiParam(value = "Update an already existing AnnotationSet") @ QueryParam("update") @DefaultValue("false") boolean update,
                                       @ApiParam(value = "Delete an AnnotationSet") @ QueryParam("delete") @DefaultValue("false") boolean delete,
                                       Map<String, Object> annotations) {
        try {
            long individualId = catalogManager.getIndividualId(individualStr, sessionId);
            QueryResult<AnnotationSet> queryResult;
            if (update && delete) {
                return createErrorResponse("Annotate individual", "Unable to update and delete annotations at the same time");
            } else if (update) {
                queryResult = catalogManager.updateIndividualAnnotation(individualId, annotateSetName, annotations, sessionId);
            } else if (delete) {
                queryResult = catalogManager.deleteIndividualAnnotation(individualId, annotateSetName, sessionId);
            } else {
                queryResult = catalogManager.annotateIndividual(individualId, annotateSetName, variableSetId,
                        annotations, Collections.emptyMap(), sessionId);
            }
            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @Deprecated
    @GET
    @Path("/{individualId}/annotate")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Annotate an individual [DEPRECATED]", position = 5)
    public Response annotateSampleGET(@ApiParam(value = "individualId", required = true) @PathParam("individualId") String individualStr,
                                      @ApiParam(value = "Annotation set name. Must be unique", required = true) @QueryParam("annotateSetName") String annotateSetName,
                                      @ApiParam(value = "variableSetId", required = false) @QueryParam("variableSetId") long variableSetId,
                                      @ApiParam(value = "Update an already existing AnnotationSet") @ QueryParam("update") @DefaultValue("false") boolean update,
                                      @ApiParam(value = "Delete an AnnotationSet") @ QueryParam("delete") @DefaultValue("false") boolean delete) {
        try {
            long individualId = catalogManager.getIndividualId(individualStr, sessionId);
            QueryResult<AnnotationSet> queryResult;
            if (update && delete) {
                return createErrorResponse("Annotate individual", "Unable to update and delete annotations at the same time");
            } else if (delete) {
                queryResult = catalogManager.deleteIndividualAnnotation(individualId, annotateSetName, sessionId);
            } else {
                if (update) {
                    for (AnnotationSet annotationSet : catalogManager.getIndividual(individualId, null, sessionId).first().getAnnotationSets()) {
                        if (annotationSet.getName().equals(annotateSetName)) {
                            variableSetId = annotationSet.getVariableSetId();
                        }
                    }
                }
                QueryResult<VariableSet> variableSetResult = catalogManager.getVariableSet(variableSetId, null, sessionId);
                if(variableSetResult.getResult().isEmpty()) {
                    return createErrorResponse("sample annotate", "VariableSet not find.");
                }
                Map<String, Object> annotations = variableSetResult.getResult().get(0).getVariables().stream()
                        .filter(variable -> params.containsKey(variable.getName()))
                        .collect(Collectors.toMap(Variable::getName, variable -> params.getFirst(variable.getName())));

                if (update) {
                    queryResult = catalogManager.updateIndividualAnnotation(individualId, annotateSetName, annotations, sessionId);
                } else {
                    queryResult = catalogManager.annotateIndividual(individualId, annotateSetName, variableSetId,
                            annotations, Collections.emptyMap(), sessionId);
                }
            }

            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{individualId}/annotationSets/{annotationSetName}/search")
    @ApiOperation(value = "Search annotation sets [PENDING]", position = 11)
    public Response searchAnnotationSetGET(@ApiParam(value = "individualId", required = true) @PathParam("individualId") String individualStr,
                                           @ApiParam(value = "annotationSetName", required = true) @PathParam("annotationSetName") String annotationSetName,
                                           @ApiParam(value = "variableSetId", required = true) @QueryParam("variableSetId") long variableSetId,
                                           @ApiParam(value = "annotation", required = false) @QueryParam("annotation") String annotation,
                                           @ApiParam(value = "as-map", required = false, defaultValue = "true") @QueryParam("as-map") boolean asMap) {
        return createErrorResponse("Search", "not implemented");
    }

    @GET
    @Path("/{individualId}/annotationSets/info")
    @ApiOperation(value = "Returns the annotation sets of the sample [PENDING]", position = 12)
    public Response infoAnnotationSetGET(@ApiParam(value = "individualId", required = true) @PathParam("individualId") String individualStr,
                                         @ApiParam(value = "as-map", required = false, defaultValue = "true") @QueryParam("as-map") boolean asMap) {
        return createErrorResponse("Search", "not implemented");
    }

    @POST
    @Path("/{individualId}/annotationSets/create")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "annotate sample [PENDING]", position = 13)
    public Response annotateSamplePOST(@ApiParam(value = "individualId", required = true) @PathParam("individualId") String individualStr,
                                       @ApiParam(value = "Annotation set name. Must be unique for the sample", required = true) @QueryParam("annotateSetName") String annotateSetName,
                                       @ApiParam(value = "VariableSetId of the new annotation", required = false) @QueryParam("variableSetId") long variableSetId,
                                       Map<String, Object> annotations) {
        try {
//            QueryResult<AnnotationSet> queryResult;
//            queryResult = catalogManager.annotateSample(sampleId, annotateSetName, variableSetId,
//                    annotations, Collections.emptyMap(), sessionId);
//            return createOkResponse(queryResult);
            return createOkResponse(null);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{individualId}/annotationSets/{annotationSetName}/delete")
    @ApiOperation(value = "Delete the annotation set or the annotations within the annotation set [PENDING]", position = 14)
    public Response deleteAnnotationGET(@ApiParam(value = "individualId", required = true) @PathParam("individualId") String individualStr,
                                        @ApiParam(value = "annotationSetName", required = true) @PathParam("annotationSetName") String annotationSetName,
                                        @ApiParam(value = "variableSetId", required = true) @QueryParam("variableSetId") long variableSetId,
                                        @ApiParam(value = "annotation", required = false) @QueryParam("annotation") String annotation) {
        return createErrorResponse("Search", "not implemented");
    }

    @POST
    @Path("/{individualId}/annotationSets/{annotationSetName}/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update the annotations [PENDING]", position = 15)
    public Response updateAnnotationGET(@ApiParam(value = "individualId", required = true) @PathParam("individualId") long individualId,
                                        @ApiParam(value = "annotationSetName", required = true) @PathParam("annotationSetName") String annotationSetName,
                                        @ApiParam(value = "variableSetId", required = true) @QueryParam("variableSetId") long variableSetId,
                                        @ApiParam(value = "reset", required = false) @QueryParam("reset") String reset,
                                        Map<String, Object> annotations) {
        return createErrorResponse("Search", "not implemented");
    }

    @GET
    @Path("/{individualId}/annotationSets/{annotationSetName}/info")
    @ApiOperation(value = "Returns the annotation set [PENDING]", position = 16)
    public Response infoAnnotationGET(@ApiParam(value = "individualId", required = true) @PathParam("individualId") String individualStr,
                                      @ApiParam(value = "annotationSetName", required = true) @PathParam("annotationSetName") String annotationSetName,
                                      @ApiParam(value = "variableSetId", required = true) @QueryParam("variableSetId") long variableSetId,
                                      @ApiParam(value = "as-map", required = false, defaultValue = "true") @QueryParam("as-map") boolean asMap) {
        return createErrorResponse("Search", "not implemented");
    }
    @GET
    @Path("/{individualId}/update")
    @ApiOperation(value = "Update individual information", position = 6, response = Individual.class)
    public Response updateIndividual(@ApiParam(value = "individualId", required = true) @PathParam("individualId") String individualStr,
                                     @ApiParam(value = "id", required = false) @QueryParam("id") String id,
                                     @ApiParam(value = "name", required = false) @QueryParam("name") String name,
                                     @ApiParam(value = "fatherId", required = false) @QueryParam("fatherId") long fatherId,
                                     @ApiParam(value = "motherId", required = false) @QueryParam("motherId") long motherId,
                                     @ApiParam(value = "family", required = false) @QueryParam("family") String family,
                                     @ApiParam(value = "gender", required = false) @QueryParam("gender") String gender,
                                     @ApiParam(value = "race", required = false) @QueryParam("race") String race
                                      ) {
        try {
            long individualId = catalogManager.getIndividualId(individualStr, sessionId);
            QueryResult<Individual> queryResult = catalogManager.modifyIndividual(individualId, queryOptions, sessionId);
            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    public static class UpdateIndividual {
        public String name;
        public int fatherId;
        public int motherId;
        public String family;
        public Individual.Gender gender;

        public String race;
        public Individual.Species species;
        public Individual.Population population;
    }

    @POST
    @Path("/{individualId}/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update some individual attributes using POST method", position = 6)
    public Response updateByPost(@ApiParam(value = "individualId", required = true) @PathParam("individualId") String individualStr,
                                 @ApiParam(value = "params", required = true) UpdateIndividual updateParams) {
        try {
            long individualId = catalogManager.getIndividualId(individualStr, sessionId);
            QueryResult<Individual> queryResult = catalogManager.modifyIndividual(individualId,
                    new QueryOptions(jsonObjectMapper.writeValueAsString(updateParams)), sessionId);
            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{individualId}/delete")
    @ApiOperation(value = "Delete individual information", position = 7)
    public Response deleteIndividual(@ApiParam(value = "individualId", required = true) @PathParam("individualId") long individualId) {
        try {
            QueryResult<Individual> queryResult = catalogManager.deleteIndividual(individualId, queryOptions, sessionId);
            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }
//
//    @GET
//    @Path("/{individualIds}/share")
//    @ApiOperation(value = "Share individuals with other members", position = 8)
//    public Response share(@PathParam(value = "individualIds") String individualIds,
//                          @ApiParam(value = "Comma separated list of members. Accepts: '{userId}', '@{groupId}' or '*'", required = true) @DefaultValue("") @QueryParam("members") String members,
//                          @ApiParam(value = "Comma separated list of individual permissions", required = false) @DefaultValue("") @QueryParam("permissions") String permissions,
//                          @ApiParam(value = "Boolean indicating whether to allow the change of of permissions in case any member already had any", required = true) @DefaultValue("false") @QueryParam("override") boolean override) {
//        try {
//            return createOkResponse(catalogManager.shareIndividual(individualIds, members, Arrays.asList(permissions.split(",")), override, sessionId));
//        } catch (Exception e) {
//            return createErrorResponse(e);
//        }
//    }
//
//    @GET
//    @Path("/{individualIds}/unshare")
//    @ApiOperation(value = "Remove the permissions for the list of members", position = 9)
//    public Response unshare(@PathParam(value = "individualIds") String individualIds,
//                            @ApiParam(value = "Comma separated list of members. Accepts: '{userId}', '@{groupId}' or '*'", required = true) @DefaultValue("") @QueryParam("members") String members,
//                            @ApiParam(value = "Comma separated list of individual permissions", required = false) @DefaultValue("") @QueryParam("permissions") String permissions) {
//        try {
//            return createOkResponse(catalogManager.unshareIndividual(individualIds, members, permissions, sessionId));
//        } catch (Exception e) {
//            return createErrorResponse(e);
//        }
//    }

    @GET
    @Path("/groupBy")
    @ApiOperation(value = "Group individuals by several fields", position = 10)
    public Response groupBy(@ApiParam(value = "Comma separated list of fields by which to group by.", required = true) @DefaultValue("") @QueryParam("by") String by,
                            @ApiParam(value = "studyId", required = true) @QueryParam("studyId") String studyIdStr,
                            @ApiParam(value = "id", required = false) @QueryParam("id") String ids,
                            @ApiParam(value = "name", required = false) @QueryParam("name") String names,
                            @ApiParam(value = "fatherId", required = false) @QueryParam("fatherId") String fatherId,
                            @ApiParam(value = "motherId", required = false) @QueryParam("motherId") String motherId,
                            @ApiParam(value = "family", required = false) @QueryParam("family") String family,
                            @ApiParam(value = "gender", required = false) @QueryParam("gender") String gender,
                            @ApiParam(value = "race", required = false) @QueryParam("race") String race,
                            @ApiParam(value = "species", required = false) @QueryParam("species") String species,
                            @ApiParam(value = "population", required = false) @QueryParam("population") String population,
                            @ApiParam(value = "variableSetId", required = false) @QueryParam("variableSetId") long variableSetId,
                            @ApiParam(value = "annotationSetName", required = false) @QueryParam("annotationSetName") String annotationSetName,
                            @ApiParam(value = "annotation", required = false) @QueryParam("annotation") String annotation) {
        try {
            Query query = new Query();
            QueryOptions qOptions = new QueryOptions();
            parseQueryParams(params, CatalogIndividualDBAdaptor.QueryParams::getParam, query, qOptions);

            logger.debug("query = " + query.toJson());
            logger.debug("queryOptions = " + qOptions.toJson());
            QueryResult result = catalogManager.individualGroupBy(query, qOptions, by, sessionId);
            return createOkResponse(result);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{individualId}/acls")
    @ApiOperation(value = "Returns the acls of the individual [PENDING]", position = 18)
    public Response getAcls(@ApiParam(value = "individualId", required = true) @PathParam("individualId") String studyIdStr) {
        try {
            return createOkResponse(null);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }


    @GET
    @Path("/{individualId}/acls/create")
    @ApiOperation(value = "Define a set of permissions for a list of members [PENDING]", position = 19)
    public Response createAcl   (@ApiParam(value = "individualId", required = true) @PathParam("individualId") String individualStr,
                               @ApiParam(value = "Comma separated list of permissions that will be granted to the member list", required = true) @DefaultValue("") @QueryParam("permissions") String permissions,
                               @ApiParam(value = "Comma separated list of members. Accepts: '{userId}', '@{groupId}' or '*'", required = true) @DefaultValue("") @QueryParam("members") String members) {
        try {
            return createOkResponse(null);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{individualId}/acls/{memberId}/info")
    @ApiOperation(value = "Returns the set of permissions granted for the member [PENDING]", position = 20)
    public Response getAcl(@ApiParam(value = "individualId", required = true) @PathParam("individualId") String studyIdStr,
                           @ApiParam(value = "Member id", required = true) @PathParam("memberId") String memberId) {
        try {
            return createOkResponse(null);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{individualId}/acls/{memberId}/update")
    @ApiOperation(value = "Update the set of permissions granted for the member [PENDING]", position = 21)
    public Response updateAcl(@ApiParam(value = "individualId", required = true) @PathParam("individualId") String cohortIdStr,
                              @ApiParam(value = "Member id", required = true) @PathParam("memberId") String memberId,
                              @ApiParam(value = "Comma separated list of permissions to add", required = false) @PathParam("addPermissions") String addPermissions,
                              @ApiParam(value = "Comma separated list of permissions to remove", required = false) @PathParam("removePermissions") String removePermissions,
                              @ApiParam(value = "Comma separated list of permissions to set", required = false) @PathParam("setPermissions") String setPermissions) {
        try {
            return createOkResponse(null);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{individualId}/acls/{memberId}/delete")
    @ApiOperation(value = "Delete all the permissions granted for the member [PENDING]", position = 22)
    public Response deleteAcl(@ApiParam(value = "individualId", required = true) @PathParam("individualId") String cohortIdStr,
                              @ApiParam(value = "Member id", required = true) @PathParam("memberId") String memberId) {
        try {
            return createOkResponse(null);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

}
