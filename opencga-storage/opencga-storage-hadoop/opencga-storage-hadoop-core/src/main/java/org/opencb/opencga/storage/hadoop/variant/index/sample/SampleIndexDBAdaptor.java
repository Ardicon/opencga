package org.opencb.opencga.storage.hadoop.variant.index.sample;

import com.google.common.collect.Iterators;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.CollectionUtils;
import org.opencb.biodata.models.core.Region;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.storage.core.metadata.VariantStorageMetadataManager;
import org.opencb.opencga.storage.core.variant.VariantStorageEngine;
import org.opencb.opencga.storage.core.variant.adaptors.GenotypeClass;
import org.opencb.opencga.storage.core.variant.adaptors.VariantIterable;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryException;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryUtils.QueryOperation;
import org.opencb.opencga.storage.core.variant.adaptors.iterators.IntersectMultiVariantKeyIterator;
import org.opencb.opencga.storage.core.variant.adaptors.iterators.UnionMultiVariantKeyIterator;
import org.opencb.opencga.storage.core.variant.adaptors.iterators.VariantDBIterator;
import org.opencb.opencga.storage.hadoop.utils.HBaseManager;
import org.opencb.opencga.storage.hadoop.variant.GenomeHelper;
import org.opencb.opencga.storage.hadoop.variant.index.IndexUtils;
import org.opencb.opencga.storage.hadoop.variant.index.query.SampleAnnotationIndexQuery.PopulationFrequencyQuery;
import org.opencb.opencga.storage.hadoop.variant.index.query.SampleIndexQuery;
import org.opencb.opencga.storage.hadoop.variant.index.query.SampleIndexQuery.SingleSampleIndexQuery;
import org.opencb.opencga.storage.hadoop.variant.utils.HBaseVariantTableNameGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.opencb.opencga.storage.hadoop.variant.adaptors.phoenix.VariantSqlQueryParser.DEFAULT_LOADED_GENOTYPES;
import static org.opencb.opencga.storage.hadoop.variant.index.IndexUtils.EMPTY_MASK;

/**
 * Created on 14/05/18.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class SampleIndexDBAdaptor implements VariantIterable {

    private final HBaseManager hBaseManager;
    private final HBaseVariantTableNameGenerator tableNameGenerator;
    private final VariantStorageMetadataManager metadataManager;
    private final byte[] family;
    private static Logger logger = LoggerFactory.getLogger(SampleIndexDBAdaptor.class);
    private SampleIndexQueryParser parser;
    private final SampleIndexConfiguration configuration;

    public SampleIndexDBAdaptor(GenomeHelper helper, HBaseManager hBaseManager, HBaseVariantTableNameGenerator tableNameGenerator,
                                VariantStorageMetadataManager metadataManager) {
        this.hBaseManager = hBaseManager;
        this.tableNameGenerator = tableNameGenerator;
        this.metadataManager = metadataManager;
        family = helper.getColumnFamily();
        // TODO: Read configuration from metadata manager
        configuration = SampleIndexConfiguration.defaultConfiguration();
        parser = new SampleIndexQueryParser(metadataManager, configuration);
    }

    @Override
    public VariantDBIterator iterator(Query query, QueryOptions options) {
        return iterator(parser.parse(query));
    }

    public VariantDBIterator iterator(SampleIndexQuery query) {
        return iterator(query, QueryOptions.empty());
    }

    public VariantDBIterator iterator(SampleIndexQuery query, QueryOptions options) {
        String study = query.getStudy();
        Map<String, List<String>> samples = query.getSamplesMap();

        if (samples.isEmpty()) {
            throw new VariantQueryException("At least one sample expected to query SampleIndex!");
        }
        List<String> allGts = getAllLoadedGenotypes(study);
        QueryOperation operation = query.getQueryOperation();

        if (samples.size() == 1) {
            String sample = samples.entrySet().iterator().next().getKey();
            List<String> gts = query.getSamplesMap().get(sample);
            List<String> filteredGts = GenotypeClass.filter(gts, allGts);

            if (!gts.isEmpty() && filteredGts.isEmpty()) {
                // If empty, should find none. Return empty iterator
                return VariantDBIterator.emptyIterator();
            } else {
                logger.info("Single sample indexes iterator");
                SingleSampleIndexVariantDBIterator iterator = internalIterator(query.forSample(sample, filteredGts));
                return applyLimitSkip(iterator, options);
            }
        }

        List<VariantDBIterator> iterators = new ArrayList<>(samples.size());
        List<VariantDBIterator> negatedIterators = new ArrayList<>(samples.size());

        for (Map.Entry<String, List<String>> entry : samples.entrySet()) {
            String sample = entry.getKey();
            List<String> gts = GenotypeClass.filter(entry.getValue(), allGts);
            if (!entry.getValue().isEmpty() && gts.isEmpty()) {
                // If empty, should find none. Add empty iterator for this sample
                iterators.add(VariantDBIterator.emptyIterator());
            } else if (gts.stream().allMatch(SampleIndexSchema::validGenotype)) {
                iterators.add(internalIterator(query.forSample(sample, gts)));
            } else {
                if (operation.equals(QueryOperation.OR)) {
                    throw new IllegalArgumentException("Unable to query by REF or MISS genotypes!");
                }
                List<String> queryGts = new ArrayList<>(allGts);
                queryGts.removeAll(gts);

                // Skip if GTs to query is empty!
                // Otherwise, it will return ALL genotypes instead of none
                if (!queryGts.isEmpty()) {
                    negatedIterators.add(internalIterator(query.forSample(sample, queryGts)));
                }
            }
        }
        VariantDBIterator iterator;
        if (operation.equals(QueryOperation.OR)) {
            logger.info("Union of " + iterators.size() + " sample indexes");
            iterator = new UnionMultiVariantKeyIterator(iterators);
        } else {
            logger.info("Intersection of " + iterators.size() + " sample indexes plus " + negatedIterators.size() + " negated indexes");
            iterator = new IntersectMultiVariantKeyIterator(iterators, negatedIterators);
        }

        return applyLimitSkip(iterator, options);
    }

    protected VariantDBIterator applyLimitSkip(VariantDBIterator iterator, QueryOptions options) {
        int limit = options.getInt(QueryOptions.LIMIT, -1);
        int skip = options.getInt(QueryOptions.SKIP, -1);
        // Client site limit-skip
        if (skip > 0) {
            Iterators.advance(iterator, skip);
        }
        if (limit > 0) {
            Iterator<Variant> it = Iterators.limit(iterator, limit);
            return VariantDBIterator.wrapper(it).addCloseable(iterator);
        } else {
            return iterator;
        }
    }

    /**
     * Partially processed iterator. Internal usage only.
     *
     * @param query SingleSampleIndexQuery
     * @return SingleSampleIndexVariantDBIterator
     */
    private SingleSampleIndexVariantDBIterator internalIterator(SingleSampleIndexQuery query) {
        String tableName = tableNameGenerator.getSampleIndexTableName(toStudyId(query.getStudy()));

        try {
            return hBaseManager.act(tableName, table -> {
                return new SingleSampleIndexVariantDBIterator(table, query, family, this);
            });
        } catch (IOException e) {
            throw VariantQueryException.internalException(e);
        }
    }

    protected Map<String, List<Variant>> queryByGt(int study, int sample, String chromosome, int position) throws IOException {
        String tableName = tableNameGenerator.getSampleIndexTableName(study);

        return hBaseManager.act(tableName, table -> {
            Get get = new Get(SampleIndexSchema.toRowKey(sample, chromosome, position));
            HBaseToSampleIndexConverter converter = new HBaseToSampleIndexConverter();
            try {
                Result result = table.get(get);
                if (result != null) {
                    return converter.convertToMap(result);
                } else {
                    return Collections.emptyMap();
                }
            } catch (IOException e) {
                throw VariantQueryException.internalException(e);
            }
        });
    }

    public Iterator<Map<String, List<Variant>>> iteratorByGt(int study, int sample) throws IOException {
        String tableName = tableNameGenerator.getSampleIndexTableName(study);

        return hBaseManager.act(tableName, table -> {

            Scan scan = new Scan();
            scan.setRowPrefixFilter(SampleIndexSchema.toRowKey(sample));
            HBaseToSampleIndexConverter converter = new HBaseToSampleIndexConverter();
            try {
                ResultScanner scanner = table.getScanner(scan);
                Iterator<Result> resultIterator = scanner.iterator();
                return Iterators.transform(resultIterator, converter::convertToMap);
            } catch (IOException e) {
                throw VariantQueryException.internalException(e);
            }
        });
    }

    public Iterator<SampleIndexEntry> rawIterator(int study, int sample) throws IOException {
        String tableName = tableNameGenerator.getSampleIndexTableName(study);

        return hBaseManager.act(tableName, table -> {

            Scan scan = new Scan();
            scan.setRowPrefixFilter(SampleIndexSchema.toRowKey(sample));
            HBaseToSampleIndexConverter converter = new HBaseToSampleIndexConverter();
            try {
                ResultScanner scanner = table.getScanner(scan);
                Iterator<Result> resultIterator = scanner.iterator();
                return Iterators.transform(resultIterator, converter::convert);
            } catch (IOException e) {
                throw VariantQueryException.internalException(e);
            }
        });
    }

    public boolean isFastCount(SampleIndexQuery query) {
        return query.getSamplesMap().size() == 1 && query.emptyAnnotationIndex() && query.emptyFileIndex();
    }

    public long count(List<Region> regions, String study, String sample, List<String> gts) {
        return count(new SampleIndexQuery(regions, study, Collections.singletonMap(sample, gts), null).forSample(sample));
    }

    public long count(SampleIndexQuery query) {
        if (query.getSamplesMap().size() == 1) {
            String sample = query.getSamplesMap().keySet().iterator().next();
            return count(query.forSample(sample));
        } else {
            return Iterators.size(iterator(query));
        }
    }

    public long count(SingleSampleIndexQuery query) {
        List<Region> regionsList;
        if (CollectionUtils.isEmpty(query.getRegions())) {
            // If no regions are defined, get a list of one null element to initialize the stream.
            regionsList = Collections.singletonList(null);
        } else {
            regionsList = VariantQueryUtils.mergeRegions(query.getRegions());
        }

        String tableName = tableNameGenerator.getSampleIndexTableName(toStudyId(query.getStudy()));

        try {
            return hBaseManager.act(tableName, table -> {
                long count = 0;
                for (Region region : regionsList) {
                    // Split region in countable regions
                    List<Region> subRegions = region == null ? Collections.singletonList((Region) null) : splitRegion(region);
                    for (Region subRegion : subRegions) {
                        HBaseToSampleIndexConverter converter = new HBaseToSampleIndexConverter();
                        boolean noRegionFilter = subRegion == null || startsAtBatch(subRegion) && endsAtBatch(subRegion);
                        boolean simpleCount = CollectionUtils.isEmpty(query.getVariantTypes()) && noRegionFilter;
                        try {
                            if (query.emptyOrRegionFilter() && simpleCount) {
                                // Directly sum counters
                                Scan scan = parseCount(query, null);
                                ResultScanner scanner = table.getScanner(scan);
                                Result result = scanner.next();
                                while (result != null) {
                                    count += converter.convertToCount(result);
                                    result = scanner.next();
                                }
                            } else if (simpleCount) {
                                // Fast filter and count
                                SampleIndexEntryFilter filter = buildSampleIndexEntryFilter(query, subRegion);
                                Scan scan = parseCountAndFilter(query, subRegion);

                                ResultScanner scanner = table.getScanner(scan);
                                Result result = scanner.next();
                                while (result != null) {
                                    SampleIndexEntry sampleIndexEntry = converter.convertCountersOnly(result);
                                    count += filter.filterAndCount(sampleIndexEntry);
                                    result = scanner.next();
                                }
                            } else {
                                // Parse, filter and count
                                SampleIndexEntryFilter filter = buildSampleIndexEntryFilter(query, subRegion);
                                Scan scan = parse(query, subRegion);

                                ResultScanner scanner = table.getScanner(scan);
                                Result result = scanner.next();
                                while (result != null) {
                                    SampleIndexEntry sampleIndexEntry = converter.convert(result);
                                    count += filter.filterAndCount(sampleIndexEntry);
                                    result = scanner.next();
                                }
                            }
                        } catch (IOException e) {
                            throw VariantQueryException.internalException(e);
                        }
                    }
                }
                return count;
            });
        } catch (IOException e) {
            throw VariantQueryException.internalException(e);
        }
    }

    public SampleIndexQueryParser getSampleIndexQueryParser() {
        return parser;
    }

    protected int toStudyId(String study) {
        int studyId;
        if (StringUtils.isEmpty(study)) {
            Map<String, Integer> studies = metadataManager.getStudies(null);
            if (studies.size() == 1) {
                studyId = studies.values().iterator().next();
            } else {
                throw VariantQueryException.studyNotFound(study, studies.keySet());
            }
        } else {
            studyId = metadataManager.getStudyId(study);
        }
        return studyId;
    }

    protected List<String> getAllLoadedGenotypes(String study) {
        List<String> allGts = metadataManager.getStudyMetadata(study)
                .getAttributes()
                .getAsStringList(VariantStorageEngine.Options.LOADED_GENOTYPES.key());
        if (allGts == null || allGts.isEmpty()) {
            allGts = DEFAULT_LOADED_GENOTYPES;
        }
        return allGts;
    }

    /**
     * Split region into regions that match with batches at SampleIndexTable.
     *
     * @param region Region to split
     * @return List of regions.
     */
    protected static List<Region> splitRegion(Region region) {
        List<Region> regions;
        if (region.getEnd() - region.getStart() < SampleIndexSchema.BATCH_SIZE) {
            // Less than one batch. Do not split region
            regions = Collections.singletonList(region);
        } else if (region.getStart() / SampleIndexSchema.BATCH_SIZE + 1 == region.getEnd() / SampleIndexSchema.BATCH_SIZE
                && !startsAtBatch(region)
                && !endsAtBatch(region)) {
            // Consecutive partial batches. Do not split region
            regions = Collections.singletonList(region);
        } else {
            regions = new ArrayList<>(3);
            if (!startsAtBatch(region)) {
                int splitPoint = region.getStart() - region.getStart() % SampleIndexSchema.BATCH_SIZE + SampleIndexSchema.BATCH_SIZE;
                regions.add(new Region(region.getChromosome(), region.getStart(), splitPoint - 1));
                region.setStart(splitPoint);
            }
            regions.add(region);
            if (!endsAtBatch(region)) {
                int splitPoint = region.getEnd() - region.getEnd() % SampleIndexSchema.BATCH_SIZE;
                regions.add(new Region(region.getChromosome(), splitPoint, region.getEnd()));
                region.setEnd(splitPoint - 1);
            }
        }
        return regions;
    }

    protected static boolean startsAtBatch(Region region) {
        return region.getStart() % SampleIndexSchema.BATCH_SIZE == 0;
    }

    protected static boolean endsAtBatch(Region region) {
        return region.getEnd() + 1 % SampleIndexSchema.BATCH_SIZE == 0;
    }

    public SampleIndexEntryFilter buildSampleIndexEntryFilter(SingleSampleIndexQuery query, Region region) {
        return new SampleIndexEntryFilter(query, configuration, region);
    }

    public Scan parse(SingleSampleIndexQuery query, Region region) {
        return parse(query, region, false, false);
    }

    public Scan parseCount(SingleSampleIndexQuery query, Region region) {
        return parse(query, region, true, true);
    }

    public Scan parseCountAndFilter(SingleSampleIndexQuery query, Region region) {
        return parse(query, region, false, true);
    }

    private Scan parse(SingleSampleIndexQuery query, Region region, boolean onlyCount, boolean skipGtColumn) {

        Scan scan = new Scan();
        int studyId = toStudyId(query.getStudy());
        int sampleId = toSampleId(studyId, query.getSample());
        if (region != null) {
            scan.setStartRow(SampleIndexSchema.toRowKey(sampleId, region.getChromosome(), region.getStart()));
            scan.setStopRow(SampleIndexSchema.toRowKey(sampleId, region.getChromosome(),
                    region.getEnd() + (region.getEnd() == Integer.MAX_VALUE ? 0 : SampleIndexSchema.BATCH_SIZE)));
        } else {
            scan.setStartRow(SampleIndexSchema.toRowKey(sampleId));
            scan.setStopRow(SampleIndexSchema.toRowKey(sampleId + 1));
        }
        // If genotypes are not defined, return ALL columns
        for (String gt : query.getGenotypes()) {
            scan.addColumn(family, SampleIndexSchema.toGenotypeCountColumn(gt));
            if (!onlyCount) {
                if (query.getMendelianError()) {
                    scan.addColumn(family, SampleIndexSchema.toMendelianErrorColumn());
                } else {
                    if (!skipGtColumn) {
                        scan.addColumn(family, SampleIndexSchema.toGenotypeColumn(gt));
                    }
                }
                if (query.getAnnotationIndexMask() != EMPTY_MASK) {
                    scan.addColumn(family, SampleIndexSchema.toAnnotationIndexColumn(gt));
                    scan.addColumn(family, SampleIndexSchema.toAnnotationIndexCountColumn(gt));
                }
                if (query.getAnnotationIndexQuery().getBiotypeMask() != EMPTY_MASK) {
                    scan.addColumn(family, SampleIndexSchema.toAnnotationBiotypeIndexColumn(gt));
                }
                if (query.getAnnotationIndexQuery().getConsequenceTypeMask() != EMPTY_MASK) {
                    scan.addColumn(family, SampleIndexSchema.toAnnotationConsequenceTypeIndexColumn(gt));
                }
                if (!query.getAnnotationIndexQuery().getPopulationFrequencyQueries().isEmpty()) {
                    scan.addColumn(family, SampleIndexSchema.toAnnotationPopFreqIndexColumn(gt));
                }
                if (query.getFileIndexMask() != EMPTY_MASK) {
                    scan.addColumn(family, SampleIndexSchema.toFileIndexColumn(gt));
                }
                if (query.hasFatherFilter() || query.hasMotherFilter()) {
                    scan.addColumn(family, SampleIndexSchema.toParentsGTColumn(gt));
                }
            }
        }
        if (query.getMendelianError()) {
            scan.addColumn(family, SampleIndexSchema.toMendelianErrorColumn());
        }
        scan.setCaching(hBaseManager.getConf().getInt("hbase.client.scanner.caching", 100));

        logger.info("StartRow = " + Bytes.toStringBinary(scan.getStartRow()) + " == "
                + SampleIndexSchema.rowKeyToString(scan.getStartRow()));
        logger.info("StopRow = " + Bytes.toStringBinary(scan.getStopRow()) + " == "
                + SampleIndexSchema.rowKeyToString(scan.getStopRow()));
        logger.info("columns = " + scan.getFamilyMap().getOrDefault(family, Collections.emptyNavigableSet())
                .stream().map(Bytes::toString).collect(Collectors.joining(",")));
//        logger.info("MaxResultSize = " + scan.getMaxResultSize());
//        logger.info("Filters = " + scan.getFilter());
//        logger.info("Batch = " + scan.getBatch());
        logger.info("Caching = " + scan.getCaching());
        logger.info("AnnotationIndex = " + IndexUtils.maskToString(query.getAnnotationIndexMask(), query.getAnnotationIndex()));
        if (query.getAnnotationIndexQuery().getBiotypeMask() != EMPTY_MASK) {
            logger.info("BiotypeIndex    = " + IndexUtils.byteToString(query.getAnnotationIndexQuery().getBiotypeMask()));
        }
        if (query.getAnnotationIndexQuery().getConsequenceTypeMask() != EMPTY_MASK) {
            logger.info("CTIndex         = " + IndexUtils.shortToString(query.getAnnotationIndexQuery().getConsequenceTypeMask()));
        }
        for (PopulationFrequencyQuery pf : query.getAnnotationIndexQuery().getPopulationFrequencyQueries()) {
            logger.info("PopFreq         = " + pf);
        }
        boolean[] validFileIndex = query.getSampleFileIndexQuery().getValidFileIndex();
        for (int i = 0; i < validFileIndex.length; i++) {
            if (validFileIndex[i]) {
                logger.info("FileIndex       = " + IndexUtils.maskToString(query.getFileIndexMask(), (byte) i));
            }
        }
        if (query.hasFatherFilter()) {
            logger.info("FatherFilter       = " + IndexUtils.parentFilterToString(query.getFatherFilter()));
        }
        if (query.hasMotherFilter()) {
            logger.info("MotherFilter       = " + IndexUtils.parentFilterToString(query.getMotherFilter()));
        }

//        try {
//            System.out.println("scan = " + scan.toJSON() + " " + rowKeyToString(scan.getStartRow()) + " -> + "
// + rowKeyToString(scan.getStopRow()));
//        } catch (IOException e) {
//            throw VariantQueryException.internalException(e);
//        }

        return scan;
    }

    private int toSampleId(int studyId, String sample) {
        return metadataManager.getSampleId(studyId, sample);
    }


}
