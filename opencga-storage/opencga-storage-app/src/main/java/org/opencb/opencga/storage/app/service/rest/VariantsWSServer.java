package org.opencb.opencga.storage.app.service.rest;

import org.opencb.biodata.models.feature.Region;
import org.opencb.datastore.core.ObjectMap;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.datastore.core.QueryResult;
import org.opencb.opencga.storage.app.service.OpenCGAStorageService;
import org.opencb.opencga.storage.core.StorageManagerFactory;
import org.opencb.opencga.storage.core.variant.VariantStorageManager;
import org.opencb.opencga.storage.core.variant.adaptors.CatalogVariantDBAdaptor;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by hpccoll1 on 13/02/15.
 */
@Path("/variants")
public class VariantsWSServer extends DaemonServlet {

    public VariantsWSServer(@PathParam("version") String version, @Context UriInfo uriInfo, @Context HttpServletRequest httpServletRequest) throws IOException {
        super(version, uriInfo, httpServletRequest);
    }

    @GET
    @Path("/{fileId}/fetch")
    @Produces("application/json")
    public Response fetch(@PathParam("fileId") @DefaultValue("") String fileId,
                          @QueryParam("storageEngine") String storageEngine,
                          @QueryParam("region") @DefaultValue("") String regionsCVS,
                          @QueryParam("sessionId") String sessionId,

                          @QueryParam("view_as_pairs") @DefaultValue("false") boolean view_as_pairs,
                          @QueryParam("include_coverage") @DefaultValue("true") boolean include_coverage,
                          @QueryParam("process_differences") @DefaultValue("true") boolean process_differences,
                          @DefaultValue("false") @QueryParam("histogram") boolean histogram,
                          @DefaultValue("-1") @QueryParam("histogram_interval") int interval
    ) {

        try {
            CatalogVariantDBAdaptor variants = new CatalogVariantDBAdaptor(OpenCGAStorageService.getInstance().getCatalogManager(), fileId, sessionId);

            for (String acceptedValue : VariantDBAdaptor.QueryParams.acceptedValues) {
                addQueryOption(acceptedValue);
            }
            for (String acceptedValue : Arrays.asList("merge", "exclude", "include", "skip", "limit")) {
                addQueryOption(acceptedValue);
            }
            queryOptions.put("files", Arrays.asList(fileId));

            if(params.containsKey("fileId")) {
                if(params.get("fileId").get(0).isEmpty()) {
                    queryOptions.put("fileId", fileId);
                } else {
                    List<String> files = params.get("fileId");
                    queryOptions.put("fileId", files.get(0));
                }
            }
            queryOptions.add("sessionId", sessionId);

            // Parse the provided regions. The total size of all regions together
            // can't excede 1 million positions
            int regionsSize = 0;
            List<Region> regions = new ArrayList<>();
            for (String s : regionsCVS.split(",")) {
                Region r = Region.parseRegion(s);
                regions.add(r);
                regionsSize += r.getEnd() - r.getStart();
            }

            if (histogram) {
                if (regions.size() != 1) {
                    return createErrorResponse("Sorry, histogram functionality only works with a single region");
                } else {
                    if (interval > 0) {
                        queryOptions.put("interval", interval);
                    }
                    return createOkResponse(variants.getVariantFrequencyByRegion(regions.get(0), queryOptions));
                }
            } else if (regionsSize <= 1000000) {
                if (regions.size() == 0) {
                    if (!queryOptions.containsKey("id") && !queryOptions.containsKey("gene")) {
                        return createErrorResponse("Some positional filer is needed, like region, gene or id.");
                    } else {
                        return createOkResponse(variants.getAllVariants(queryOptions));
                    }
                } else {
                    return createOkResponse(variants.getAllVariantsByRegionList(regions, queryOptions));
                }
            } else {
                return createErrorResponse("The total size of all regions provided can't exceed 1 million positions. "
                        + "If you want to browse a larger number of positions, please provide the parameter 'histogram=true'");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return createErrorResponse(e.toString());
        }
    }

    private void addQueryOption(String acceptedValue) {
        if (params.containsKey(acceptedValue)) {
            List<String> values = params.get(acceptedValue);
            String csv = values.get(0);
            for (int i = 1; i < values.size(); i++) {
                csv += "," + values.get(i);
            }
            queryOptions.add(acceptedValue, csv);
        }
    }

}
