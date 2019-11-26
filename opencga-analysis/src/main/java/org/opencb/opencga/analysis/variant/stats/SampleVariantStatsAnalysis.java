package org.opencb.opencga.analysis.variant.stats;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.opencb.biodata.models.variant.metadata.SampleVariantStats;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.analysis.OpenCgaAnalysis;
import org.opencb.opencga.catalog.db.api.SampleDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.utils.AvroToAnnotationConverter;
import org.opencb.opencga.core.analysis.result.FileResult;
import org.opencb.opencga.core.analysis.variant.SampleVariantStatsAnalysisExecutor;
import org.opencb.opencga.core.annotations.Analysis;
import org.opencb.opencga.core.common.JacksonUtils;
import org.opencb.opencga.core.exception.AnalysisException;
import org.opencb.opencga.core.models.*;
import org.opencb.opencga.storage.core.exceptions.StorageEngineException;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

@Analysis(id = SampleVariantStatsAnalysis.ID, type = Analysis.AnalysisType.VARIANT, description = SampleVariantStatsAnalysis.DESCRIPTION)
public class SampleVariantStatsAnalysis extends OpenCgaAnalysis {

    public static final String ID = "sample-variant-stats";
    public static final String DESCRIPTION = "Compute sample variant stats for the selected list of samples.";
    public static final String VARIABLE_SET_ID = "SAMPLE_VARIANT_STATS";
    private String study;
    private Query samplesQuery;
    private List<String> sampleNames;
    private String individual;
    private String family;
    private ArrayList<String> checkedSamplesList;
    private boolean indexResults = false;
    private Path outputFile;

    /**
     * Study of the samples.
     * @param study Study id
     * @return this
     */
    public SampleVariantStatsAnalysis setStudy(String study) {
        this.study = study;
        return this;
    }

    /**
     * List of samples.
     * @param sampleNames Sample names
     * @return this
     */
    public SampleVariantStatsAnalysis setSampleNames(List<String> sampleNames) {
        this.sampleNames = sampleNames;
        return this;
    }

    /**
     * Samples query to select samples to be used.
     * @param samplesQuery Samples query
     * @return this
     */
    public SampleVariantStatsAnalysis setSamplesQuery(Query samplesQuery) {
        this.samplesQuery = samplesQuery;
        return this;
    }

    /**
     * Select samples from this individual.
     * @param individualId individual
     * @return this
     */
    public SampleVariantStatsAnalysis setIndividual(String individualId) {
        this.individual = individualId;
        return this;
    }

    /**
     * Select samples form the individuals of this family.
     * @param family family
     * @return this
     */
    public SampleVariantStatsAnalysis setFamily(String family) {
        this.family = family;
        return this;
    }

    /**
     * Index results in catalog.
     * Create an AnnotationSet for the VariableSet {@link #VARIABLE_SET_ID}
     * containing the SampleVariantStats.
     *
     * @param indexResults index results
     * @return boolean
     */
    public SampleVariantStatsAnalysis setIndexResults(boolean indexResults) {
        this.indexResults = indexResults;
        return this;
    }

    @Override
    protected void check() throws Exception {
        super.check();
        setUpStorageEngineExecutor(study);

        Set<String> allSamples = new HashSet<>();

        try {
            study = catalogManager.getStudyManager().get(study, null, token).first().getFqn();
        } catch (CatalogException e) {
            throw new AnalysisException(e);
        }

        try {
            if (CollectionUtils.isNotEmpty(sampleNames)) {
                catalogManager.getSampleManager().get(study, sampleNames, new QueryOptions(), token)
                        .getResults()
                        .stream()
                        .map(Sample::getId)
                        .forEach(allSamples::add);
            }
            if (samplesQuery != null) {
                catalogManager.getSampleManager().search(study, samplesQuery, new QueryOptions(), token)
                        .getResults()
                        .stream()
                        .map(Sample::getId)
                        .forEach(allSamples::add);
            }
            if (StringUtils.isNotEmpty(individual)) {
                Query query = new Query(SampleDBAdaptor.QueryParams.INDIVIDUAL.key(), individual);
                catalogManager.getSampleManager().search(study, query, new QueryOptions(), token)
                        .getResults()
                        .stream()
                        .map(Sample::getId)
                        .forEach(allSamples::add);
            }
            if (StringUtils.isNotEmpty(family)) {
                Family family = catalogManager.getFamilyManager().get(study, this.family, null, token).first();
                List<String> individualIds = new ArrayList<>(family.getMembers().size());
                for (Individual member : family.getMembers()) {
                    individualIds.add(member.getId());
                }
                Query query = new Query(SampleDBAdaptor.QueryParams.INDIVIDUAL.key(), individualIds);
                catalogManager.getSampleManager().search(study, query, new QueryOptions(), token)
                        .getResults()
                        .stream()
                        .map(Sample::getId)
                        .forEach(allSamples::add);
            }

            // Remove non-indexed samples
            Set<String> indexedSamples = variantStorageManager.getIndexedSamples(study, token);
            allSamples.removeIf(s -> !indexedSamples.contains(s));

        } catch (CatalogException e) {
            throw new AnalysisException(e);
        }
        checkedSamplesList = new ArrayList<>(allSamples);
        checkedSamplesList.sort(String::compareTo);

        if (allSamples.isEmpty()) {
            throw new AnalysisException("Missing samples!");
        }

        // check read permission
        try {
            variantStorageManager.checkQueryPermissions(
                    new Query()
                            .append(VariantQueryParam.STUDY.key(), study)
                            .append(VariantQueryParam.INCLUDE_SAMPLE.key(), checkedSamplesList),
                    new QueryOptions(),
                    token);
        } catch (CatalogException | StorageEngineException e) {
            throw new AnalysisException(e);
        }
        outputFile = getOutDir().resolve(getId() + ".json");
    }

    @Override
    public List<String> getSteps() {
        List<String> steps = super.getSteps();
        if (indexResults) {
            steps.add("index");
        }
        return steps;
    }

    @Override
    protected void run() throws AnalysisException {
        step(getId(), () -> {
            getAnalysisExecutor(SampleVariantStatsAnalysisExecutor.class)
                    .setOutputFile(outputFile)
                    .setStudy(study)
                    .setSampleNames(checkedSamplesList)
                    .execute();

            addFile(outputFile, FileResult.FileType.JSON);
        });

        if (indexResults) {
            step("index", () -> indexResults(outputFile));
        }
    }

    private void indexResults(Path outputFile) throws AnalysisException {
        List<SampleVariantStats> stats = new ArrayList<>(checkedSamplesList.size());
        try {
            JacksonUtils.getDefaultObjectMapper()
                    .readerFor(SampleVariantStats.class)
                    .<SampleVariantStats>readValues(outputFile.toFile())
                    .forEachRemaining(stats::add);

            try {
                catalogManager.getStudyManager().getVariableSet(study, VARIABLE_SET_ID, new QueryOptions(), token);
            } catch (CatalogException e) {
                // Assume variable set not found. Try to create
                List<Variable> variables = AvroToAnnotationConverter.convertToVariableSet(SampleVariantStats.getClassSchema());
                catalogManager.getStudyManager()
                        .createVariableSet(study, VARIABLE_SET_ID, VARIABLE_SET_ID, true, false, "", Collections.emptyMap(), variables,
                        Collections.singletonList(VariableSet.AnnotableDataModels.SAMPLE), token);
            }

            for (SampleVariantStats sampleStats : stats) {
                AnnotationSet annotationSet = AvroToAnnotationConverter.convertToAnnotationSet(sampleStats, VARIABLE_SET_ID);
                catalogManager.getSampleManager()
                        .addAnnotationSet(study, sampleStats.getId(), annotationSet, new QueryOptions(), token);
            }
        } catch (IOException | CatalogException e) {
            throw new AnalysisException(e);
        }
    }

}
