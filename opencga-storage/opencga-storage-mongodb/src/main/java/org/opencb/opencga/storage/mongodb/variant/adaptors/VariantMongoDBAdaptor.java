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

package org.opencb.opencga.storage.mongodb.variant.adaptors;

import com.google.common.base.Throwables;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.mongodb.*;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import htsjdk.variant.vcf.VCFConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.opencb.biodata.models.core.Region;
import org.opencb.biodata.models.variant.StudyEntry;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.AdditionalAttribute;
import org.opencb.biodata.models.variant.avro.VariantAnnotation;
import org.opencb.biodata.models.variant.avro.VariantType;
import org.opencb.biodata.models.variant.stats.VariantStats;
import org.opencb.cellbase.client.config.ClientConfiguration;
import org.opencb.cellbase.client.rest.CellBaseClient;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.commons.datastore.mongodb.MongoDBCollection;
import org.opencb.commons.datastore.mongodb.MongoDataStore;
import org.opencb.commons.datastore.mongodb.MongoDataStoreManager;
import org.opencb.commons.io.DataWriter;
import org.opencb.opencga.core.results.VariantQueryResult;
import org.opencb.opencga.storage.core.cache.CacheManager;
import org.opencb.opencga.storage.core.config.CellBaseConfiguration;
import org.opencb.opencga.storage.core.config.StorageConfiguration;
import org.opencb.opencga.storage.core.config.StorageEngineConfiguration;
import org.opencb.opencga.storage.core.exceptions.VariantSearchException;
import org.opencb.opencga.storage.core.metadata.StudyConfiguration;
import org.opencb.opencga.storage.core.metadata.StudyConfigurationManager;
import org.opencb.opencga.storage.core.search.VariantSearchManager;
import org.opencb.opencga.storage.core.variant.VariantStorageEngine;
import org.opencb.opencga.storage.core.variant.adaptors.*;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptorUtils.*;
import org.opencb.opencga.storage.core.variant.annotation.VariantAnnotationManager;
import org.opencb.opencga.storage.core.variant.annotation.annotators.AbstractCellBaseVariantAnnotator;
import org.opencb.opencga.storage.core.variant.stats.VariantStatsWrapper;
import org.opencb.opencga.storage.mongodb.auth.MongoCredentials;
import org.opencb.opencga.storage.mongodb.variant.MongoDBVariantStorageEngine;
import org.opencb.opencga.storage.mongodb.variant.converters.*;
import org.opencb.opencga.storage.mongodb.variant.load.MongoDBVariantWriteResult;
import org.opencb.opencga.storage.mongodb.variant.load.stage.MongoDBVariantStageLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.opencb.commons.datastore.mongodb.MongoDBCollection.MULTI;
import static org.opencb.commons.datastore.mongodb.MongoDBCollection.UPSERT;
import static org.opencb.opencga.storage.core.variant.VariantStorageEngine.Options.DEFAULT_TIMEOUT;
import static org.opencb.opencga.storage.core.variant.VariantStorageEngine.Options.MAX_TIMEOUT;
import static org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptorUtils.*;
import static org.opencb.opencga.storage.mongodb.variant.MongoDBVariantStorageEngine.MongoDBVariantOptions.COLLECTION_STAGE;
import static org.opencb.opencga.storage.mongodb.variant.MongoDBVariantStorageEngine.MongoDBVariantOptions.DEFAULT_GENOTYPE;

/**
 * @author Ignacio Medina <igmecas@gmail.com>
 * @author Jacobo Coll <jacobo167@gmail.com>
 * @author Cristina Yenyxe Gonzalez Garcia <cyenyxe@ebi.ac.uk>
 */
public class VariantMongoDBAdaptor implements VariantDBAdaptor {

    private final CellBaseClient cellBaseClient;
    private boolean closeConnection;
    private final MongoDataStoreManager mongoManager;
    private final MongoDataStore db;
    private final String collectionName;
    private final MongoDBCollection variantsCollection;
    private final VariantSourceMongoDBAdaptor variantSourceMongoDBAdaptor;
    private final StorageConfiguration storageConfiguration;
    @Deprecated
    private final StorageEngineConfiguration storageEngineConfiguration;
    private final Pattern writeResultErrorPattern = Pattern.compile("^.*dup key: \\{ : \"([^\"]*)\" \\}$");
    private final VariantDBAdaptorUtils utils;
    private final MongoCredentials credentials;
    private static final Pattern OPERATION_PATTERN = Pattern.compile("^([^=<>~!]*)(<=?|>=?|!=|!?=?~|==?)([^=<>~!]+.*)$");

    private StudyConfigurationManager studyConfigurationManager;
    private final ObjectMap configuration;
    private final CellBaseConfiguration cellbaseConfiguration;
    private CacheManager cacheManager;

    private VariantSearchManager variantSearchManager;

    @Deprecated
    private DataWriter dataWriter;

    protected static Logger logger = LoggerFactory.getLogger(VariantMongoDBAdaptor.class);

    // Number of opened dbAdaptors
    public static final AtomicInteger NUMBER_INSTANCES = new AtomicInteger(0);

    public VariantMongoDBAdaptor(MongoCredentials credentials, String variantsCollectionName, String filesCollectionName,
                                 StudyConfigurationManager studyConfigurationManager, StorageConfiguration storageConfiguration)
            throws UnknownHostException {
        this(new MongoDataStoreManager(credentials.getDataStoreServerAddresses()), credentials, variantsCollectionName, filesCollectionName,
                studyConfigurationManager, storageConfiguration);
        this.closeConnection = true;
    }

    public VariantMongoDBAdaptor(MongoDataStoreManager mongoManager, MongoCredentials credentials,
                                 String variantsCollectionName, String filesCollectionName,
                                 StudyConfigurationManager studyConfigurationManager, StorageConfiguration storageConfiguration)
            throws UnknownHostException {
        // MongoDB configuration
        this.closeConnection = false;
        this.credentials = credentials;
        this.mongoManager = mongoManager;
        db = mongoManager.get(credentials.getMongoDbName(), credentials.getMongoDBConfiguration());
        variantSourceMongoDBAdaptor = new VariantSourceMongoDBAdaptor(db, filesCollectionName);
        collectionName = variantsCollectionName;
        variantsCollection = db.getCollection(collectionName);
        this.studyConfigurationManager = studyConfigurationManager;
        cellbaseConfiguration = storageConfiguration.getCellbase();
        this.storageConfiguration = storageConfiguration;
        this.storageEngineConfiguration = storageConfiguration.getStorageEngine(MongoDBVariantStorageEngine.STORAGE_ENGINE_ID);
        this.configuration = storageEngineConfiguration == null || this.storageEngineConfiguration.getVariant().getOptions() == null
                ? new ObjectMap()
                : this.storageEngineConfiguration.getVariant().getOptions();
        this.utils = new VariantDBAdaptorUtils(this);
        String species = configuration.getString(VariantAnnotationManager.SPECIES);
        String assembly = configuration.getString(VariantAnnotationManager.ASSEMBLY);
        ClientConfiguration clientConfiguration = cellbaseConfiguration.toClientConfiguration();
        if (StringUtils.isEmpty(species)) {
            species = clientConfiguration.getDefaultSpecies();
        }
        cellBaseClient = new CellBaseClient(AbstractCellBaseVariantAnnotator.toCellBaseSpeciesName(species), assembly, clientConfiguration);
        this.cacheManager = new CacheManager(storageConfiguration);
        this.variantSearchManager = new VariantSearchManager(utils, storageConfiguration);
        NUMBER_INSTANCES.incrementAndGet();
    }

    public MongoDBCollection getVariantsCollection() {
        return variantsCollection;
    }

    public MongoDBCollection getStageCollection() {
        return db.getCollection(configuration.getString(COLLECTION_STAGE.key(), COLLECTION_STAGE.defaultValue()));
    }

    protected MongoDataStore getDB() {
        return db;
    }

    protected MongoCredentials getCredentials() {
        return credentials;
    }

    @Override
    @Deprecated
    public void setDataWriter(DataWriter dataWriter) {
        this.dataWriter = dataWriter;
    }


    @Override
    public QueryResult insert(List<Variant> variants, String studyName, QueryOptions options) {
        StudyConfiguration studyConfiguration = studyConfigurationManager.getStudyConfiguration(studyName, options).first();
        // TODO FILE_ID must be in QueryOptions?
        int fileId = options.getInt(VariantStorageEngine.Options.FILE_ID.key());
        boolean includeStats = options.getBoolean(VariantStorageEngine.Options.INCLUDE_STATS.key(), VariantStorageEngine.Options
                .INCLUDE_STATS.defaultValue());
//        boolean includeSrc = options.getBoolean(VariantStorageEngine.Options.INCLUDE_SRC.key(), VariantStorageEngine.Options
//                .INCLUDE_SRC.defaultValue());
//        boolean includeGenotypes = options.getBoolean(VariantStorageEngine.Options.INCLUDE_GENOTYPES.key(), VariantStorageEngine
//                .Options.INCLUDE_GENOTYPES.defaultValue());
//        boolean compressGenotypes = options.getBoolean(VariantStorageEngine.Options.COMPRESS_GENOTYPES.key(), VariantStorageEngine
// .Options.COMPRESS_GENOTYPES.defaultValue());
//        String defaultGenotype = options.getString(MongoDBVariantStorageEngine.DEFAULT_GENOTYPE, "0|0");

        DocumentToVariantConverter variantConverter = new DocumentToVariantConverter(null, includeStats ? new
                DocumentToVariantStatsConverter(studyConfigurationManager) : null);
//        DBObjectToStudyVariantEntryConverter sourceEntryConverter = new DBObjectToStudyVariantEntryConverter(includeSrc,
//                includeGenotypes ? new DBObjectToSamplesConverter(studyConfiguration) : null);
        DocumentToStudyVariantEntryConverter sourceEntryConverter =
                new DocumentToStudyVariantEntryConverter(true, new DocumentToSamplesConverter(studyConfiguration));
        return insert(variants, fileId, variantConverter, sourceEntryConverter, studyConfiguration, getLoadedSamples(fileId,
                studyConfiguration));
    }

    @Override
    public QueryResult delete(Query query, QueryOptions options) {
        Bson mongoQuery = parseQuery(query);
        logger.debug("Delete to be executed: '{}'", mongoQuery.toString());
        QueryResult queryResult = variantsCollection.remove(mongoQuery, options);

        return queryResult;
    }

    @Override
    public QueryResult deleteSamples(String studyName, List<String> sampleNames, QueryOptions options) {
        //TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public QueryResult deleteFile(String studyName, String fileName, QueryOptions options) {
        //TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public QueryResult deleteStudy(String studyName, QueryOptions options) {
        if (options == null) {
            options = new QueryOptions();
        }
        StudyConfiguration studyConfiguration = studyConfigurationManager.getStudyConfiguration(studyName, options).first();
        Document query = parseQuery(new Query(VariantQueryParams.STUDIES.key(), studyConfiguration.getStudyId()));

        // { $pull : { files : {  sid : <studyId> } } }
        Document update = new Document(
                "$pull",
                new Document(
                        DocumentToVariantConverter.STUDIES_FIELD,
                        new Document(
                                DocumentToStudyVariantEntryConverter.STUDYID_FIELD, studyConfiguration.getStudyId()
                        )
                )
        );
        QueryResult<UpdateResult> result = variantsCollection.update(query, update, new QueryOptions(MULTI, true));

        logger.debug("deleteStudy: query = {}", query);
        logger.debug("deleteStudy: update = {}", update);
        if (options.getBoolean("purge", false)) {
            Document purgeQuery = new Document(DocumentToVariantConverter.STUDIES_FIELD, new Document("$size", 0));
            variantsCollection.remove(purgeQuery, new QueryOptions(MULTI, true));
        }

        return result;
    }

    @Override
    public VariantQueryResult<Variant> get(Query query, QueryOptions options) {

        if (options == null) {
            options = new QueryOptions();
        }

        logger.info("******************** Summary => " + options.getBoolean("summary"));

        VariantQueryResult<Variant> queryResult;

        if (options.getBoolean("cache") && cacheManager.isTypeAllowed("var")) {
            List<Integer> studyIds = utils.getStudyIds(query.getAsList(VariantQueryParams.STUDIES.key()), options);
            // TODO : ONLY USING ONE STUDY ID ?
            String key = cacheManager.createKey(studyIds.get(0).toString(), "var", query, options);
            queryResult = new VariantQueryResult<>(cacheManager.get(key), null);
            if (queryResult.getResult() == null || queryResult.getResult().size() == 0) {
                queryResult = getVariantQueryResult(query, options);
                cacheManager.set(key, query, queryResult);
            }
        } else {
            if (options.getBoolean("summary", false) && storageConfiguration.getSearch().getActive()
                    && variantSearchManager != null && variantSearchManager.isAlive(credentials.getMongoDbName())) {
                try {
                    queryResult = variantSearchManager.query(credentials.getMongoDbName(), query, options);
                } catch (IOException | VariantSearchException e) {
                    throw Throwables.propagate(e);
                }
            } else {
                queryResult = getVariantQueryResult(query, options);
            }
        }
        return queryResult;
    }

    private VariantQueryResult<Variant> getVariantQueryResult(Query query, QueryOptions options) {
        Document mongoQuery = parseQuery(query);
        Document projection = createProjection(query, options);
//        logger.debug("Query to be executed: '{}'", mongoQuery.toJson(new JsonWriterSettings(JsonMode.SHELL, false)));
        options.putIfAbsent(QueryOptions.SKIP_COUNT, true);

        int defaultTimeout = configuration.getInt(DEFAULT_TIMEOUT.key(), DEFAULT_TIMEOUT.defaultValue());
        int maxTimeout = configuration.getInt(MAX_TIMEOUT.key(), MAX_TIMEOUT.defaultValue());
        int timeout = options.getInt(QueryOptions.TIMEOUT, defaultTimeout);
        if (timeout > maxTimeout || timeout < 0) {
            timeout = maxTimeout;
        }
        options.put(QueryOptions.TIMEOUT, timeout);

        // FIXME: MONGO_MIGRATION
//        if (options.getBoolean("mongodb.explain", false)) {
//            FindIterable<Document> dbCursor = variantsCollection.nativeQuery().find(mongoQuery, projection, options);
//            DBObject explain = dbCursor.explain();
//            try {
//                System.err.println("mongodb.explain = "
//                        + new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(explain));
//            } catch (JsonProcessingException ignore) {
//                System.err.println("mongodb.explain = " + explain);
//            }
//        }
        DocumentToVariantConverter converter = getDocumentToVariantConverter(query, options);
        Map<String, List<String>> samples = getDBAdaptorUtils().getSamplesMetadata(query, options);
        return new VariantQueryResult<>(variantsCollection.find(mongoQuery, projection, converter, options), samples);
    }

    @Override
    public List<VariantQueryResult<Variant>> get(List<Query> queries, QueryOptions options) {
        List<VariantQueryResult<Variant>> queryResultList = new ArrayList<>(queries.size());
        for (Query query : queries) {
            VariantQueryResult<Variant> queryResult = get(query, options);
            queryResultList.add(queryResult);
        }
        return queryResultList;
    }

    @Override
    public VariantQueryResult<Variant> getPhased(String varStr, String studyName, String sampleName, QueryOptions options,
                                                 int windowsSize) {
        StopWatch watch = new StopWatch();
        watch.start();

        Variant variant = new Variant(varStr);
        Region region = new Region(variant.getChromosome(), variant.getStart(), variant.getEnd());
        Query query = new Query(VariantQueryParams.REGION.key(), region)
                .append(VariantQueryParams.REFERENCE.key(), variant.getReference())
                .append(VariantQueryParams.ALTERNATE.key(), variant.getAlternate())
                .append(VariantQueryParams.STUDIES.key(), studyName)
                .append(VariantQueryParams.RETURNED_STUDIES.key(), studyName)
                .append(VariantQueryParams.RETURNED_SAMPLES.key(), sampleName);
        VariantQueryResult<Variant> queryResult = get(query, new QueryOptions());
        variant = queryResult.first();
        if (variant != null && !variant.getStudies().isEmpty()) {
            StudyEntry studyEntry = variant.getStudies().get(0);
            Integer psIdx = studyEntry.getFormatPositions().get(VCFConstants.PHASE_SET_KEY);
            if (psIdx != null) {
                String ps = studyEntry.getSamplesData().get(0).get(psIdx);
                if (!ps.equals(DocumentToSamplesConverter.UNKNOWN_FIELD)) {
                    sampleName = studyEntry.getOrderedSamplesName().get(0);

                    region.setStart(region.getStart() > windowsSize ? region.getStart() - windowsSize : 0);
                    region.setEnd(region.getEnd() + windowsSize);
                    query.remove(VariantQueryParams.REFERENCE.key());
                    query.remove(VariantQueryParams.ALTERNATE.key());
                    query.remove(VariantQueryParams.RETURNED_STUDIES.key());
                    query.remove(VariantQueryParams.RETURNED_SAMPLES.key());
                    queryResult = get(query, new QueryOptions(QueryOptions.SORT, true));
                    Iterator<Variant> iterator = queryResult.getResult().iterator();
                    while (iterator.hasNext()) {
                        Variant next = iterator.next();
                        if (!next.getStudies().isEmpty()) {
                            if (!ps.equals(next.getStudies().get(0).getSampleData(sampleName, VCFConstants.PHASE_SET_KEY))) {
                                iterator.remove();
                            }
                        }
                    }
                    queryResult.setNumResults(queryResult.getResult().size());
                    queryResult.setNumTotalResults(queryResult.getResult().size());
                    watch.stop();
                    queryResult.setDbTime(((int) watch.getTime()));
                    queryResult.setId("getPhased");
                    queryResult.setSamples(getDBAdaptorUtils().getSamplesMetadata(query, options));
                    return queryResult;
                }
            }
        }
        watch.stop();
        return new VariantQueryResult<>("getPhased", ((int) watch.getTime()), 0, 0, null, null, Collections.emptyList(), null);
    }

    @Override
    public QueryResult<Long> count(Query query) {
        Document mongoQuery = parseQuery(query);
        return variantsCollection.count(mongoQuery);
    }

    @Override
    public QueryResult distinct(Query query, String field) {
        String documentPath;
        switch (field) {
            case "gene":
            case "ensemblGene":
                documentPath = DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CT_ENSEMBL_GENE_ID_FIELD;
                break;
            case "ensemblTranscript":
                documentPath = DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CT_ENSEMBL_TRANSCRIPT_ID_FIELD;
                break;
            case "ct":
            case "consequence_type":
                documentPath = DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CT_SO_ACCESSION_FIELD;
                break;
            default:
                documentPath = DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CT_GENE_NAME_FIELD;
                break;
        }
        Document mongoQuery = parseQuery(query);
        return variantsCollection.distinct(documentPath, mongoQuery);
    }

    @Override
    public VariantDBIterator iterator() {
        return iterator(new Query(), new QueryOptions());
    }

    @Override
    public VariantDBIterator iterator(Query query, QueryOptions options) {
        if (options == null) {
            options = new QueryOptions();
        }
        if (query == null) {
            query = new Query();
        }

        if (options.getBoolean("summary", false) && storageConfiguration.getSearch().getActive()
                && variantSearchManager != null && variantSearchManager.isAlive(credentials.getMongoDbName())) {
            // Solr iterator
            try {
                return variantSearchManager.iterator(credentials.getMongoDbName(), query, options);
            } catch (VariantSearchException | IOException e) {
                e.printStackTrace();
            }
            //throw new UnsupportedOperationException("Summary option (i.e., Solr search) not implemented yet!!");
        } else {
            Document mongoQuery = parseQuery(query);
            Document projection = createProjection(query, options);
            DocumentToVariantConverter converter = getDocumentToVariantConverter(query, options);
            options.putIfAbsent(MongoDBCollection.BATCH_SIZE, 100);

            // Short unsorted queries with timeout or limit don't need the persistent cursor.
            if (options.containsKey(QueryOptions.TIMEOUT)
                    || options.containsKey(QueryOptions.LIMIT)
                    || !options.containsKey(QueryOptions.SORT)) {
                FindIterable<Document> dbCursor = variantsCollection.nativeQuery().find(mongoQuery, projection, options);
                return new VariantMongoDBIterator(dbCursor, converter);
            } else {
                return VariantMongoDBIterator.persistentIterator(variantsCollection, mongoQuery, projection, options, converter);
            }
        }
        return null;
    }

    @Override
    public void forEach(Consumer<? super Variant> action) {
        forEach(new Query(), action, new QueryOptions());
    }

    @Override
    public void forEach(Query query, Consumer<? super Variant> action, QueryOptions options) {
        Objects.requireNonNull(action);
        try (VariantDBIterator variantDBIterator = iterator(query, options)) {
            while (variantDBIterator.hasNext()) {
                action.accept(variantDBIterator.next());
            }
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }

    @Override
    public QueryResult getFrequency(Query query, Region region, int regionIntervalSize) {
        // db.variants.aggregate( { $match: { $and: [ {chr: "1"}, {start: {$gt: 251391, $lt: 2701391}} ] }},
        //                        { $group: { _id: { $subtract: [ { $divide: ["$start", 20000] }, { $divide: [{$mod: ["$start", 20000]},
        // 20000] } ] },
        //                                  totalCount: {$sum: 1}}})

        QueryOptions options = new QueryOptions();

        // If interval is not provided is set to the value that returns 200 values
        if (regionIntervalSize <= 0) {
//            regionIntervalSize = options.getInt("interval", (region.getEnd() - region.getStart()) / 200);
            regionIntervalSize = (region.getEnd() - region.getStart()) / 200;
        }

        Document start = new Document("$gt", region.getStart());
        start.append("$lt", region.getEnd());

        BasicDBList andArr = new BasicDBList();
        andArr.add(new Document(DocumentToVariantConverter.CHROMOSOME_FIELD, region.getChromosome()));
        andArr.add(new Document(DocumentToVariantConverter.START_FIELD, start));

        // Parsing the rest of options
        Document mongoQuery = parseQuery(query);
        if (!mongoQuery.isEmpty()) {
            andArr.add(mongoQuery);
        }
        Document match = new Document("$match", new Document("$and", andArr));

//        qb.and("_at.chunkIds").in(chunkIds);
//        qb.and(DBObjectToVariantConverter.END_FIELD).greaterThanEquals(region.getStart());
//        qb.and(DBObjectToVariantConverter.START_FIELD).lessThanEquals(region.getEnd());
//
//        List<String> chunkIds = getChunkIds(region);
//        DBObject regionObject = new Document("_at.chunkIds", new Document("$in", chunkIds))
//                .append(DBObjectToVariantConverter.END_FIELD, new Document("$gte", region.getStart()))
//                .append(DBObjectToVariantConverter.START_FIELD, new Document("$lte", region.getEnd()));

        BasicDBList divide1 = new BasicDBList();
        divide1.add("$start");
        divide1.add(regionIntervalSize);

        BasicDBList divide2 = new BasicDBList();
        divide2.add(new Document("$mod", divide1));
        divide2.add(regionIntervalSize);

        BasicDBList subtractList = new BasicDBList();
        subtractList.add(new Document("$divide", divide1));
        subtractList.add(new Document("$divide", divide2));

        Document subtract = new Document("$subtract", subtractList);
        Document totalCount = new Document("$sum", 1);
        Document g = new Document("_id", subtract);
        g.append("features_count", totalCount);
        Document group = new Document("$group", g);
        Document sort = new Document("$sort", new Document("_id", 1));

//        logger.info("getAllIntervalFrequencies - (>·_·)>");
//        System.out.println(options.toString());
//        System.out.println(match.toString());
//        System.out.println(group.toString());
//        System.out.println(sort.toString());

        long dbTimeStart = System.currentTimeMillis();
        QueryResult output = variantsCollection.aggregate(/*"$histogram", */Arrays.asList(match, group, sort), options);
        long dbTimeEnd = System.currentTimeMillis();

        Map<Long, Document> ids = new HashMap<>();
        // Create DBObject for intervals with features inside them
        for (Document intervalObj : (List<Document>) output.getResult()) {
            Long auxId = Math.round((Double) intervalObj.get("_id")); //is double

            Document intervalVisited = ids.get(auxId);
            if (intervalVisited == null) {
                intervalObj.put("_id", auxId);
                intervalObj.put("start", getChunkStart(auxId.intValue(), regionIntervalSize));
                intervalObj.put("end", getChunkEnd(auxId.intValue(), regionIntervalSize));
                intervalObj.put("chromosome", region.getChromosome());
                intervalObj.put("features_count", Math.log((int) intervalObj.get("features_count")));
                ids.put(auxId, intervalObj);
            } else {
                Double sum = (Double) intervalVisited.get("features_count") + Math.log((int) intervalObj.get("features_count"));
                intervalVisited.put("features_count", sum.intValue());
            }
        }

        // Create DBObject for intervals without features inside them
        BasicDBList resultList = new BasicDBList();
        int firstChunkId = getChunkId(region.getStart(), regionIntervalSize);
        int lastChunkId = getChunkId(region.getEnd(), regionIntervalSize);
        Document intervalObj;
        for (int chunkId = firstChunkId; chunkId <= lastChunkId; chunkId++) {
            intervalObj = ids.get((long) chunkId);
            if (intervalObj == null) {
                intervalObj = new Document();
                intervalObj.put("_id", chunkId);
                intervalObj.put("start", getChunkStart(chunkId, regionIntervalSize));
                intervalObj.put("end", getChunkEnd(chunkId, regionIntervalSize));
                intervalObj.put("chromosome", region.getChromosome());
                intervalObj.put("features_count", 0);
            }
            resultList.add(intervalObj);
        }

        QueryResult queryResult = new QueryResult(region.toString(), ((Long) (dbTimeEnd - dbTimeStart)).intValue(),
                resultList.size(), resultList.size(), null, null, resultList);
        return queryResult;
    }

    @Override
    public QueryResult rank(Query query, String field, int numResults, boolean asc) {
        QueryOptions options = new QueryOptions();
        options.put("limit", numResults);
        options.put("count", true);
        options.put("order", (asc) ? 1 : -1); // MongoDB: 1 = ascending, -1 = descending

        return groupBy(query, field, options);
    }

    @Override
    public QueryResult groupBy(Query query, String field, QueryOptions options) {
        String documentPath;
        String unwindPath;
        int numUnwinds = 2;
        switch (field) {
            case "gene":
            case "ensemblGene":
                documentPath = DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CT_ENSEMBL_GENE_ID_FIELD;
                unwindPath = DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD;

                break;
            case "ct":
            case "consequence_type":
                documentPath = DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CT_SO_ACCESSION_FIELD;
                unwindPath = DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD;
                numUnwinds = 3;
                break;
            default:
                documentPath = DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CT_GENE_NAME_FIELD;
                unwindPath = DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD;
                break;
        }

        Document mongoQuery = parseQuery(query);

        if (options == null) {
            options = new QueryOptions();
        } else {
            options = new QueryOptions(options); // Copy given QueryOptions.
        }
        boolean count = options.getBoolean("count", false);
        int order = options.getInt("order", -1);

        Document project;
        Document projectAndCount;
        if (count) {
            project = new Document("$project", new Document("field", "$" + documentPath));
            projectAndCount = new Document("$project", new Document()
                    .append("id", "$_id")
                    .append("_id", 0)
                    .append("count", new Document("$size", "$values")));
        } else {
            project = new Document("$project", new Document()
                    .append("field", "$" + documentPath)
                    //.append("_id._id", "$_id")
                    .append("_id.start", "$" + DocumentToVariantConverter.START_FIELD)
                    .append("_id.end", "$" + DocumentToVariantConverter.END_FIELD)
                    .append("_id.chromosome", "$" + DocumentToVariantConverter.CHROMOSOME_FIELD)
                    .append("_id.alternate", "$" + DocumentToVariantConverter.ALTERNATE_FIELD)
                    .append("_id.reference", "$" + DocumentToVariantConverter.REFERENCE_FIELD)
                    .append("_id.ids", "$" + DocumentToVariantConverter.IDS_FIELD));
            projectAndCount = new Document("$project", new Document()
                    .append("id", "$_id")
                    .append("_id", 0)
                    .append("values", "$values")
                    .append("count", new Document("$size", "$values")));
        }

        Document match = new Document("$match", mongoQuery);
        Document unwindField = new Document("$unwind", "$field");
        Document notNull = new Document("$match", new Document("field", new Document("$ne", null)));
        Document groupAndAddToSet = new Document("$group", new Document("_id", "$field")
                .append("values", new Document("$addToSet", "$_id"))); // sum, count, avg, ...?
        Document sort = new Document("$sort", new Document("count", order)); // 1 = ascending, -1 = descending

        int skip = options.getInt(QueryOptions.SKIP, -1);
        Document skipStep = skip > 0 ? new Document("$skip", skip) : null;

        int limit = options.getInt(QueryOptions.LIMIT, -1) > 0 ? options.getInt(QueryOptions.LIMIT) : 10;
        options.remove(QueryOptions.LIMIT); // Remove limit or Datastore will add a new limit step
        Document limitStep = new Document("$limit", limit);

        List<Bson> operations = new LinkedList<>();
        operations.add(match);
        operations.add(project);
        for (int i = 0; i < numUnwinds; i++) {
            operations.add(unwindField);
        }
        operations.add(notNull);
        operations.add(groupAndAddToSet);
        operations.add(projectAndCount);
        operations.add(sort);
        if (skipStep != null) {
            operations.add(skipStep);
        }
        operations.add(limitStep);
        logger.debug("db." + collectionName + ".aggregate( " + operations + " )");
        QueryResult<Document> queryResult = variantsCollection.aggregate(operations, options);

//            List<Map<String, Object>> results = new ArrayList<>(queryResult.getResult().size());
//            results.addAll(queryResult.getResult().stream().map(dbObject -> new ObjectMap("id", dbObject.get("_id")).append("count",
// dbObject.get("count"))).collect(Collectors.toList()));

        return queryResult;
    }

    @Override
    public QueryResult groupBy(Query query, List<String> fields, QueryOptions options) {
        String warningMsg = "Unimplemented VariantMongoDBAdaptor::groupBy list of fields. Using field[0] : '" + fields.get(0) + "'";
        logger.warn(warningMsg);
        QueryResult queryResult = groupBy(query, fields.get(0), options);
        queryResult.setWarningMsg(warningMsg);
        return queryResult;
    }

    @Override
    public QueryResult updateStats(List<VariantStatsWrapper> variantStatsWrappers, String studyName, QueryOptions options) {
        return updateStats(variantStatsWrappers, studyConfigurationManager.getStudyConfiguration(studyName, options).first(), options);
    }

    @Override
    public QueryResult updateStats(List<VariantStatsWrapper> variantStatsWrappers, StudyConfiguration studyConfiguration,
                                   QueryOptions options) {
//        MongoCollection<Document> coll = db.getDb().getCollection(collectionName);
//        BulkWriteOperation pullBuilder = coll.initializeUnorderedBulkOperation();
//        BulkWriteOperation pushBuilder = coll.initializeUnorderedBulkOperation();

        List<Bson> pullQueriesBulkList = new LinkedList<>();
        List<Bson> pullUpdatesBulkList = new LinkedList<>();

        List<Bson> pushQueriesBulkList = new LinkedList<>();
        List<Bson> pushUpdatesBulkList = new LinkedList<>();

        long start = System.nanoTime();
        DocumentToVariantStatsConverter statsConverter = new DocumentToVariantStatsConverter(studyConfigurationManager);
//        VariantSource variantSource = queryOptions.get(VariantStorageEngine.VARIANT_SOURCE, VariantSource.class);
        DocumentToVariantConverter variantConverter = getDocumentToVariantConverter(new Query(), options);
        boolean overwrite = options.getBoolean(VariantStorageEngine.Options.OVERWRITE_STATS.key(), false);
        //TODO: Use the StudyConfiguration to change names to ids

        // TODO make unset of 'st' if already present?
        for (VariantStatsWrapper wrapper : variantStatsWrappers) {
            Map<String, VariantStats> cohortStats = wrapper.getCohortStats();
            Iterator<VariantStats> iterator = cohortStats.values().iterator();
            VariantStats variantStats = iterator.hasNext() ? iterator.next() : null;
            List<Document> cohorts = statsConverter.convertCohortsToStorageType(cohortStats, studyConfiguration.getStudyId());   // TODO
            // remove when we remove fileId
//            List cohorts = statsConverter.convertCohortsToStorageType(cohortStats, variantSource.getStudyId());   // TODO use when we
// remove fileId

            // add cohorts, overwriting old values if that cid, fid and sid already exists: remove and then add
            // db.variants.update(
            //      {_id:<id>},
            //      {$pull:{st:{cid:{$in:["Cohort 1","cohort 2"]}, fid:{$in:["file 1", "file 2"]}, sid:{$in:["study 1", "study 2"]}}}}
            // )
            // db.variants.update(
            //      {_id:<id>},
            //      {$push:{st:{$each: [{cid:"Cohort 1", fid:"file 1", ... , defaultValue:3},{cid:"Cohort 2", ... , defaultValue:3}] }}}
            // )

            if (!cohorts.isEmpty()) {
                String id = variantConverter.buildStorageId(wrapper.getChromosome(), wrapper.getPosition(),
                        variantStats.getRefAllele(), variantStats.getAltAllele());


                Document find = new Document("_id", id);
                if (overwrite) {
                    List<Document> idsList = new ArrayList<>(cohorts.size());
                    for (Document cohort : cohorts) {
                        Document ids = new Document()
                                .append(DocumentToVariantStatsConverter.COHORT_ID, cohort.get(DocumentToVariantStatsConverter.COHORT_ID))
                                .append(DocumentToVariantStatsConverter.STUDY_ID, cohort.get(DocumentToVariantStatsConverter.STUDY_ID));
                        idsList.add(ids);
                    }
                    Document pull = new Document("$pull",
                            new Document(DocumentToVariantConverter.STATS_FIELD,
                                    new Document("$or", idsList)));
                    pullQueriesBulkList.add(find);
                    pullUpdatesBulkList.add(pull);
                }

                Document push = new Document("$push",
                        new Document(DocumentToVariantConverter.STATS_FIELD,
                                new Document("$each", cohorts)));
                pushQueriesBulkList.add(find);
                pushUpdatesBulkList.add(push);
            }
        }

        // TODO handle if the variant didn't had that studyId in the files array
        // TODO check the substitution is done right if the stats are already present
        if (overwrite) {
            variantsCollection.update(pullQueriesBulkList, pullUpdatesBulkList, new QueryOptions());
        }
        BulkWriteResult writeResult = variantsCollection.update(pushQueriesBulkList, pushUpdatesBulkList, new QueryOptions()).first();
        int writes = writeResult.getModifiedCount();


        return new QueryResult<>("", ((int) (System.nanoTime() - start)), writes, writes, "", "", Collections.singletonList(writeResult));
    }

    @Override
    public QueryResult deleteStats(String studyName, String cohortName, QueryOptions options) {
        StudyConfiguration studyConfiguration = studyConfigurationManager.getStudyConfiguration(studyName, options).first();
        int cohortId = studyConfiguration.getCohortIds().get(cohortName);

        // { st : { $elemMatch : {  sid : <studyId>, cid : <cohortId> } } }
        Document query = new Document(DocumentToVariantConverter.STATS_FIELD,
                new Document("$elemMatch",
                        new Document(DocumentToVariantStatsConverter.STUDY_ID, studyConfiguration.getStudyId())
                                .append(DocumentToVariantStatsConverter.COHORT_ID, cohortId)));

        // { $pull : { st : {  sid : <studyId>, cid : <cohortId> } } }
        Document update = new Document(
                "$pull",
                new Document(DocumentToVariantConverter.STATS_FIELD,
                        new Document(DocumentToVariantStatsConverter.STUDY_ID, studyConfiguration.getStudyId())
                                .append(DocumentToVariantStatsConverter.COHORT_ID, cohortId)
                )
        );
        logger.debug("deleteStats: query = {}", query);
        logger.debug("deleteStats: update = {}", update);

        return variantsCollection.update(query, update, new QueryOptions(MULTI, true));
    }

    @Override
    public QueryResult addAnnotations(List<VariantAnnotation> variantAnnotations, QueryOptions queryOptions) {
        logger.warn("Unimplemented VariantMongoDBAdaptor::addAnnotations. Using \"VariantMongoDBAdaptor::updateAnnotations\"");
        return updateAnnotations(variantAnnotations, queryOptions);
    }

    @Override
    public QueryResult updateAnnotations(List<VariantAnnotation> variantAnnotations, QueryOptions queryOptions) {

        List<Bson> queries = new LinkedList<>();
        List<Bson> updates = new LinkedList<>();

        long start = System.nanoTime();
        DocumentToVariantConverter variantConverter = getDocumentToVariantConverter(new Query(), queryOptions);
        for (VariantAnnotation variantAnnotation : variantAnnotations) {
            String id = variantConverter.buildStorageId(variantAnnotation.getChromosome(), variantAnnotation.getStart(),
                    variantAnnotation.getReference(), variantAnnotation.getAlternate());
            Document find = new Document("_id", id);
            DocumentToVariantAnnotationConverter converter = new DocumentToVariantAnnotationConverter();
            Document convertedVariantAnnotation = converter.convertToStorageType(variantAnnotation);
            Document update = new Document("$set", new Document(DocumentToVariantConverter.ANNOTATION_FIELD + ".0",
                    convertedVariantAnnotation));
            queries.add(find);
            updates.add(update);
        }
        BulkWriteResult writeResult = variantsCollection.update(queries, updates, null).first();

        return new QueryResult<>("", ((int) (System.nanoTime() - start)), 1, 1, "", "", Collections.singletonList(writeResult));
    }

    @Override
    public QueryResult updateCustomAnnotations(Query query, String name, AdditionalAttribute attribute, QueryOptions options) {
        Document queryDocument = parseQuery(query);
        Document updateDocument = DocumentToVariantAnnotationConverter.convertToStorageType(attribute);
        return variantsCollection.update(queryDocument,
                Updates.set(DocumentToVariantConverter.CUSTOM_ANNOTATION_FIELD + "." + name, updateDocument),
                new QueryOptions(MULTI, true));
    }

    @Override
    public QueryResult deleteAnnotation(String annotationId, Query query, QueryOptions queryOptions) {
        Document mongoQuery = parseQuery(query);
        logger.debug("deleteAnnotation: query = {}", mongoQuery);

        Document update = new Document("$set", new Document(DocumentToVariantConverter.ANNOTATION_FIELD + ".0", null));
        logger.debug("deleteAnnotation: update = {}", update);
        return variantsCollection.update(mongoQuery, update, new QueryOptions(MULTI, true));
    }


    @Override
    public void close() throws IOException {
        if (closeConnection) {
            mongoManager.close();
        }
        studyConfigurationManager.close();
        cacheManager.close();
        NUMBER_INSTANCES.decrementAndGet();
    }

    private Document parseQuery(final Query originalQuery) {
        QueryBuilder builder = new QueryBuilder();
        if (originalQuery != null) {
            // Copy given query. It may be modified
            Query query = new Query(originalQuery);
            boolean nonGeneRegionFilter = false;
            /* VARIANT PARAMS */
            if (isValidParam(query, VariantQueryParams.CHROMOSOME)) {
                nonGeneRegionFilter = true;
                List<String> chromosomes = query.getAsStringList(VariantQueryParams.CHROMOSOME.key());
                LinkedList<String> regions = new LinkedList<>(query.getAsStringList(VariantQueryParams.REGION.key()));
                regions.addAll(chromosomes);
                query.put(VariantQueryParams.REGION.key(), regions);
            }

            if (isValidParam(query, VariantQueryParams.REGION)) {
                nonGeneRegionFilter = true;
                List<String> stringList = query.getAsStringList(VariantQueryParams.REGION.key());
                List<Region> regions = new ArrayList<>(stringList.size());
                for (String reg : stringList) {
                    Region region = Region.parseRegion(reg);
                    regions.add(region);
                }
                getRegionFilter(regions, builder);
            }

            // List with all MongoIds from ID and XREF filters
            List<String> mongoIds = new ArrayList<>();

            if (isValidParam(query, VariantQueryParams.ID)) {
                nonGeneRegionFilter = true;
                List<String> idsList = query.getAsStringList(VariantQueryParams.ID.key());
                List<String> otherIds = new ArrayList<>(idsList.size());

                for (String value : idsList) {
                    Variant variant = toVariant(value);
                    if (variant != null) {
                        mongoIds.add(MongoDBVariantStageLoader.STRING_ID_CONVERTER.buildId(variant));
                    } else {
                        otherIds.add(value);
                    }
                }

                if (!otherIds.isEmpty()) {
                    String ids = otherIds.stream().collect(Collectors.joining(","));
                    addQueryStringFilter(DocumentToVariantConverter.ANNOTATION_FIELD
                            + "." + DocumentToVariantAnnotationConverter.XREFS_FIELD
                            + "." + DocumentToVariantAnnotationConverter.XREF_ID_FIELD, ids, builder, QueryOperation.OR);
                    addQueryStringFilter(DocumentToVariantConverter.IDS_FIELD, ids, builder, QueryOperation.OR);
                }
            }

            List<String> genes = new ArrayList<>(query.getAsStringList(VariantQueryParams.GENE.key()));

            if (isValidParam(query, VariantQueryParams.ANNOT_XREF)) {
                List<String> xrefs = query.getAsStringList(VariantQueryParams.ANNOT_XREF.key());
                List<String> otherXrefs = new ArrayList<>();
                for (String value : xrefs) {
                    Variant variant = toVariant(value);
                    if (variant != null) {
                        mongoIds.add(MongoDBVariantStageLoader.STRING_ID_CONVERTER.buildId(variant));
                    } else {
                        if (isVariantAccession(value) || isClinicalAccession(value) || isGeneAccession(value)) {
                            otherXrefs.add(value);
                        } else {
                            genes.add(value);
                        }
                    }
                }

                if (!otherXrefs.isEmpty()) {
                    nonGeneRegionFilter = true;
                    addQueryStringFilter(DocumentToVariantConverter.ANNOTATION_FIELD
                                    + '.' + DocumentToVariantAnnotationConverter.XREFS_FIELD
                                    + '.' + DocumentToVariantAnnotationConverter.XREF_ID_FIELD,
                            String.join(",", otherXrefs), builder, QueryOperation.OR);
                }
            }

            if (!genes.isEmpty()) {
                if (isValidParam(query, VariantQueryParams.ANNOT_CONSEQUENCE_TYPE)) {
                    List<String> soList = query.getAsStringList(VariantQueryParams.ANNOT_CONSEQUENCE_TYPE.key());
                    Set<String> gnSo = new HashSet<>(genes.size() * soList.size());
                    for (String gene : genes) {
                        for (String so : soList) {
                            int soNumber = parseConsequenceType(so);
                            gnSo.add(DocumentToVariantAnnotationConverter.buildGeneSO(gene, soNumber));
                        }
                    }
                    builder.or(new BasicDBObject(DocumentToVariantConverter.ANNOTATION_FIELD
                            + '.' + DocumentToVariantAnnotationConverter.GENE_SO_FIELD, new BasicDBObject("$in", gnSo)));
                    if (!nonGeneRegionFilter) {
                        // Filter already present in the GENE_SO_FIELD
                        query.remove(VariantQueryParams.ANNOT_CONSEQUENCE_TYPE.key());
                    }
                } else {
                    addQueryStringFilter(DocumentToVariantConverter.ANNOTATION_FIELD
                                    + '.' + DocumentToVariantAnnotationConverter.XREFS_FIELD
                                    + '.' + DocumentToVariantAnnotationConverter.XREF_ID_FIELD,
                            String.join(",", genes), builder, QueryOperation.OR);
                }
            }

            if (!mongoIds.isEmpty()) {
                if (mongoIds.size() == 1) {
                    builder.or(new QueryBuilder().and("_id").is(mongoIds.get(0)).get());
                } else {
                    builder.or(new QueryBuilder().and("_id").in(mongoIds).get());
                }
            }


            if (isValidParam(query, VariantQueryParams.REFERENCE)) {
                addQueryStringFilter(DocumentToVariantConverter.REFERENCE_FIELD, query.getString(VariantQueryParams.REFERENCE.key()),
                        builder, QueryOperation.AND);
            }

            if (isValidParam(query, VariantQueryParams.ALTERNATE)) {
                addQueryStringFilter(DocumentToVariantConverter.ALTERNATE_FIELD, query.getString(VariantQueryParams.ALTERNATE.key()),
                        builder, QueryOperation.AND);
            }

            if (isValidParam(query, VariantQueryParams.TYPE)) {
                addQueryFilter(DocumentToVariantConverter.TYPE_FIELD, query.getString(VariantQueryParams.TYPE.key()), builder,
                        QueryOperation.AND, s -> {
                            Set<VariantType> subTypes = Variant.subTypes(VariantType.valueOf(s));
                            List<String> types = new ArrayList<>(subTypes.size() + 1);
                            types.add(s);
                            subTypes.forEach(subType -> types.add(subType.toString()));
                            return types;
                        }); //addQueryStringFilter(DBObjectToVariantConverter.TYPE_FIELD,
//                query.getString(VariantQueryParams.TYPE.key()), builder, QueryOperation.AND);
            }

            /* ANNOTATION PARAMS */
            parseAnnotationQueryParams(query, builder);

            /* STUDIES */
            final StudyConfiguration defaultStudyConfiguration = parseStudyQueryParams(query, builder);

            /* STATS PARAMS */
            parseStatsQueryParams(query, builder, defaultStudyConfiguration);
        }
        logger.debug("Query         = {}", originalQuery == null ? "{}" : originalQuery.toJson());
        Document mongoQuery = new Document(builder.get().toMap());
        logger.debug("MongoDB Query = {}", mongoQuery.toJson(new JsonWriterSettings(JsonMode.SHELL, false)));
        return mongoQuery;
    }

    private void parseAnnotationQueryParams(Query query, QueryBuilder builder) {
        if (query != null) {
            if (isValidParam(query, VariantQueryParams.ANNOTATION_EXISTS)) {
                builder.and(DocumentToVariantConverter.ANNOTATION_FIELD + "." + DocumentToVariantAnnotationConverter.ANNOT_ID_FIELD);
                builder.exists(query.getBoolean(VariantQueryParams.ANNOTATION_EXISTS.key()));
            }

            if (isValidParam(query, VariantQueryParams.ANNOT_CONSEQUENCE_TYPE)) {
                String value = query.getString(VariantQueryParams.ANNOT_CONSEQUENCE_TYPE.key());
                addQueryFilter(DocumentToVariantConverter.ANNOTATION_FIELD
                                + '.' + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                                + '.' + DocumentToVariantAnnotationConverter.CT_SO_ACCESSION_FIELD, value, builder, QueryOperation.AND,
                        VariantDBAdaptorUtils::parseConsequenceType);
            }

            if (isValidParam(query, VariantQueryParams.ANNOT_BIOTYPE)) {
                String biotypes = query.getString(VariantQueryParams.ANNOT_BIOTYPE.key());
                addQueryStringFilter(DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CT_BIOTYPE_FIELD, biotypes, builder, QueryOperation.AND);
            }

            if (isValidParam(query, VariantQueryParams.ANNOT_POLYPHEN)) {
                String value = query.getString(VariantQueryParams.ANNOT_POLYPHEN.key());
//                addCompListQueryFilter(DocumentToVariantConverter.ANNOTATION_FIELD
//                                + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
//                                + "." + DocumentToVariantAnnotationConverter.CT_PROTEIN_POLYPHEN_FIELD
//                                + "." + DocumentToVariantAnnotationConverter.SCORE_SCORE_FIELD,
//                        value, builder);
                addScoreFilter(value, builder, VariantQueryParams.ANNOT_POLYPHEN, DocumentToVariantAnnotationConverter.POLYPHEN, true);
            }

            if (isValidParam(query, VariantQueryParams.ANNOT_SIFT)) {
                String value = query.getString(VariantQueryParams.ANNOT_SIFT.key());
//                addCompListQueryFilter(DocumentToVariantConverter.ANNOTATION_FIELD
//                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
//                        + "." + DocumentToVariantAnnotationConverter.CT_PROTEIN_SIFT_FIELD + "."
//                        + DocumentToVariantAnnotationConverter.SCORE_SCORE_FIELD, value, builder);
                addScoreFilter(value, builder, VariantQueryParams.ANNOT_SIFT, DocumentToVariantAnnotationConverter.SIFT, true);
            }

            if (isValidParam(query, VariantQueryParams.ANNOT_PROTEIN_SUBSTITUTION)) {
                String value = query.getString(VariantQueryParams.ANNOT_PROTEIN_SUBSTITUTION.key());
                addScoreFilter(value, builder, VariantQueryParams.ANNOT_PROTEIN_SUBSTITUTION, true);
            }

            if (isValidParam(query, VariantQueryParams.ANNOT_CONSERVATION)) {
                String value = query.getString(VariantQueryParams.ANNOT_CONSERVATION.key());
                addScoreFilter(value, builder, VariantQueryParams.ANNOT_CONSERVATION, false);
            }

            if (isValidParam(query, VariantQueryParams.ANNOT_TRANSCRIPTION_FLAGS)) {
                String value = query.getString(VariantQueryParams.ANNOT_TRANSCRIPTION_FLAGS.key());
                addQueryStringFilter(DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CT_TRANSCRIPT_ANNOT_FLAGS, value, builder, QueryOperation.AND);
            }

//            QueryBuilder geneTraitBuilder = QueryBuilder.start();
            if (isValidParam(query, VariantQueryParams.ANNOT_GENE_TRAITS_ID)) {
                String value = query.getString(VariantQueryParams.ANNOT_GENE_TRAITS_ID.key());
                addQueryStringFilter(DocumentToVariantConverter.ANNOTATION_FIELD
                        + '.' + DocumentToVariantAnnotationConverter.GENE_TRAIT_FIELD
                        + '.' + DocumentToVariantAnnotationConverter.GENE_TRAIT_ID_FIELD, value, builder, QueryOperation.AND);
            }

            if (isValidParam(query, VariantQueryParams.ANNOT_GENE_TRAITS_NAME)) {
                String value = query.getString(VariantQueryParams.ANNOT_GENE_TRAITS_NAME.key());
                addCompQueryFilter(DocumentToVariantConverter.ANNOTATION_FIELD
                        + '.' + DocumentToVariantAnnotationConverter.GENE_TRAIT_FIELD
                        + '.' + DocumentToVariantAnnotationConverter.GENE_TRAIT_NAME_FIELD, value, builder, false);
            }

            if (isValidParam(query, VariantQueryParams.ANNOT_HPO)) {
                String value = query.getString(VariantQueryParams.ANNOT_HPO.key());
//                addQueryStringFilter(DocumentToVariantAnnotationConverter.GENE_TRAIT_HPO_FIELD, value, geneTraitBuilder,
//                        QueryOperation.AND);
                addQueryStringFilter(DocumentToVariantConverter.ANNOTATION_FIELD
                                + '.' + DocumentToVariantAnnotationConverter.XREFS_FIELD
                                + '.' + DocumentToVariantAnnotationConverter.XREF_ID_FIELD, value, builder,
                        QueryOperation.AND);
            }

//            DBObject geneTraitQuery = geneTraitBuilder.get();
//            if (geneTraitQuery.keySet().size() != 0) {
//                builder.and(DocumentToVariantConverter.ANNOTATION_FIELD
//                        + "." + DocumentToVariantAnnotationConverter.GENE_TRAIT_FIELD).elemMatch(geneTraitQuery);
//            }

            if (isValidParam(query, VariantQueryParams.ANNOT_GO)) {
                String value = query.getString(VariantQueryParams.ANNOT_GO.key());

                // Check if comma separated of semi colon separated (AND or OR)
                QueryOperation queryOperation = checkOperator(value);
                // Split by comma or semi colon
                List<String> goValues = splitValue(value, queryOperation);

                if (queryOperation == QueryOperation.AND) {
                    throw VariantQueryException.malformedParam(VariantQueryParams.ANNOT_GO, value, "Unimplemented AND operator");
                }

                Set<String> genes = utils.getGenesByGo(goValues);

                builder.and(DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.XREFS_FIELD
                        + "." + DocumentToVariantAnnotationConverter.XREF_ID_FIELD).in(genes);

            }

            if (isValidParam(query, VariantQueryParams.ANNOT_EXPRESSION)) {
                String value = query.getString(VariantQueryParams.ANNOT_EXPRESSION.key());

                // Check if comma separated of semi colon separated (AND or OR)
                QueryOperation queryOperation = checkOperator(value);
                // Split by comma or semi colon
                List<String> expressionValues = splitValue(value, queryOperation);

                if (queryOperation == QueryOperation.AND) {
                    throw VariantQueryException.malformedParam(VariantQueryParams.ANNOT_EXPRESSION, value, "Unimplemented AND operator");
                }

                Set<String> genes = utils.getGenesByExpression(expressionValues);

                builder.and(DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.XREFS_FIELD
                        + "." + DocumentToVariantAnnotationConverter.XREF_ID_FIELD).in(genes);

            }


            if (isValidParam(query, VariantQueryParams.ANNOT_PROTEIN_KEYWORDS)) {
                String value = query.getString(VariantQueryParams.ANNOT_PROTEIN_KEYWORDS.key());
                addQueryStringFilter(DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CT_PROTEIN_KEYWORDS, value, builder, QueryOperation.AND);
            }

            if (isValidParam(query, VariantQueryParams.ANNOT_DRUG)) {
                String value = query.getString(VariantQueryParams.ANNOT_DRUG.key());
                addQueryStringFilter(DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.DRUG_FIELD
                        + "." + DocumentToVariantAnnotationConverter.DRUG_NAME_FIELD, value, builder, QueryOperation.AND);
            }

            if (isValidParam(query, VariantQueryParams.ANNOT_FUNCTIONAL_SCORE)) {
                String value = query.getString(VariantQueryParams.ANNOT_FUNCTIONAL_SCORE.key());
                addScoreFilter(value, builder, VariantQueryParams.ANNOT_FUNCTIONAL_SCORE, false);
            }

            if (isValidParam(query, VariantQueryParams.ANNOT_CUSTOM)) {
                String value = query.getString(VariantQueryParams.ANNOT_CUSTOM.key());
                addCompListQueryFilter(DocumentToVariantConverter.CUSTOM_ANNOTATION_FIELD, value, builder, true);
            }

            if (isValidParam(query, VariantQueryParams.ANNOT_POPULATION_ALTERNATE_FREQUENCY)) {
                String value = query.getString(VariantQueryParams.ANNOT_POPULATION_ALTERNATE_FREQUENCY.key());
                addFrequencyFilter(DocumentToVariantConverter.ANNOTATION_FIELD
                                + "." + DocumentToVariantAnnotationConverter.POPULATION_FREQUENCIES_FIELD,
                        DocumentToVariantAnnotationConverter.POPULATION_FREQUENCY_ALTERNATE_FREQUENCY_FIELD, value, builder,
                        VariantQueryParams.ANNOT_POPULATION_ALTERNATE_FREQUENCY); // Same
                // method addFrequencyFilter is used for reference and allele frequencies. Need to provide the field
                // (reference/alternate) where to check the frequency
            }

            if (isValidParam(query, VariantQueryParams.ANNOT_POPULATION_REFERENCE_FREQUENCY)) {
                String value = query.getString(VariantQueryParams.ANNOT_POPULATION_REFERENCE_FREQUENCY.key());
                addFrequencyFilter(DocumentToVariantConverter.ANNOTATION_FIELD
                                + "." + DocumentToVariantAnnotationConverter.POPULATION_FREQUENCIES_FIELD,
                        DocumentToVariantAnnotationConverter.POPULATION_FREQUENCY_REFERENCE_FREQUENCY_FIELD, value, builder,
                        VariantQueryParams.ANNOT_POPULATION_REFERENCE_FREQUENCY); // Same
                // method addFrequencyFilter is used for reference and allele frequencies. Need to provide the field
                // (reference/alternate) where to check the frequency
            }

            if (isValidParam(query, VariantQueryParams.ANNOT_POPULATION_MINOR_ALLELE_FREQUENCY)) {
                String value = query.getString(VariantQueryParams.ANNOT_POPULATION_MINOR_ALLELE_FREQUENCY.key());
                addFrequencyFilter(DocumentToVariantConverter.ANNOTATION_FIELD + "."
                                + DocumentToVariantAnnotationConverter.POPULATION_FREQUENCIES_FIELD,
                        value, builder, VariantQueryParams.ANNOT_POPULATION_MINOR_ALLELE_FREQUENCY,
                        (v, queryBuilder) -> {
                            String op = getOperator(v);
                            String obj = v.replaceFirst(op, "");

                            double aDouble = Double.parseDouble(obj);
                            switch (op) {
                                case "<":
                                    queryBuilder.or(QueryBuilder.start(DocumentToVariantAnnotationConverter.
                                                    POPULATION_FREQUENCY_REFERENCE_FREQUENCY_FIELD).lessThan(aDouble).get(),
                                            QueryBuilder.start(DocumentToVariantAnnotationConverter.
                                                    POPULATION_FREQUENCY_ALTERNATE_FREQUENCY_FIELD).lessThan(aDouble).get()
                                    );
                                    break;
                                case "<=":
                                    queryBuilder.or(QueryBuilder.start(DocumentToVariantAnnotationConverter.
                                                    POPULATION_FREQUENCY_REFERENCE_FREQUENCY_FIELD).lessThanEquals(aDouble).get(),
                                            QueryBuilder.start(DocumentToVariantAnnotationConverter.
                                                    POPULATION_FREQUENCY_ALTERNATE_FREQUENCY_FIELD).lessThanEquals(aDouble).get()
                                    );
                                    break;
                                case ">":
                                    queryBuilder.and(DocumentToVariantAnnotationConverter.
                                            POPULATION_FREQUENCY_REFERENCE_FREQUENCY_FIELD).greaterThan(aDouble)
                                            .and(DocumentToVariantAnnotationConverter.
                                                    POPULATION_FREQUENCY_ALTERNATE_FREQUENCY_FIELD).greaterThan(aDouble);
                                    break;
                                case ">=":
                                    queryBuilder.and(DocumentToVariantAnnotationConverter.
                                            POPULATION_FREQUENCY_REFERENCE_FREQUENCY_FIELD).greaterThanEquals(aDouble)
                                            .and(DocumentToVariantAnnotationConverter.
                                                    POPULATION_FREQUENCY_ALTERNATE_FREQUENCY_FIELD).greaterThanEquals(aDouble);
                                    break;
                                default:
                                    throw new IllegalArgumentException("Unsupported operator '" + op + "'");
                            }
                        });
            }
        }
    }

    private StudyConfiguration parseStudyQueryParams(Query query, QueryBuilder builder) {

        if (query != null) {
            Map<String, Integer> studies = getStudyConfigurationManager().getStudies(null);

            boolean singleStudy = studies.size() == 1;
            boolean validStudiesFilter = isValidParam(query, VariantQueryParams.STUDIES);
            // SAMPLES filter will add a FILES filter if absent
            boolean validFilesFilter = isValidParam(query, VariantQueryParams.FILES) || isValidParam(query, VariantQueryParams.SAMPLES);
            boolean otherFilters =
                       isValidParam(query, VariantQueryParams.FILES)
                    || isValidParam(query, VariantQueryParams.GENOTYPE)
                    || isValidParam(query, VariantQueryParams.SAMPLES)
                    || isValidParam(query, VariantQueryParams.FILTER);

            // Use an elemMatch with all the study filters if there is more than one study registered,
            // or FILES and STUDIES filters are being used.
            // If filters STUDIES+FILES is used, elemMatch is required to use the index correctly. See #493
            boolean studyElemMatch = (!singleStudy || (validFilesFilter && validStudiesFilter));

            // If only studyId filter is being used, elemMatch is not needed
            if (validStudiesFilter && !otherFilters) {
                studyElemMatch = false;
            }

            // If using an elemMatch for the study, keys don't need to start with "studies"
            String studyQueryPrefix = studyElemMatch ? "" : DocumentToVariantConverter.STUDIES_FIELD + '.';
            QueryBuilder studyBuilder = QueryBuilder.start();
            final StudyConfiguration defaultStudyConfiguration;

            if (isValidParam(query, VariantQueryParams.STUDIES)) {
                String sidKey = DocumentToVariantConverter.STUDIES_FIELD + '.' + DocumentToStudyVariantEntryConverter.STUDYID_FIELD;
                String value = query.getString(VariantQueryParams.STUDIES.key());

                // Check that the study exists
                QueryOperation studiesOperation = checkOperator(value);
                List<String> studiesNames = splitValue(value, studiesOperation);
                List<Integer> studyIds = utils.getStudyIds(studiesNames, studies); // Non negated studyIds

                // If the Studies query has an AND operator or includes negated fields, it can not be represented only
                // in the "elemMatch". It needs to be in the root
                boolean anyNegated = studiesNames.stream().anyMatch(VariantDBAdaptorUtils::isNegated);
                boolean studyFilterAtRoot = studiesOperation == QueryOperation.AND || anyNegated;
                if (studyFilterAtRoot) {
                    addQueryFilter(sidKey, value, builder, QueryOperation.AND, study -> utils.getStudyId(study, false, studies));
                }

                // Add all non negated studies to the elemMatch builder if it is being used,
                // or it is not and it has not been added to the root
                if (studyElemMatch || !studyFilterAtRoot) {
                    if (!studyIds.isEmpty()) {
                        if (!singleStudy || anyNegated || validFilesFilter) {
                            String studyIdsCsv = studyIds.stream().map(Object::toString).collect(Collectors.joining(","));
                            addQueryIntegerFilter(studyQueryPrefix + DocumentToStudyVariantEntryConverter.STUDYID_FIELD, studyIdsCsv,
                                    studyBuilder, QueryOperation.AND);
                        } // There is only one study! We can skip this filter
                    }
                }

                if (studyIds.size() == 1) {
                    defaultStudyConfiguration = studyConfigurationManager.getStudyConfiguration(studyIds.get(0), null).first();
                } else {
                    defaultStudyConfiguration = null;
                }

            } else {
                List<String> studyNames = studyConfigurationManager.getStudyNames(null);
                if (studyNames != null && studyNames.size() == 1) {
                    defaultStudyConfiguration = studyConfigurationManager.getStudyConfiguration(studyNames.get(0), null).first();
                } else {
                    defaultStudyConfiguration = null;
                }
            }

            if (isValidParam(query, VariantQueryParams.FILES)) {
                addQueryFilter(studyQueryPrefix + DocumentToStudyVariantEntryConverter.FILES_FIELD
                                + '.' + DocumentToStudyVariantEntryConverter.FILEID_FIELD,
                        query.getString(VariantQueryParams.FILES.key()), studyBuilder, QueryOperation.AND,
                        f -> utils.getFileId(f, false, defaultStudyConfiguration));
            }

            if (isValidParam(query, VariantQueryParams.FILTER)) {
                String filesValue = query.getString(VariantQueryParams.FILES.key());
                QueryOperation filesOperation = checkOperator(filesValue);
                List<String> fileNames = splitValue(filesValue, filesOperation);
                List<Integer> fileIds = utils.getFileIds(fileNames, true, defaultStudyConfiguration);

                String fileQueryPrefix;
                if (fileIds.isEmpty()) {
                    fileQueryPrefix = studyQueryPrefix + DocumentToStudyVariantEntryConverter.FILES_FIELD + '.';
                    addQueryStringFilter(fileQueryPrefix + DocumentToStudyVariantEntryConverter.ATTRIBUTES_FIELD + '.' + StudyEntry.FILTER,
                            query.getString(VariantQueryParams.FILTER.key()), studyBuilder, QueryOperation.AND);
                } else {
                    QueryBuilder fileBuilder = QueryBuilder.start();
                    addQueryStringFilter(DocumentToStudyVariantEntryConverter.ATTRIBUTES_FIELD + '.' + StudyEntry.FILTER,
                            query.getString(VariantQueryParams.FILTER.key()), fileBuilder, QueryOperation.AND);
                    fileBuilder.and(DocumentToStudyVariantEntryConverter.FILEID_FIELD).in(fileIds);
                    studyBuilder.and(studyQueryPrefix + DocumentToStudyVariantEntryConverter.FILES_FIELD).elemMatch(fileBuilder.get());
                }
            }

            Map<Object, List<String>> genotypesFilter = new HashMap<>();
            if (isValidParam(query, VariantQueryParams.GENOTYPE)) {
                String sampleGenotypes = query.getString(VariantQueryParams.GENOTYPE.key());
                parseGenotypeFilter(sampleGenotypes, genotypesFilter);
            }

            if (isValidParam(query, VariantQueryParams.SAMPLES)) {
                Set<Integer> files = new HashSet<>();
                String samples = query.getString(VariantQueryParams.SAMPLES.key());

                for (String sample : samples.split(",")) {
                    int sampleId = utils.getSampleId(sample, defaultStudyConfiguration);
                    genotypesFilter.put(sampleId, Arrays.asList(
                            "1",
                            "0/1", "0|1", "1|0",
                            "1/1", "1|1",
                            "1/2", "1|2", "2|1"
                    ));
                    if (!isValidParam(query, VariantQueryParams.FILES) && defaultStudyConfiguration != null) {
                        for (Integer file : defaultStudyConfiguration.getIndexedFiles()) {
                            if (defaultStudyConfiguration.getSamplesInFiles().get(file).contains(sampleId)) {
                                files.add(file);
                            }
                        }
                    }
                }

                // If there is no valid files filter, add files filter to speed up this query
                if (!isValidParam(query, VariantQueryParams.FILES) && !files.isEmpty()) {
                    addQueryFilter(studyQueryPrefix + DocumentToStudyVariantEntryConverter.FILES_FIELD
                                    + '.' + DocumentToStudyVariantEntryConverter.FILEID_FIELD, files, studyBuilder, QueryOperation.AND,
                            f -> utils.getFileId(f, false, defaultStudyConfiguration));
                }
            }

            if (!genotypesFilter.isEmpty()) {
                for (Map.Entry<Object, List<String>> entry : genotypesFilter.entrySet()) {
                    Object sample = entry.getKey();
                    List<String> genotypes = entry.getValue();

                    int sampleId = utils.getSampleId(sample, defaultStudyConfiguration);

                    QueryBuilder genotypesBuilder = QueryBuilder.start();

                    List<String> defaultGenotypes;
                    if (defaultStudyConfiguration != null) {
                        defaultGenotypes = defaultStudyConfiguration.getAttributes().getAsStringList(DEFAULT_GENOTYPE.key());
                    } else {
                        defaultGenotypes = Arrays.asList("0/0", "0|0");
                    }
                    for (String genotype : genotypes) {
                        boolean negated = isNegated(genotype);
                        if (negated) {
                            genotype = genotype.substring(1);
                        }
                        if (defaultGenotypes.contains(genotype)) {
                            List<String> otherGenotypes = Arrays.asList(
                                    "0/0", "0|0",
                                    "0/1", "1/0", "1/1", "-1/-1",
                                    "0|1", "1|0", "1|1", "-1|-1",
                                    "0|2", "2|0", "2|1", "1|2", "2|2",
                                    "0/2", "2/0", "2/1", "1/2", "2/2",
                                    DocumentToSamplesConverter.UNKNOWN_GENOTYPE);
                            if (negated) {
                                for (String otherGenotype : otherGenotypes) {
                                    if (defaultGenotypes.contains(otherGenotype)) {
                                        continue;
                                    }
                                    String key = studyQueryPrefix
                                            + DocumentToStudyVariantEntryConverter.GENOTYPES_FIELD
                                            + '.' + otherGenotype;
                                    genotypesBuilder.or(new BasicDBObject(key, sampleId));
                                }
                            } else {
                                QueryBuilder andBuilder = QueryBuilder.start();
                                for (String otherGenotype : otherGenotypes) {
                                    if (defaultGenotypes.contains(otherGenotype)) {
                                        continue;
                                    }
                                    String key = studyQueryPrefix
                                            + DocumentToStudyVariantEntryConverter.GENOTYPES_FIELD
                                            + '.' + otherGenotype;
                                    andBuilder.and(new BasicDBObject(key,
                                            new Document("$ne", sampleId)));
                                }
                                genotypesBuilder.or(andBuilder.get());
                            }
                        } else {
                            String s = studyQueryPrefix
                                    + DocumentToStudyVariantEntryConverter.GENOTYPES_FIELD
                                    + '.' + DocumentToSamplesConverter.genotypeToStorageType(genotype);
                            if (negated) {
                                //and [ {"gt.0|1" : { $ne : <sampleId> } } ]
                                genotypesBuilder.and(new BasicDBObject(s, new BasicDBObject("$ne", sampleId)));

                            } else {
                                //or [ {"gt.0|1" : <sampleId> } ]
                                genotypesBuilder.or(new BasicDBObject(s, sampleId));
                            }
                        }
                    }
                    studyBuilder.and(genotypesBuilder.get());
                }
            }

            // If Study Query is used then we add a elemMatch query
            DBObject studyQuery = studyBuilder.get();
            if (!studyQuery.keySet().isEmpty()) {
                if (studyElemMatch) {
                    builder.and(DocumentToVariantConverter.STUDIES_FIELD).elemMatch(studyQuery);
                } else {
                    builder.and(studyQuery);
                }
            }
            return defaultStudyConfiguration;
        } else {
            return null;
        }
    }

    private void parseStatsQueryParams(Query query, QueryBuilder builder, StudyConfiguration defaultStudyConfiguration) {
        if (query != null) {
            if (query.get(VariantQueryParams.COHORTS.key()) != null && !query.getString(VariantQueryParams.COHORTS.key()).isEmpty()) {
                addQueryFilter(DocumentToVariantConverter.STATS_FIELD
                                + "." + DocumentToVariantStatsConverter.COHORT_ID,
                        query.getString(VariantQueryParams.COHORTS.key()), builder, QueryOperation.AND,
                        s -> {
                            try {
                                return Integer.parseInt(s);
                            } catch (NumberFormatException ignore) {
                                int indexOf = s.lastIndexOf(":");
                                if (defaultStudyConfiguration == null && indexOf < 0) {
                                    throw VariantQueryException.malformedParam(VariantQueryParams.COHORTS, s, "Expected {study}:{cohort}");
                                } else {
                                    String study;
                                    String cohort;
                                    Integer cohortId;
                                    if (defaultStudyConfiguration != null && indexOf < 0) {
                                        cohort = s;
                                        cohortId = utils.getCohortId(cohort, defaultStudyConfiguration);
                                    } else {
                                        study = s.substring(0, indexOf);
                                        cohort = s.substring(indexOf + 1);
                                        StudyConfiguration studyConfiguration =
                                                utils.getStudyConfiguration(study, defaultStudyConfiguration);
                                        cohortId = utils.getCohortId(cohort, studyConfiguration);
                                    }
                                    return cohortId;
                                }
                            }
                        });
            }

            if (query.get(VariantQueryParams.STATS_MAF.key()) != null && !query.getString(VariantQueryParams.STATS_MAF.key()).isEmpty()) {
                addStatsFilterList(DocumentToVariantStatsConverter.MAF_FIELD, query.getString(VariantQueryParams.STATS_MAF.key()),
                        builder, defaultStudyConfiguration);
            }

            if (query.get(VariantQueryParams.STATS_MGF.key()) != null && !query.getString(VariantQueryParams.STATS_MGF.key()).isEmpty()) {
                addStatsFilterList(DocumentToVariantStatsConverter.MGF_FIELD, query.getString(VariantQueryParams.STATS_MGF.key()),
                        builder, defaultStudyConfiguration);
            }

            if (query.get(VariantQueryParams.MISSING_ALLELES.key()) != null && !query.getString(VariantQueryParams.MISSING_ALLELES.key())
                    .isEmpty()) {
                addStatsFilterList(DocumentToVariantStatsConverter.MISSALLELE_FIELD, query.getString(VariantQueryParams.MISSING_ALLELES
                        .key()), builder, defaultStudyConfiguration);
            }

            if (query.get(VariantQueryParams.MISSING_GENOTYPES.key()) != null && !query.getString(VariantQueryParams.MISSING_GENOTYPES
                    .key()).isEmpty()) {
                addStatsFilterList(DocumentToVariantStatsConverter.MISSGENOTYPE_FIELD, query.getString(VariantQueryParams
                        .MISSING_GENOTYPES.key()), builder, defaultStudyConfiguration);
            }

            if (query.get("numgt") != null && !query.getString("numgt").isEmpty()) {
                for (String numgt : query.getAsStringList("numgt")) {
                    String[] split = numgt.split(":");
                    addCompQueryFilter(
                            DocumentToVariantConverter.STATS_FIELD + "." + DocumentToVariantStatsConverter.NUMGT_FIELD + "." + split[0],
                            split[1], builder, false);
                }
            }
        }
    }

    private Document createProjection(Query query, QueryOptions options) {
        Document projection = new Document();

        if (options == null) {
            options = new QueryOptions();
        }

        if (options.containsKey(QueryOptions.SORT) && !options.getString(QueryOptions.SORT).equals("_id")) {
            if (options.getBoolean(QueryOptions.SORT)) {
                options.put(QueryOptions.SORT, "_id");
                options.putIfAbsent(QueryOptions.ORDER, QueryOptions.ASCENDING);
            } else {
                options.remove(QueryOptions.SORT);
            }
        }

        Set<VariantField> returnedFields = VariantField.getReturnedFields(options);
        // Add all required fields
        returnedFields.addAll(DocumentToVariantConverter.REQUIRED_FIELDS_SET);
        if (returnedFields.contains(VariantField.STUDIES) && !returnedFields.contains(VariantField.STUDIES_STUDY_ID)) {
            returnedFields.add(VariantField.STUDIES_STUDY_ID);
        }

        returnedFields = VariantField.prune(returnedFields);

        if (!returnedFields.isEmpty()) { //Include some
            for (VariantField s : returnedFields) {
                List<String> keys = DocumentToVariantConverter.toShortFieldName(s);
//                String key = DocumentToVariantConverter.toShortFieldName(s.fieldName());
                if (keys != null) {
                    for (String key : keys) {
                        projection.put(key, 1);
                    }
                } else {
                    logger.warn("Unknown include field: {}", s);
                }
            }
        }

//        if (query.containsKey(VariantQueryParams.RETURNED_FILES.key()) && projection.containsKey(DocumentToVariantConverter
//                .STUDIES_FIELD)) {
//            List<Integer> files = query.getAsIntegerList(VariantQueryParams.RETURNED_FILES.key());
//            projection.put(
//                    DocumentToVariantConverter.STUDIES_FIELD,
//                    new Document(
//                            "$elemMatch",
//                            new Document(
//                                    DocumentToStudyVariantEntryConverter.FILES_FIELD + "." + DocumentToStudyVariantEntryConverter
//                                            .FILEID_FIELD,
//                                    new Document(
//                                            "$in",
//                                            files
//                                    )
//                            )
//                    )
//            );
//        }

        List<Integer> studiesIds = utils.getReturnedStudies(query, options);
        // Use elemMatch only if there is one study to return.
        if (studiesIds.size() == 1) {
            projection.put(
                    DocumentToVariantConverter.STUDIES_FIELD,
                    new Document(
                            "$elemMatch",
                            new Document(
                                    DocumentToStudyVariantEntryConverter.STUDYID_FIELD,
                                    new Document(
                                            "$in",
                                            studiesIds
                                    )
                            )
                    )
            );
        }

        logger.debug("QueryOptions: = {}", options.toJson());
        logger.debug("Projection:   = {}", projection.toJson(new JsonWriterSettings(JsonMode.SHELL, false)));
        return projection;
    }

    /**
     * Two steps insertion:
     * First check that the variant and study exists making an update.
     * For those who doesn't exist, pushes a study with the file and genotype information
     * <p>
     * The documents that throw a "dup key" exception are those variants that exist and have the study.
     * Then, only for those variants, make a second update.
     * <p>
     * *An interesting idea would be to invert this actions depending on the number of already inserted variants.
     *
     * @param data                        Variants to insert
     * @param fileId                      File ID
     * @param variantConverter            Variant converter to be used
     * @param variantSourceEntryConverter Variant source converter to be used
     * @param studyConfiguration          Configuration for the study
     * @param loadedSampleIds             Other loaded sampleIds EXCEPT those that are going to be loaded
     * @return QueryResult object
     */
    QueryResult<MongoDBVariantWriteResult> insert(List<Variant> data, int fileId, DocumentToVariantConverter variantConverter,
                                                  DocumentToStudyVariantEntryConverter variantSourceEntryConverter,
                                                  StudyConfiguration studyConfiguration, List<Integer> loadedSampleIds) {

        MongoDBVariantWriteResult writeResult = new MongoDBVariantWriteResult();
        long startTime = System.currentTimeMillis();
        if (data.isEmpty()) {
            return new QueryResult<>("insertVariants", 0, 1, 1, "", "", Collections.singletonList(writeResult));
        }
        List<Bson> queries = new ArrayList<>(data.size());
        List<Bson> updates = new ArrayList<>(data.size());
        // Use a multiset instead of a normal set, to keep tracking of duplicated variants
        Multiset<String> nonInsertedVariants = HashMultiset.create();
        String fileIdStr = Integer.toString(fileId);

//        List<String> extraFields = studyConfiguration.getAttributes().getAsStringList(VariantStorageEngine.Options.EXTRA_GENOTYPE_FIELDS
//                .key());
        boolean excludeGenotypes = studyConfiguration.getAttributes().getBoolean(VariantStorageEngine.Options.EXCLUDE_GENOTYPES.key(),
                VariantStorageEngine.Options.EXCLUDE_GENOTYPES.defaultValue());

        long nanoTime = System.nanoTime();
        Map missingSamples = Collections.emptyMap();
        String defaultGenotype = studyConfiguration.getAttributes().getString(DEFAULT_GENOTYPE.key(), "");
        if (defaultGenotype.equals(DocumentToSamplesConverter.UNKNOWN_GENOTYPE)) {
            logger.debug("Do not need fill gaps. DefaultGenotype is UNKNOWN_GENOTYPE({}).", DocumentToSamplesConverter.UNKNOWN_GENOTYPE);
        } else if (excludeGenotypes) {
            logger.debug("Do not need fill gaps. Excluding genotypes.");
        } else if (!loadedSampleIds.isEmpty()) {
            missingSamples = new Document(DocumentToSamplesConverter.UNKNOWN_GENOTYPE, loadedSampleIds);   // ?/?
        }
//            List<Object> missingOtherValues = new ArrayList<>(loadedSampleIds.size());
//            for (int i = 0; i < loadedSampleIds.size(); i++) {
//                missingOtherValues.add(DBObjectToSamplesConverter.UNKNOWN_FIELD);
//            }
        for (Variant variant : data) {
            if (variant.getType().equals(VariantType.NO_VARIATION)) {
                //Storage-MongoDB is not able to store NON VARIANTS
                writeResult.setSkippedVariants(writeResult.getSkippedVariants() + 1);
                continue;
            } else if (variant.getType().equals(VariantType.SYMBOLIC)) {
                logger.warn("Skip symbolic variant " + variant.toString());
                writeResult.setSkippedVariants(writeResult.getSkippedVariants() + 1);
                continue;
            }
            String id = variantConverter.buildStorageId(variant);
            for (StudyEntry studyEntry : variant.getStudies()) {
                if (studyEntry.getFiles().size() == 0 || !studyEntry.getFiles().get(0).getFileId().equals(fileIdStr)) {
                    continue;
                }
                int studyId = studyConfiguration.getStudyId();
                Document study = variantSourceEntryConverter.convertToStorageType(variant, studyEntry);
                Document genotypes = study.get(DocumentToStudyVariantEntryConverter.GENOTYPES_FIELD, Document.class);
                if (genotypes != null) {        //If genotypes is null, genotypes are not suppose to be loaded
                    genotypes.putAll(missingSamples);   //Add missing samples
//                        for (String extraField : extraFields) {
//                            List<Object> otherFieldValues = (List<Object>) study.get(extraField.toLowerCase());
//                            otherFieldValues.addAll(0, missingOtherValues);
//                        }
                }
                Document push = new Document(DocumentToVariantConverter.STUDIES_FIELD, study);
                Document update = new Document()
                        .append("$push", push)
                        .append("$setOnInsert", variantConverter.convertToStorageType(variant));
                if (variant.getIds() != null && !variant.getIds().isEmpty() && !variant.getIds().iterator().next().isEmpty()) {
                    update.put("$addToSet", new Document(DocumentToVariantConverter.IDS_FIELD, new Document("$each",
                            variant.getIds())));
                }
                // { _id: <variant_id>, "studies.sid": {$ne: <studyId> } }
                //If the variant exists and contains the study, this find will fail, will try to do the upsert, and throw a
                // duplicated key exception.
                queries.add(new Document("_id", id).append(DocumentToVariantConverter.STUDIES_FIELD
                                + "." + DocumentToStudyVariantEntryConverter.STUDYID_FIELD,
                        new Document("$ne", studyId)));
                updates.add(update);
            }
        }

        //
        if (!queries.isEmpty()) {
            QueryOptions options = new QueryOptions(UPSERT, true);
            options.put(MULTI, false);
            int newDocuments;
            int updatedObjects;

            try {
                BulkWriteResult bulkWriteResult;
                bulkWriteResult = variantsCollection.update(queries, updates, options).first();
                newDocuments = bulkWriteResult.getUpserts().size();
                updatedObjects = bulkWriteResult.getModifiedCount();
            } catch (MongoBulkWriteException e) {
                BulkWriteResult bulkWriteResult;
                bulkWriteResult = e.getWriteResult();
                newDocuments = bulkWriteResult.getUpserts().size();
                updatedObjects = bulkWriteResult.getModifiedCount();
                for (BulkWriteError writeError : e.getWriteErrors()) {
                    if (writeError.getCode() == 11000) { //Dup Key error code
                        Matcher matcher = writeResultErrorPattern.matcher(writeError.getMessage());
                        if (matcher.find()) {
                            String id = matcher.group(1);
                            nonInsertedVariants.add(id);
                        } else {
                            throw e;
                        }
                    } else {
                        throw e;
                    }
                }
            }

            writeResult.setNewVariants(newDocuments);
            writeResult.setUpdatedVariants(updatedObjects);
//                writeResult.setNewDocuments(data.size() - nonInsertedVariants.size() - writeResult.getSkippedVariants());
            queries.clear();
            updates.clear();
        }
        writeResult.setNewVariantsNanoTime(System.nanoTime() - nanoTime);
        nanoTime = System.nanoTime();

        for (Variant variant : data) {
            variant.setAnnotation(null);
            String id = variantConverter.buildStorageId(variant);

            if (nonInsertedVariants != null && !nonInsertedVariants.contains(id)) {
                continue;   //Already inserted variant
            }

            for (StudyEntry studyEntry : variant.getStudies()) {
                if (studyEntry.getFiles().size() == 0 || !studyEntry.getFiles().get(0).getFileId().equals(fileIdStr)) {
                    continue;
                }

                Document studyObject = variantSourceEntryConverter.convertToStorageType(variant, studyEntry);
                Document genotypes = studyObject.get(DocumentToStudyVariantEntryConverter.GENOTYPES_FIELD, Document.class);
                Document push = new Document();

                if (!excludeGenotypes) {
                    if (genotypes != null) { //If genotypes is null, genotypes are not suppose to be loaded
                        for (String genotype : genotypes.keySet()) {
                            push.put(DocumentToVariantConverter.STUDIES_FIELD + ".$." + DocumentToStudyVariantEntryConverter.GENOTYPES_FIELD
                                    + "." + genotype, new Document("$each", genotypes.get(genotype)));
                        }
//                    for (String extraField : extraFields) {
//                        List values = (List) studyObject.get(extraField.toLowerCase());
//                        push.put(DBObjectToVariantConverter.STUDIES_FIELD + ".$." + extraField.toLowerCase(),
//                                new Document("$each", values).append("$position", loadedSampleIds.size()));
//                    }
                    } else {
                        push.put(DocumentToVariantConverter.STUDIES_FIELD + ".$." + DocumentToStudyVariantEntryConverter.GENOTYPES_FIELD,
                                Collections.emptyMap());
                    }
                }
                push.put(DocumentToVariantConverter.STUDIES_FIELD + ".$." + DocumentToStudyVariantEntryConverter.FILES_FIELD, ((List)
                        studyObject.get(DocumentToStudyVariantEntryConverter.FILES_FIELD)).get(0));
                Document update = new Document(new Document("$push", push));

                queries.add(new Document("_id", id)
                        .append(DocumentToVariantConverter.STUDIES_FIELD
                                + '.' + DocumentToStudyVariantEntryConverter.STUDYID_FIELD, studyConfiguration.getStudyId())
                        .append(DocumentToVariantConverter.STUDIES_FIELD
                                + '.' + DocumentToStudyVariantEntryConverter.FILES_FIELD
                                + '.' + DocumentToStudyVariantEntryConverter.FILEID_FIELD, new Document("$ne", fileId))
                );
                updates.add(update);

            }
        }
        writeResult.setExistingVariantsNanoTime(System.nanoTime() - nanoTime);

        if (!queries.isEmpty()) {
            QueryOptions options = new QueryOptions(UPSERT, false);
            options.put(MULTI, false);
            QueryResult<BulkWriteResult> update = variantsCollection.update(queries, updates, options);
            // Can happen that nonInsertedVariantsNum != queries.size() != nonInsertedVariants.size() if there was
            // a duplicated variant.
            writeResult.setNonInsertedVariants(nonInsertedVariants.size() - update.first().getMatchedCount());
            writeResult.setUpdatedVariants(writeResult.getUpdatedVariants() + update.first().getModifiedCount());
        }

        return new QueryResult<>("insertVariants", ((int) (System.currentTimeMillis() - startTime)), 1, 1, "", "",
                Collections.singletonList(writeResult));
    }


    /**
     * Fills the missing genotype values for the new loaded samples.
     * Missing data is which was present in the database but not in the input file.
     * Data present in the file but not in the database is added during the {@link #insert} step.
     * <p>
     * +--------+---------+
     * | Loaded | NewFile |
     * +--------+--------+---------+
     * | 10:A:T | DATA   |         |   <- Missing data to be filled
     * +--------+--------+---------+
     * | 20:C:T |        | DATA    |   <- Missing data already filled in the {@link #insert} step.
     * +--------+--------+---------+
     *
     * @param fileId             Loading File ID
     * @param chromosomes        Chromosomes covered by the current file
     * @param fileSampleIds      FileSampleIds
     * @param studyConfiguration StudyConfiguration
     * @return WriteResult
     */
    QueryResult<UpdateResult> fillFileGaps(int fileId, List<String> chromosomes, List<Integer> fileSampleIds,
                                           StudyConfiguration studyConfiguration) {

        // { "studies.sid" : <studyId>, "studies.files.fid" : { $ne : <fileId> } },
        // { $push : {
        //      "studies.$.gt.?/?" : {$each : [ <fileSampleIds> ] }
        // } }
        if (studyConfiguration.getAttributes().getAsStringList(DEFAULT_GENOTYPE.key(), "")
                .equals(Collections.singletonList(DocumentToSamplesConverter.UNKNOWN_GENOTYPE))
//                && studyConfiguration.getAttributes().getAsStringList(VariantStorageEngine.Options.EXTRA_GENOTYPE_FIELDS.key()).isEmpty()
                ) {
            // Check if the default genotype is the unknown genotype. In that case, is not required to fill missing genotypes.
            // Previously, also checks if there where EXTRA_GENOTYPE_FIELDS like DP:AD,... . In that case, those arrays had to be filled.
            logger.debug("Do not need fill gaps. DefaultGenotype is UNKNOWN_GENOTYPE({}).", DocumentToSamplesConverter.UNKNOWN_GENOTYPE);
            return new QueryResult<>();
        } else if (studyConfiguration.getAttributes().getBoolean(VariantStorageEngine.Options.EXCLUDE_GENOTYPES.key(),
                VariantStorageEngine.Options.EXCLUDE_GENOTYPES.defaultValue())) {
            // Check if the genotypes are not required. In that case, no fillGaps is needed
            logger.debug("Do not need fill gaps. Exclude genotypes.");
            return new QueryResult<>();
        } else {
            BiMap<String, Integer> indexedSamples = StudyConfiguration.getIndexedSamples(studyConfiguration);
            if (indexedSamples.isEmpty() || indexedSamples.values().equals(new HashSet<>(fileSampleIds))) {
                // If the loaded samples match with the current samples means that there where no other samples loaded.
                // There were no gaps, so it is not needed to fill anything.
                logger.debug("Do not need fill gaps. First sample batch.");
                return new QueryResult<>();
            }
        }
        logger.debug("Do fill gaps.");


        Document query = new Document();
        if (chromosomes != null && !chromosomes.isEmpty()) {
            query.put(DocumentToVariantConverter.CHROMOSOME_FIELD, new Document("$in", chromosomes));
        }

        query.put(DocumentToVariantConverter.STUDIES_FIELD, new Document("$elemMatch",
                new Document(
                        DocumentToStudyVariantEntryConverter.STUDYID_FIELD,
                        studyConfiguration.getStudyId())
                        .append(DocumentToStudyVariantEntryConverter.FILES_FIELD + "." + DocumentToStudyVariantEntryConverter.FILEID_FIELD,
                                new Document("$ne", fileId)
                        )
        ));

        Document push = new Document()
                .append(DocumentToVariantConverter.STUDIES_FIELD
                        + ".$." + DocumentToStudyVariantEntryConverter.GENOTYPES_FIELD
                        + "." + DocumentToSamplesConverter.UNKNOWN_GENOTYPE, new Document("$each", fileSampleIds));

//        List<Integer> loadedSamples = getLoadedSamples(fileId, studyConfiguration);
//        List<Object> missingOtherValues = new ArrayList<>(fileSampleIds.size());
//        for (int size = fileSampleIds.size(); size > 0; size--) {
//            missingOtherValues.add(DBObjectToSamplesConverter.UNKNOWN_FIELD);
//        }
//        List<String> extraFields = studyConfiguration.getAttributes()
//                .getAsStringList(VariantStorageEngine.Options.EXTRA_GENOTYPE_FIELDS.key());
//        for (String extraField : extraFields) {
//            push.put(DBObjectToVariantConverter.STUDIES_FIELD + ".$." + extraField.toLowerCase(),
//                    new Document("$each", missingOtherValues).append("$position", loadedSamples.size())
//            );
//        }

        Document update = new Document("$push", push);

        QueryOptions queryOptions = new QueryOptions(MULTI, true);
        logger.debug("FillGaps find : {}", query);
        logger.debug("FillGaps update : {}", update);
        return variantsCollection.update(query, update, queryOptions);
    }

    private DocumentToVariantConverter getDocumentToVariantConverter(Query query, QueryOptions options) {
        List<Integer> returnedStudies = utils.getReturnedStudies(query, options);
        DocumentToSamplesConverter samplesConverter;
        samplesConverter = new DocumentToSamplesConverter(studyConfigurationManager);
        // Fetch some StudyConfigurations that will be needed
        if (returnedStudies != null) {
            for (Integer studyId : returnedStudies) {
                QueryResult<StudyConfiguration> queryResult = studyConfigurationManager.getStudyConfiguration(studyId, options);
                if (queryResult.getResult().isEmpty()) {
                    throw VariantQueryException.studyNotFound(studyId);
//                    throw new IllegalArgumentException("Couldn't find studyConfiguration for StudyId '" + studyId + "'");
                } else {
                    samplesConverter.addStudyConfiguration(queryResult.first());
                }
            }
        }
        if (query.containsKey(VariantQueryParams.UNKNOWN_GENOTYPE.key())) {
            samplesConverter.setReturnedUnknownGenotype(query.getString(VariantQueryParams.UNKNOWN_GENOTYPE.key()));
        }

        Set<VariantField> fields = VariantField.getReturnedFields(options);
        samplesConverter.setReturnedSamples(getReturnedSamplesList(query, fields));

        DocumentToStudyVariantEntryConverter studyEntryConverter;
        Collection<Integer> returnedFiles = utils.getReturnedFiles(query, fields);

        studyEntryConverter = new DocumentToStudyVariantEntryConverter(false, returnedFiles, samplesConverter);
        studyEntryConverter.setStudyConfigurationManager(studyConfigurationManager);
        return new DocumentToVariantConverter(studyEntryConverter,
                new DocumentToVariantStatsConverter(studyConfigurationManager), returnedStudies);
    }

    private QueryBuilder addQueryStringFilter(String key, String value, final QueryBuilder builder, QueryOperation op) {
        return this.addQueryFilter(key, value, builder, op, Function.identity());
    }

    private QueryBuilder addQueryIntegerFilter(String key, String value, final QueryBuilder builder, QueryOperation op) {
        return this.<Integer>addQueryFilter(key, value, builder, op, elem -> {
            try {
                return Integer.parseInt(elem);
            } catch (NumberFormatException e) {
                throw new VariantQueryException("Unable to parse int " + elem, e);
            }
        });
    }

    private <T> QueryBuilder addQueryFilter(String key, Collection<?> value, final QueryBuilder builder, QueryOperation op,
                                            Function<String, T> map) {
        return addQueryFilter(key, value.stream().map(Object::toString).collect(Collectors.joining(AND)), builder, op, map);
    }

    private <T> QueryBuilder addQueryFilter(String key, String value, final QueryBuilder builder, QueryOperation op,
                                            Function<String, T> map) {
        QueryOperation operation = checkOperator(value);
        QueryBuilder auxBuilder;
        if (op == QueryOperation.OR) {
            auxBuilder = QueryBuilder.start();
        } else {
            auxBuilder = builder;
        }

        if (operation == null) {
            if (value.startsWith("!")) {
                T mapped = map.apply(value.substring(1));
                if (mapped instanceof Collection) {
                    auxBuilder.and(key).notIn(mapped);
                } else {
                    auxBuilder.and(key).notEquals(mapped);
                }
            } else {
                T mapped = map.apply(value);
                if (mapped instanceof Collection) {
                    auxBuilder.and(key).in(mapped);
                } else {
                    auxBuilder.and(key).is(mapped);
                }
            }
        } else if (operation == QueryOperation.OR) {
            String[] array = value.split(OR);
            List list = new ArrayList(array.length);
            for (String elem : array) {
                if (elem.startsWith("!")) {
                    throw new VariantQueryException("Unable to use negate (!) operator in OR sequences (<it_1>(,<it_n>)*)");
                } else {
                    T mapped = map.apply(elem);
                    if (mapped instanceof Collection) {
                        list.addAll(((Collection) mapped));
                    } else {
                        list.add(mapped);
                    }
                }
            }
            auxBuilder.and(key).in(list);
        } else {
            //Split in two lists: positive and negative
            String[] array = value.split(AND);
            List listIs = new ArrayList(array.length);
            List listNotIs = new ArrayList(array.length);

            for (String elem : array) {
                if (elem.startsWith("!")) {
                    T mapped = map.apply(elem.substring(1));
                    if (mapped instanceof Collection) {
                        listNotIs.addAll(((Collection) mapped));
                    } else {
                        listNotIs.add(mapped);
                    }
                } else {
                    T mapped = map.apply(elem);
                    if (mapped instanceof Collection) {
                        listIs.addAll(((Collection) mapped));
                    } else {
                        listIs.add(mapped);
                    }
                }
            }

            if (!listIs.isEmpty()) {    //Can not use method "is" because it will be overwritten with the "notEquals" or "notIn" method
                auxBuilder.and(key).all(listIs);
            }
            if (listNotIs.size() == 1) {
                auxBuilder.and(key).notEquals(listNotIs.get(0));
            } else if (listNotIs.size() > 1) {
                auxBuilder.and(key).notIn(listNotIs);
            }

        }

        if (op == QueryOperation.OR) {
            builder.or(auxBuilder.get());
        }
        return builder;
    }

    /**
     * Accept a list of comparative filters separated with "," or ";" with the expression:
     * {OPERATION}{VALUE}, where the accepted operations are: <, <=, >, >=, =, ==, !=, ~=.
     *
     * @param key
     * @param value
     * @param builder
     * @param extendKey
     * @return
     */
    private QueryBuilder addCompListQueryFilter(String key, String value, QueryBuilder builder, boolean extendKey) {
        QueryOperation op = checkOperator(value);
        List<String> values = splitValue(value, op);

        QueryBuilder compBuilder;
        if (op == QueryOperation.OR) {
            compBuilder = QueryBuilder.start();
        } else {
            compBuilder = builder;
        }

        for (String elem : values) {
            addCompQueryFilter(key, elem, compBuilder, extendKey);
        }

        if (op == QueryOperation.OR) {
            builder.or(compBuilder.get());
        }
        return builder;
    }

    private QueryBuilder addCompQueryFilter(String key, String value, QueryBuilder builder, boolean extendKey) {
        String[] strings = splitKeyOpValue(value);
        String op = "";
        if (strings.length == 3) {
            if (extendKey && !strings[0].isEmpty()) {
                key = key + "." + strings[0];
            }
            value = strings[2];
            op = strings[1];
        }
        return addCompQueryFilter(key, value, builder, op);
    }

    private QueryBuilder addCompQueryFilter(String key, String obj, QueryBuilder builder, String op) {

        switch (op) {
            case "<":
                builder.and(key).lessThan(Double.parseDouble(obj));
                break;
            case "<=":
                builder.and(key).lessThanEquals(Double.parseDouble(obj));
                break;
            case ">":
                builder.and(key).greaterThan(Double.parseDouble(obj));
                break;
            case ">=":
                builder.and(key).greaterThanEquals(Double.parseDouble(obj));
                break;
            case "=":
            case "==":
                try {
                    builder.and(key).is(Double.parseDouble(obj));
                } catch (NumberFormatException e) {
                    builder.and(key).is(obj);
                }
                break;
            case "!=":
                builder.and(key).notEquals(Double.parseDouble(obj));
                break;
            case "~=":
            case "~":
                builder.and(key).regex(Pattern.compile(obj));
                break;
            default:
                break;
        }
        return builder;
    }

    private QueryBuilder addStringCompQueryFilter(String key, String value, QueryBuilder builder) {
        String op = getOperator(value);
        String obj = value.replaceFirst(op, "");

        switch (op) {
            case "!=":
            case "!":
                builder.and(key).notEquals(obj);
                break;
            case "~=":
            case "~":
                builder.and(key).regex(Pattern.compile(obj));
                break;
            case "":
            case "=":
            case "==":
            default:
                builder.and(key).is(obj);
                break;
        }
        return builder;
    }

    private String getOperator(String value) {
        Matcher matcher = OPERATION_PATTERN.matcher(value);
        if (!matcher.find()) {
            return "";
        } else {
            return matcher.group(2);
        }
    }

    /**
     * Accepts a list of filters separated with "," or ";" with the expression: {SCORE}{OPERATION}{VALUE}.
     *
     * @param value        Value to parse
     * @param builder      QueryBuilder
     * @param scoreParam Score query param
     * @param allowDescriptionFilter Use string values as filters for the score description
     * @return QueryBuilder
     */
    private QueryBuilder addScoreFilter(String value, QueryBuilder builder, VariantQueryParams scoreParam,
                                        boolean allowDescriptionFilter) {
        return addScoreFilter(value, builder, scoreParam, null, allowDescriptionFilter);
    }

    /**
     * Accepts a list of filters separated with "," or ";" with the expression: {SOURCE}{OPERATION}{VALUE}.
     *
     * @param value         Value to parse
     * @param builder       QueryBuilder
     * @param scoreParam    Score VariantQueryParam
     * @param defaultSource Default source value. If null, must be present in the filter. If not, must not be present.
     * @param allowDescriptionFilter Use string values as filters for the score description
     * @return QueryBuilder
     */
    private QueryBuilder addScoreFilter(String value, QueryBuilder builder, VariantQueryParams scoreParam, final String defaultSource,
                                        boolean allowDescriptionFilter) {
        final List<String> list;
        QueryOperation operation = checkOperator(value);
        list = splitValue(value, operation);
        List<DBObject> dbObjects = new ArrayList<>();
        for (String elem : list) {
            String[] score = VariantDBAdaptorUtils.splitOperator(elem);
            String source;
            String op;
            String scoreValue;
            // No given score
            if (StringUtils.isEmpty(score[0])) {
                if (defaultSource == null) {
                    logger.error("Bad score filter: " + elem);
                    throw VariantQueryException.malformedParam(scoreParam, value);
                }
                source = defaultSource;
                op = score[1];
                scoreValue = score[2];
            } else {
                if (defaultSource != null) {
                    logger.error("Bad score filter: " + elem);
                    throw VariantQueryException.malformedParam(scoreParam, value);
                }
                source = score[0];
                op = score[1];
                scoreValue = score[2];
            }

            String key = DocumentToVariantAnnotationConverter.SCORE_FIELD_MAP.get(source);
            if (key == null) {
                // Unknown score
                throw VariantQueryException.malformedParam(scoreParam, value);
            }

            QueryBuilder scoreBuilder = new QueryBuilder();
            if (NumberUtils.isParsable(scoreValue)) {
                // Query by score
                key += '.' + DocumentToVariantAnnotationConverter.SCORE_SCORE_FIELD;
                addCompQueryFilter(key, scoreValue, scoreBuilder, op);
            } else if (allowDescriptionFilter) {
                // Query by description
                key += '.' + DocumentToVariantAnnotationConverter.SCORE_DESCRIPTION_FIELD;
                addStringCompQueryFilter(key, scoreValue, scoreBuilder);
            } else {
                throw VariantQueryException.malformedParam(scoreParam, value);
            }
            dbObjects.add(scoreBuilder.get());
        }

        if (!dbObjects.isEmpty()) {
            if (operation == null || operation == QueryOperation.AND) {
                builder.and(dbObjects.toArray(new DBObject[dbObjects.size()]));
            } else {
                builder.and(new BasicDBObject("$or", dbObjects));
            }
        }
        return builder;
    }

    /**
     * Accepts a list of filters separated with "," or ";" with the expression:
     * {STUDY}:{POPULATION}{OPERATION}{VALUE}.
     *
     * @param key                  PopulationFrequency schema field
     * @param alleleFrequencyField Allele frequency schema field
     * @param value                Value to parse
     * @param builder              QueryBuilder
     * @param queryParam           QueryParam filter
     * @return QueryBuilder
     */
    private QueryBuilder addFrequencyFilter(String key, String alleleFrequencyField, String value, QueryBuilder builder,
                                            VariantQueryParams queryParam) {
        return addFrequencyFilter(key, value, builder, queryParam, (v, qb) -> addCompQueryFilter(alleleFrequencyField, v, qb, false));
    }

    /**
     * Accepts a list of filters separated with "," or ";" with the expression:
     * {STUDY}:{POPULATION}{OPERATION}{VALUE}.
     *
     * @param key       PopulationFrequency schema field
     * @param value     Value to parse
     * @param builder   QueryBuilder
     * @param addFilter For complex filter
     * @return QueryBuilder
     */
    private QueryBuilder addFrequencyFilter(String key, String value, QueryBuilder builder, VariantQueryParams queryParam,
                                            BiConsumer<String, QueryBuilder> addFilter) {
        final List<String> list;
        QueryOperation operation = checkOperator(value);
        list = splitValue(value, operation);

        List<BasicDBObject> dbObjects = new ArrayList<>();
        for (String elem : list) {
            String[] split = elem.split(IS);
            if (split.length != 2) {
                logger.error("Bad population frequency filter: " + elem);
                throw VariantQueryException.malformedParam(queryParam, value);
                //new IllegalArgumentException("Bad population frequency filter: " + elem);
            }
            String study = split[0];
            String population = split[1];
            String[] populationFrequency = splitKeyValue(population);
            logger.debug("populationFrequency = " + Arrays.toString(populationFrequency));

            QueryBuilder frequencyBuilder = new QueryBuilder();
            frequencyBuilder.and(DocumentToVariantAnnotationConverter.POPULATION_FREQUENCY_STUDY_FIELD).is(study);
            frequencyBuilder.and(DocumentToVariantAnnotationConverter.POPULATION_FREQUENCY_POP_FIELD).is(populationFrequency[0]);
            Document studyPopFilter = new Document(frequencyBuilder.get().toMap());
            addFilter.accept(populationFrequency[1], frequencyBuilder);
            BasicDBObject elemMatch = new BasicDBObject(key, new BasicDBObject("$elemMatch", frequencyBuilder.get()));
            if (populationFrequency[1].startsWith("<")) {
                BasicDBObject orNotExistsAnyPopulation = new BasicDBObject(key, new BasicDBObject("$exists", false));
                BasicDBObject orNotExistsPopulation =
                        new BasicDBObject(key, new BasicDBObject("$not", new BasicDBObject("$elemMatch", studyPopFilter)));
                dbObjects.add(new BasicDBObject("$or", Arrays.asList(orNotExistsAnyPopulation, orNotExistsPopulation, elemMatch)));
            } else {
                dbObjects.add(elemMatch);
            }
        }
        if (!dbObjects.isEmpty()) {
            if (operation == null || operation == QueryOperation.AND) {
                builder.and(dbObjects.toArray(new BasicDBObject[dbObjects.size()]));
            } else {
                builder.and(new BasicDBObject("$or", dbObjects));
            }
        }
        return builder;
    }

    /**
     * Accept filters separated with "," or ";" with the expression:
     * [{STUDY}:]{COHORT}{OPERATION}{VALUE}.
     * Where STUDY is optional if defaultStudyConfiguration is provided
     *
     * @param key                       Stats field to filter
     * @param values                    Values to parse
     * @param builder                   QueryBuilder
     * @param defaultStudyConfiguration
     */
    private void addStatsFilterList(String key, String values, QueryBuilder builder, StudyConfiguration defaultStudyConfiguration) {
        QueryOperation op = checkOperator(values);
        List<String> valuesList = splitValue(values, op);
        List<DBObject> statsQueries = new LinkedList<>();
        for (String value : valuesList) {
            statsQueries.add(addStatsFilter(key, value, new QueryBuilder(), defaultStudyConfiguration).get());
        }

        if (!statsQueries.isEmpty()) {
            if (op == QueryOperation.OR) {
                builder.or(statsQueries.toArray(new DBObject[statsQueries.size()]));
            } else {
                builder.and(statsQueries.toArray(new DBObject[statsQueries.size()]));
            }
        }
    }

    /**
     * Accepts filters with the expresion: [{STUDY}:]{COHORT}{OPERATION}{VALUE}.
     * Where STUDY is optional if defaultStudyConfiguration is provided
     *
     * @param key                       Stats field to filter
     * @param filter                    Filter to parse
     * @param builder                   QueryBuilder
     * @param defaultStudyConfiguration
     */
    private QueryBuilder addStatsFilter(String key, String filter, QueryBuilder builder, StudyConfiguration defaultStudyConfiguration) {
        if (filter.contains(":") || defaultStudyConfiguration != null) {
            Integer studyId;
            Integer cohortId;
            String operator;
            String valueStr;
            if (filter.contains(":")) {
                String[] studyValue = filter.split(":");
                String[] cohortOpValue = VariantDBAdaptorUtils.splitOperator(studyValue[1]);
                String study = studyValue[0];
                String cohort = cohortOpValue[0];
                operator = cohortOpValue[1];
                valueStr = cohortOpValue[2];

                StudyConfiguration studyConfiguration = utils.getStudyConfiguration(study, defaultStudyConfiguration);
                cohortId = utils.getCohortId(cohort, studyConfiguration);
                studyId = studyConfiguration.getStudyId();
            } else {
//                String study = defaultStudyConfiguration.getStudyName();
                studyId = defaultStudyConfiguration.getStudyId();
                String[] cohortOpValue = VariantDBAdaptorUtils.splitOperator(filter);
                String cohort = cohortOpValue[0];
                cohortId = utils.getCohortId(cohort, defaultStudyConfiguration);
                operator = cohortOpValue[1];
                valueStr = cohortOpValue[2];
            }

            QueryBuilder statsBuilder = new QueryBuilder();
            statsBuilder.and(DocumentToVariantStatsConverter.STUDY_ID).is(studyId);
            if (cohortId != null) {
                statsBuilder.and(DocumentToVariantStatsConverter.COHORT_ID).is(cohortId);
            }
            addCompQueryFilter(key, valueStr, statsBuilder, operator);
            builder.and(DocumentToVariantConverter.STATS_FIELD).elemMatch(statsBuilder.get());
        } else {
            addCompQueryFilter(DocumentToVariantConverter.STATS_FIELD + "." + key, filter, builder, false);
        }
        return builder;
    }

    private QueryBuilder getRegionFilter(Region region, QueryBuilder builder) {
        List<String> chunkIds = getChunkIds(region);
        builder.and(DocumentToVariantConverter.AT_FIELD + '.' + DocumentToVariantConverter.CHUNK_IDS_FIELD).in(chunkIds);
        builder.and(DocumentToVariantConverter.END_FIELD).greaterThanEquals(region.getStart());
        builder.and(DocumentToVariantConverter.START_FIELD).lessThanEquals(region.getEnd());
        return builder;
    }

    private QueryBuilder getRegionFilter(List<Region> regions, QueryBuilder builder) {
        if (regions != null && !regions.isEmpty()) {
            DBObject[] objects = new DBObject[regions.size()];
            int i = 0;
            for (Region region : regions) {
                DBObject regionObject = new BasicDBObject();
//                if (region.getEnd() - region.getStart() < 1000000) {
//                    List<String> chunkIds = getChunkIds(region);
//                    regionObject.put(DocumentToVariantConverter.AT_FIELD + '.' + DocumentToVariantConverter
//                            .CHUNK_IDS_FIELD,
//                            new Document("$in", chunkIds));
//                } else {
//                    regionObject.put(DocumentToVariantConverter.CHROMOSOME_FIELD, region.getChromosome());
//                }

                int end = region.getEnd();
                if (end < Integer.MAX_VALUE) { // Avoid overflow
                    end++;
                }
                regionObject.put("_id", new Document()
                        .append("$gte", VariantStringIdConverter.buildId(region.getChromosome(), region.getStart()))
                        .append("$lt", VariantStringIdConverter.buildId(region.getChromosome(), end)));

                objects[i] = regionObject;
                i++;
            }
            builder.or(objects);
        }
        return builder;
    }

    /* Query util methods */

    /**
     * Parses the string to integer number.
     * <p>
     * Returns null if the string was not an integer.
     */
    private Integer parseInteger(String string) {
        Integer integer;
        try {
            integer = Integer.parseInt(string);
        } catch (NumberFormatException ignored) {
            integer = null;
        }
        return integer;
    }

    public void createIndexes(QueryOptions options) {
        createIndexes(options, variantsCollection);
    }

    /**
     * Create missing indexes on the given VariantsCollection.
     * Variant indices
     * - ChunkID
     * - Chromosome + start + end
     * - IDs
     * <p>
     * Study indices
     * - StudyId + FileId
     * <p>
     * Stats indices
     * - StatsMaf
     * - StatsMgf
     * <p>
     * Annotation indices
     * - XRef.id
     * - ConsequenceType.so
     * - _gn_so : SPARSE
     * - PopulationFrequency Study + Population + AlternateFrequency : SPARSE
     * - Clinical.Clinvar.clinicalSignificance  : SPARSE
     * ConservedRegionScore
     * - phastCons.score
     * - phylop.score
     * - gerp.score
     * FunctionalScore
     * - cadd_scaled
     * - cadd_raw
     * - Drugs.name  : SPARSE
     * ProteinSubstitution
     * - polyphen.score : SPARSE
     * - polyphen.description : SPARSE
     * - sift.score : SPARSE
     * - sift.description : SPARSE
     * - ProteinVariantAnnotation.keywords : SPARSE
     * - TranscriptAnnotationFlags : SPARSE
     *
     * @param options            Unused Options.
     * @param variantsCollection MongoDBCollection
     */
    public static void createIndexes(QueryOptions options, MongoDBCollection variantsCollection) {
        logger.info("Start creating indexes");
        ObjectMap onBackground = new ObjectMap(MongoDBCollection.BACKGROUND, true);
        ObjectMap onBackgroundSparse = new ObjectMap(MongoDBCollection.BACKGROUND, true).append(MongoDBCollection.SPARSE, true);

        // Variant indices
        ////////////////
        variantsCollection.createIndex(new Document(DocumentToVariantConverter.AT_FIELD + '.'
                + DocumentToVariantConverter.CHUNK_IDS_FIELD, 1), onBackground);
        variantsCollection.createIndex(new Document(DocumentToVariantConverter.CHROMOSOME_FIELD, 1)
                .append(DocumentToVariantConverter.START_FIELD, 1)
                .append(DocumentToVariantConverter.END_FIELD, 1), onBackground);
        variantsCollection.createIndex(new Document(DocumentToVariantConverter.IDS_FIELD, 1), onBackground);

        // Study indices
        ////////////////
        variantsCollection.createIndex(new Document(DocumentToVariantConverter.STUDIES_FIELD
                + "." + DocumentToStudyVariantEntryConverter.STUDYID_FIELD, 1)
                .append(DocumentToVariantConverter.STUDIES_FIELD
                        + "." + DocumentToStudyVariantEntryConverter.FILES_FIELD
                        + "." + DocumentToStudyVariantEntryConverter.FILEID_FIELD, 1), onBackground);
        // Stats indices
        ////////////////
        variantsCollection.createIndex(new Document(DocumentToVariantConverter.STATS_FIELD + "." + DocumentToVariantStatsConverter
                .MAF_FIELD, 1), onBackground);
        variantsCollection.createIndex(new Document(DocumentToVariantConverter.STATS_FIELD + "." + DocumentToVariantStatsConverter
                .MGF_FIELD, 1), onBackground);

        // Annotation indices
        ////////////////

        // XRefs.id
        variantsCollection.createIndex(new Document()
                        .append(DocumentToVariantConverter.ANNOTATION_FIELD
                                + "." + DocumentToVariantAnnotationConverter.XREFS_FIELD
                                + "." + DocumentToVariantAnnotationConverter.XREF_ID_FIELD, 1),
                onBackground);
        // ConsequenceType.so
        variantsCollection.createIndex(new Document()
                        .append(DocumentToVariantConverter.ANNOTATION_FIELD
                                + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                                + "." + DocumentToVariantAnnotationConverter.CT_SO_ACCESSION_FIELD, 1),
                onBackground);
        // _gn_so : SPARSE
        variantsCollection.createIndex(new Document()
                        .append(DocumentToVariantConverter.ANNOTATION_FIELD
                                + "." + DocumentToVariantAnnotationConverter.GENE_SO_FIELD, 1),
                onBackgroundSparse);
        // Population frequency : SPARSE
        variantsCollection.createIndex(new Document()
                        .append(DocumentToVariantConverter.ANNOTATION_FIELD
                                + "." + DocumentToVariantAnnotationConverter.POPULATION_FREQUENCIES_FIELD
                                + "." + DocumentToVariantAnnotationConverter.POPULATION_FREQUENCY_STUDY_FIELD, 1)
                        .append(DocumentToVariantConverter.ANNOTATION_FIELD
                                + "." + DocumentToVariantAnnotationConverter.POPULATION_FREQUENCIES_FIELD
                                + "." + DocumentToVariantAnnotationConverter.POPULATION_FREQUENCY_POP_FIELD, 1)
                        .append(DocumentToVariantConverter.ANNOTATION_FIELD
                                + "." + DocumentToVariantAnnotationConverter.POPULATION_FREQUENCIES_FIELD
                                + "." + DocumentToVariantAnnotationConverter.POPULATION_FREQUENCY_ALTERNATE_FREQUENCY_FIELD, 1),
                onBackgroundSparse);
        // Clinical clinvar : SPARSE
        variantsCollection.createIndex(new Document()
                        .append(DocumentToVariantConverter.ANNOTATION_FIELD
                                + "." + DocumentToVariantAnnotationConverter.CLINICAL_DATA_FIELD
                                + "." + DocumentToVariantAnnotationConverter.CLINICAL_CLINVAR_FIELD
                                + ".clinicalSignificance", 1),
                onBackgroundSparse);

        // Conserved region score (phastCons, phylop, gerp)
        variantsCollection.createIndex(new Document(DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSERVED_REGION_GERP_FIELD
                        + "." + DocumentToVariantAnnotationConverter.SCORE_SCORE_FIELD, 1),
                onBackground);
        variantsCollection.createIndex(new Document(DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSERVED_REGION_PHYLOP_FIELD
                        + "." + DocumentToVariantAnnotationConverter.SCORE_SCORE_FIELD, 1),
                onBackground);
        variantsCollection.createIndex(new Document(DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSERVED_REGION_PHASTCONS_FIELD
                        + "." + DocumentToVariantAnnotationConverter.SCORE_SCORE_FIELD, 1),
                onBackground);

        // Functional score (cadd_scaled, cadd_raw)
        variantsCollection.createIndex(new Document(DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.FUNCTIONAL_CADD_SCALED_FIELD
                        + "." + DocumentToVariantAnnotationConverter.SCORE_SCORE_FIELD, 1),
                onBackground);
        variantsCollection.createIndex(new Document(DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.FUNCTIONAL_CADD_RAW_FIELD
                        + "." + DocumentToVariantAnnotationConverter.SCORE_SCORE_FIELD, 1),
                onBackground);

        // Drugs : SPARSE
        variantsCollection.createIndex(new Document()
                        .append(DocumentToVariantConverter.ANNOTATION_FIELD
                                + "." + DocumentToVariantAnnotationConverter.DRUG_FIELD
                                + "." + DocumentToVariantAnnotationConverter.DRUG_NAME_FIELD, 1),
                onBackgroundSparse);
        // Protein substitution score (polyphen , sift) : SPARSE
        variantsCollection.createIndex(new Document(DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CT_PROTEIN_POLYPHEN_FIELD
                        + "." + DocumentToVariantAnnotationConverter.SCORE_SCORE_FIELD, 1),
                onBackgroundSparse);
        variantsCollection.createIndex(new Document(DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CT_PROTEIN_SIFT_FIELD
                        + "." + DocumentToVariantAnnotationConverter.SCORE_SCORE_FIELD, 1),
                onBackgroundSparse);

        // Protein substitution score description (polyphen , sift) : SPARSE
        variantsCollection.createIndex(new Document(DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CT_PROTEIN_POLYPHEN_FIELD
                        + "." + DocumentToVariantAnnotationConverter.SCORE_DESCRIPTION_FIELD, 1),
                onBackgroundSparse);
        variantsCollection.createIndex(new Document(DocumentToVariantConverter.ANNOTATION_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                        + "." + DocumentToVariantAnnotationConverter.CT_PROTEIN_SIFT_FIELD
                        + "." + DocumentToVariantAnnotationConverter.SCORE_DESCRIPTION_FIELD, 1),
                onBackgroundSparse);

        // Protein Keywords : SPARSE
        variantsCollection.createIndex(new Document()
                        .append(DocumentToVariantConverter.ANNOTATION_FIELD
                                + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                                + "." + DocumentToVariantAnnotationConverter.CT_PROTEIN_KEYWORDS, 1),
                onBackgroundSparse);
        // TranscriptAnnotationFlags : SPARSE
        variantsCollection.createIndex(new Document()
                        .append(DocumentToVariantConverter.ANNOTATION_FIELD
                                + "." + DocumentToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                                + "." + DocumentToVariantAnnotationConverter.CT_TRANSCRIPT_ANNOT_FLAGS, 1),
                onBackgroundSparse);

        logger.debug("sent order to create indices");
    }

    /**
     * This method split a typical key-value param such as 'sift<=0.2' in an array ["sift", "<=0.2"].
     * This implementation can and probably should be improved.
     *
     * @param keyValue The keyvalue parameter to be split
     * @return An array with 2 positions for the key and value
     * @deprecated use {@link VariantDBAdaptorUtils#splitOperator(String)}
     */
    @Deprecated
    private String[] splitKeyValue(String keyValue) {
        Matcher matcher = OPERATION_PATTERN.matcher(keyValue);
        if (!matcher.find()) {
            return new String[]{keyValue};
        } else {
            return new String[]{matcher.group(1), matcher.group(2) + matcher.group(3)};
        }
    }

    /**
     * @deprecated use {@link VariantDBAdaptorUtils#splitOperator(String)}
     */
    @Deprecated
    private String[] splitKeyOpValue(String keyValue) {
        Matcher matcher = OPERATION_PATTERN.matcher(keyValue);
        if (!matcher.find()) {
            return new String[]{keyValue};
        } else {
            return new String[]{matcher.group(1), matcher.group(2), matcher.group(3)};
        }
    }

    /* *******************
     * Auxiliary methods *
     * *******************/
    private List<String> getChunkIds(Region region) {
        List<String> chunkIds = new LinkedList<>();

        int chunkSize = (region.getEnd() - region.getStart() > VariantMongoDBWriter.CHUNK_SIZE_BIG)
                ? VariantMongoDBWriter.CHUNK_SIZE_BIG
                : VariantMongoDBWriter.CHUNK_SIZE_SMALL;
        int ks = chunkSize / 1000;
        int chunkStart = region.getStart() / chunkSize;
        int chunkEnd = region.getEnd() / chunkSize;

        for (int i = chunkStart; i <= chunkEnd; i++) {
            String chunkId = region.getChromosome() + "_" + i + "_" + ks + "k";
            chunkIds.add(chunkId);
        }
        return chunkIds;
    }

    private int getChunkId(int position, int chunksize) {
        return position / chunksize;
    }

    private int getChunkStart(int id, int chunksize) {
        return (id == 0) ? 1 : id * chunksize;
    }

    private int getChunkEnd(int id, int chunksize) {
        return (id * chunksize) + chunksize - 1;
    }

    @Override
    public StudyConfigurationManager getStudyConfigurationManager() {
        return studyConfigurationManager;
    }

    @Override
    public VariantSourceMongoDBAdaptor getVariantSourceDBAdaptor() {
        return variantSourceMongoDBAdaptor;
    }

    @Override
    public void setStudyConfigurationManager(StudyConfigurationManager studyConfigurationManager) {
        this.studyConfigurationManager = studyConfigurationManager;
    }

    @Override
    public CellBaseClient getCellBaseClient() {
        return cellBaseClient;
    }

    @Override
    public VariantDBAdaptorUtils getDBAdaptorUtils() {
        return utils;
    }

    public static List<Integer> getLoadedSamples(int fileId, StudyConfiguration studyConfiguration) {
        List<Integer> loadedSampleIds = new LinkedList<>();
        for (Integer indexedFile : studyConfiguration.getIndexedFiles()) {
            if (indexedFile.equals(fileId)) {
                continue;
            } else {
                loadedSampleIds.addAll(studyConfiguration.getSamplesInFiles().get(indexedFile));
            }
        }
        loadedSampleIds.removeAll(studyConfiguration.getSamplesInFiles().get(fileId));
        return loadedSampleIds;
    }

    public VariantMongoDBAdaptor setVariantSearchManager(VariantSearchManager variantSearchManager) {
        this.variantSearchManager = variantSearchManager;
        return this;
    }
}
