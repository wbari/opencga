package org.opencb.opencga.server.grpc;

import ga4gh.Reads;
import htsjdk.samtools.SAMRecord;
import io.grpc.stub.StreamObserver;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.catalog.config.CatalogConfiguration;
import org.opencb.opencga.storage.core.alignment.iterators.AlignmentIterator;
import org.opencb.opencga.storage.core.config.StorageConfiguration;
import org.opencb.opencga.storage.core.manager.AlignmentStorageManager;

/**
 * Created by pfurio on 26/10/16.
 */
public class AlignmentGrpcService extends AlignmentServiceGrpc.AlignmentServiceImplBase {

    private GenericGrpcService genericGrpcService;
    private AlignmentStorageManager alignmentStorageManager;

    public AlignmentGrpcService(CatalogConfiguration catalogConfiguration, StorageConfiguration storageConfiguration) {
        genericGrpcService = new GenericGrpcService(catalogConfiguration, storageConfiguration);
        alignmentStorageManager = new AlignmentStorageManager(genericGrpcService.catalogManager, GenericGrpcService.storageManagerFactory);
    }

    @Override
    public void count(GenericAlignmentServiceModel.Request request, StreamObserver<ServiceTypesModel.LongResponse> responseObserver) {
        try {
            // Creating the datastore Query and QueryOptions objects from the gRPC request Map of Strings
            Query query = createQuery(request);
            QueryOptions queryOptions = createQueryOptions(request);

            String studyIdStr = query.getString("studyId");
            String fileIdStr = query.getString("fileId");
            String sessionId = query.getString("sid");

            QueryResult<Long> countQueryResult = alignmentStorageManager.count(studyIdStr, fileIdStr, query, queryOptions, sessionId);

            if (countQueryResult.getNumResults() != 1) {
                throw new Exception(countQueryResult.getErrorMsg());
            }

            long count = countQueryResult.first();
            ServiceTypesModel.LongResponse longResponse = ServiceTypesModel.LongResponse.newBuilder().setValue(count).build();
            responseObserver.onNext(longResponse);
            responseObserver.onCompleted();
//            alignmentDBAdaptor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void distinct(GenericAlignmentServiceModel.Request request, StreamObserver<ServiceTypesModel.StringArrayResponse> responseObserver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void get(GenericAlignmentServiceModel.Request request, StreamObserver<Reads.ReadAlignment> responseObserver) {
        // Creating the datastore Query and QueryOptions objects from the gRPC request Map of Strings
        Query query = createQuery(request);
        QueryOptions queryOptions = createQueryOptions(request);

        String studyIdStr = query.getString("studyId");
        String fileIdStr = query.getString("fileId");
        String sessionId = query.getString("sid");

        try (AlignmentIterator<Reads.ReadAlignment> iterator =
                     alignmentStorageManager.iterator(studyIdStr, fileIdStr, query, queryOptions, sessionId)) {
            while (iterator.hasNext()) {
                responseObserver.onNext(iterator.next());
            }
            responseObserver.onCompleted();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void getAsSam(GenericAlignmentServiceModel.Request request, StreamObserver<ServiceTypesModel.StringResponse> responseObserver) {
        // Creating the datastore Query and QueryOptions objects from the gRPC request Map of Strings
        Query query = createQuery(request);
        QueryOptions queryOptions = createQueryOptions(request);

        String studyIdStr = query.getString("studyId");
        String fileIdStr = query.getString("fileId");
        String sessionId = query.getString("sid");

        try (AlignmentIterator<SAMRecord> iterator =
                     alignmentStorageManager.iterator(studyIdStr, fileIdStr, query, queryOptions, sessionId,
                             SAMRecord.class)) {
            while (iterator.hasNext()) {
                ServiceTypesModel.StringResponse response =
                        ServiceTypesModel.StringResponse.newBuilder().setValue(iterator.next().getSAMString()).build();
                responseObserver.onNext(response);
            }
            responseObserver.onCompleted();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void groupBy(GenericAlignmentServiceModel.Request request, StreamObserver<ServiceTypesModel.GroupResponse> responseObserver) {
        throw new UnsupportedOperationException();
    }

    // TODO: Temporal solution. We have to implement a general createQuery and createQueryOptions
    private Query createQuery(GenericAlignmentServiceModel.Request request) {
        Query query = new Query();
        for (String key : request.getQuery().keySet()) {
            if (request.getQuery().get(key) != null) {
                query.put(key, request.getQuery().get(key));
            }
        }
        return query;
    }

    private QueryOptions createQueryOptions(GenericAlignmentServiceModel.Request request) {
        QueryOptions queryOptions = new QueryOptions();
        for (String key : request.getOptions().keySet()) {
            if (request.getOptions().get(key) != null) {
                queryOptions.put(key, request.getOptions().get(key));
            }
        }
        return queryOptions;
    }

}
