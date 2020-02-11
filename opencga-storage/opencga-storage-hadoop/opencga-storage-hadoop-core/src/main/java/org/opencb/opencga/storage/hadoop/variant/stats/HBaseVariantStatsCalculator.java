package org.opencb.opencga.storage.hadoop.variant.stats;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.hbase.client.Result;
import org.opencb.biodata.models.feature.Genotype;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.AlternateCoordinate;
import org.opencb.biodata.models.variant.metadata.Aggregation;
import org.opencb.biodata.models.variant.stats.VariantStats;
import org.opencb.biodata.tools.variant.merge.VariantAlternateRearranger;
import org.opencb.biodata.tools.variant.stats.AggregationUtils;
import org.opencb.biodata.tools.variant.stats.VariantStatsCalculator;
import org.opencb.commons.run.Task;
import org.opencb.opencga.storage.core.metadata.VariantStorageMetadataManager;
import org.opencb.opencga.storage.core.metadata.models.StudyMetadata;
import org.opencb.opencga.storage.core.variant.adaptors.GenotypeClass;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryFields;
import org.opencb.opencga.storage.hadoop.variant.GenomeHelper;
import org.opencb.opencga.storage.hadoop.variant.converters.AbstractPhoenixConverter;
import org.opencb.opencga.storage.hadoop.variant.converters.VariantRow;
import org.opencb.opencga.storage.hadoop.variant.converters.study.HBaseToStudyEntryConverter;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.opencb.biodata.models.feature.Genotype.HOM_REF;

/**
 * Created on 13/03/18.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class HBaseVariantStatsCalculator extends AbstractPhoenixConverter implements Task<VariantRow, VariantStats> {

    private final List<Integer> sampleIds;
    private final StudyMetadata sm;
    private final boolean statsMultiAllelic;
    //    private List<byte[]> rows;
    private HBaseToGenotypeCountConverter converter;

    public HBaseVariantStatsCalculator(VariantStorageMetadataManager metadataManager, StudyMetadata sm,
                                       List<Integer> sampleIds, boolean statsMultiAllelic, String unknownGenotype) {
        super(GenomeHelper.COLUMN_FAMILY_BYTES);
        this.sm = sm;
        this.sampleIds = sampleIds;
        this.statsMultiAllelic = statsMultiAllelic;
        converter = new HBaseToGenotypeCountConverter(metadataManager, statsMultiAllelic, unknownGenotype);
    }

    @Override
    public List<VariantStats> apply(List<VariantRow> list) throws Exception {
        return list.stream().map(this::apply).collect(Collectors.toCollection(() -> new ArrayList<>(list.size())));
    }

    public VariantStats apply(Result result) {
        return apply(new VariantRow(result));
    }

    public VariantStats apply(VariantRow result) {
        Variant variant = result.getVariant();

        VariantStatsPartial partial = new VariantStatsPartial();
        convert(result, variant, partial);

        return calculate(variant, partial);
    }

    protected void convert(Result result, Variant variant, VariantStatsPartial partial) {
        convert(new VariantRow(result), variant, partial);
    }

    protected void convert(VariantRow result, Variant variant, VariantStatsPartial partial) {
        converter.apply(variant, result, partial);
    }

    protected VariantStats calculate(Variant variant, VariantStatsPartial partial) {
        VariantStats stats = VariantStatsCalculator.calculate(variant, partial.gtCountMap, statsMultiAllelic);
        VariantStatsCalculator.calculateFilterFreq(stats, partial.numFileFilterWithVariant, partial.filterCount);
        stats.setQualityAvg(((float) (partial.qualitySum / partial.numFileQualWithVariant)));
        return stats;
    }

    protected static class VariantStatsPartial {
        VariantStatsPartial() {
            this.gtCountMap = new HashMap<>();
            this.filterCount = new HashMap<>();
            this.qualitySum = 0;
            this.numFileFilterWithVariant = 0;
            this.numFileQualWithVariant = 0;
        }

        private Map<Genotype, Integer> gtCountMap;
        private Map<String, Integer> filterCount;
        private double qualitySum;
        private int numFileFilterWithVariant;
        private int numFileQualWithVariant;
    }

    private final class HBaseToGenotypeCountConverter extends HBaseToStudyEntryConverter {
        private final Set<Integer> sampleIdsSet;
        private final Set<Integer> fileIds;
        private final Map<Integer, Collection<Integer>> samplesInFile;
        private String defaultGenotype;

        private HBaseToGenotypeCountConverter(VariantStorageMetadataManager metadataManager,
                                              boolean statsMultiAllelic, String unknownGenotype) {
            super(metadataManager, null);
            sampleIdsSet = new HashSet<>(sampleIds);
            if (excludeFiles(statsMultiAllelic, unknownGenotype, Aggregation.NONE)) {
                fileIds = Collections.emptySet();
                samplesInFile = Collections.emptyMap();
            } else {
                fileIds = new HashSet<>(sampleIds.size());
                samplesInFile = new HashMap<>(sampleIds.size());

                metadataManager.sampleMetadataIterator(sm.getId()).forEachRemaining(sampleMetadata -> {
                    int sampleId = sampleMetadata.getId();
                    if (sampleIds.contains(sampleId)) {
                        fileIds.addAll(sampleMetadata.getFiles());
                        for (Integer file : sampleMetadata.getFiles()) {
                            samplesInFile.computeIfAbsent(file, f -> new HashSet<>()).add(sampleId);
                        }
                    }
                });
            }

            super.setSelectVariantElements(new VariantQueryFields(sm, sampleIds, Collections.emptyList()));
            super.setUnknownGenotype(unknownGenotype);
            defaultGenotype = getDefaultGenotype(sm);
        }

        public VariantStatsPartial apply(Variant variant, VariantRow result, VariantStatsPartial partial) {
            Set<Integer> processedSamples = new HashSet<>();
            Set<Integer> filesInThisVariant = new HashSet<>();
            AtomicInteger fillMissingColumnValue = new AtomicInteger(-1);
            Map<Integer, String> sampleToGT = new HashMap<>();
            Map<String, List<Integer>> alternateFileMap = new HashMap<>();
            Map<Integer, String> filterMap = new HashMap<>();
            Map<Integer, Double> qualityMap = new HashMap<>();

            result.walker()
                    .onSample(sample -> {
                        int sampleId = sample.getSampleId();
                        // Exclude other samples
                        if (sampleIdsSet.contains(sampleId)) {
                            processedSamples.add(sampleId);

                            String gt = sample.getGT();
                            if (gt.isEmpty()) {
                                // This is a really weird situation, most likely due to errors in the input files
                                logger.error("Empty genotype at sample " + sampleId + " in variant " + variant);
                            } else {
                                sampleToGT.put(sampleId, gt);
                            }
                        }
                    })
                    .onFile(file -> {
                        int fileId = file.getFileId();
                        if (fileIds.contains(fileId)) {
                            filesInThisVariant.add(fileId);

                            String secAlt = file.getString(FILE_SEC_ALTS_IDX);

                            if (StringUtils.isNotEmpty(secAlt)) {
                                alternateFileMap.computeIfAbsent(secAlt, (key) -> new ArrayList<>()).add(fileId);
                            }
                            String filter = file.getFilter();
                            if (filter != null) {
                                filterMap.put(fileId, filter);
                            }
                            Double qual = file.getQual();
                            if (qual != null) {
                                qualityMap.put(fileId, qual);
                            }
                        }
                    })
                    .onFillMissing((studyId, value) -> fillMissingColumnValue.set(value))
                    .walk();

            // If there are multiple different alternates, rearrange genotype
            if (alternateFileMap.size() > 1) {
                rearrangeGenotypes(variant, sampleToGT, alternateFileMap);
            }

            Map<String, Integer> gtStrCount = new HashMap<>(5);
            for (String gt : sampleToGT.values()) {
                addGt(gtStrCount, gt, 1);
            }

            Set<Integer> unknownSamples = Collections.emptySet();
            if (processedSamples.size() != sampleIds.size()) {

                if (defaultGenotype.equals(HOM_REF)) {
                    // All missing samples are reference.
                    addGt(gtStrCount, HOM_REF, sampleIds.size() - processedSamples.size());
                } else if (fillMissingColumnValue.get() == -1 && filesInThisVariant.isEmpty()) {
                    // All missing samples are unknown.
                    addGt(gtStrCount, defaultGenotype, sampleIds.size() - processedSamples.size());
                } else {
                    // Some samples are missing, some other are reference.
                    unknownSamples = new HashSet<>();

                    // Same order as "sampleIds"
                    List<Boolean> missingUpdatedList = getMissingUpdatedSamples(sm, fillMissingColumnValue.get());
                    List<Boolean> sampleWithVariant = getSampleWithVariant(sm, filesInThisVariant);
                    int i = 0;
                    int reference = 0;
                    int unknown = 0;
                    for (Integer sampleId : sampleIds) {
                        if (!processedSamples.contains(sampleId)) {
                            if (missingUpdatedList.get(i) || sampleWithVariant.get(i)) {
                                reference++;
                            } else {
                                unknownSamples.add(sampleId);
                                unknown++;
                            }
                        }
                        i++;
                    }
                    addGt(gtStrCount, HOM_REF, reference);
                    addGt(gtStrCount, defaultGenotype, unknown);
                }
            }

            for (Integer fileId : filesInThisVariant) {
                for (Integer sampleId : samplesInFile.get(fileId)) {
                    String gt = sampleToGT.get(sampleId);
                    if (gt == null && GenotypeClass.MAIN_ALT.test(gt)) {
                        String filter = filterMap.get(fileId);
                        if (filter != null) {
                            VariantStatsCalculator.addFileFilter(filter, partial.filterCount);
                            partial.numFileFilterWithVariant += 1;
                        }
                        Double qual = qualityMap.get(fileId);
                        if (qual != null) {
                            partial.qualitySum += qual;
                            partial.numFileQualWithVariant += 1;
                        }
                        break;
                    }
                }
            }

            gtStrCount.forEach((str, count) -> partial.gtCountMap.merge(new Genotype(str), count, Integer::sum));

            return partial;
        }

        private void rearrangeGenotypes(Variant variant, Map<Integer, String> sampleToGT, Map<String, List<Integer>> alternateFileMap) {
            // Get set of reordered alternates.
            // Include the main alternate as first alternate. The "alternateFileMap" only contains the secondary alternates.
            Set<AlternateCoordinate> reorderedAlternatesSet = new LinkedHashSet<>();
            AlternateCoordinate mainAlternate = new AlternateCoordinate(
                    variant.getChromosome(), variant.getStart(), variant.getEnd(),
                    variant.getReference(), variant.getAlternate(), variant.getType());
            reorderedAlternatesSet.add(mainAlternate);

            // Add other secondary alternates
            for (Map.Entry<String, List<Integer>> entry : alternateFileMap.entrySet()) {
                String secAlt = entry.getKey();
                List<AlternateCoordinate> alternateCoordinates = getAlternateCoordinates(secAlt);
                reorderedAlternatesSet.addAll(alternateCoordinates);
            }
            List<AlternateCoordinate> reorderedAlternates = new ArrayList<>(reorderedAlternatesSet);

            boolean first = true;
            for (Map.Entry<String, List<Integer>> entry : alternateFileMap.entrySet()) {
                if (first) {
                    first = false;
                    // Skip first alternate. As it is the first, it does not need to be rearranged.
                    continue;
                }
                String secAlt = entry.getKey();
                List<AlternateCoordinate> alternateCoordinates = getAlternateCoordinates(secAlt);
                // Same as before. Add the main alternate as first alternate. It only contains secondary alternates.
                alternateCoordinates.add(0, mainAlternate);
                VariantAlternateRearranger rearranger = new VariantAlternateRearranger(alternateCoordinates, reorderedAlternates);

                for (Integer fileId : entry.getValue()) {
                    for (Integer sampleId : samplesInFile.get(fileId)) {
                        String gt = sampleToGT.get(sampleId);
                        if (gt != null) {
                            try {
                                Genotype newGt = rearranger.rearrangeGenotype(new Genotype(gt));
                                sampleToGT.put(sampleId, newGt.toString());
                            } catch (RuntimeException e) {
                                throw new IllegalStateException("Error rearranging GT " + gt + " at variant " + variant
                                        + " with reorderedAlternates " + reorderedAlternates
                                        + " and originalAlternates " + alternateCoordinates, e);
                            }
                        }
                    }
                }
            }
        }

        private void addGt(Map<String, Integer> gtStrCount, String gt, int num) {
            gtStrCount.merge(gt, num, Integer::sum);
        }
    }

    protected static boolean excludeFiles(boolean statsMultiAllelic, String unknownGenotype, Aggregation aggregation) {
        boolean calculateFilterQualStats = true;
        return !calculateFilterQualStats
                && !statsMultiAllelic
                && unknownGenotype.equals(HOM_REF)
                && !AggregationUtils.isAggregated(aggregation);
    }

}
