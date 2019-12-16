package org.opencb.opencga.analysis.variant.stats;

import org.apache.commons.io.FileUtils;
import org.opencb.biodata.models.variant.StudyEntry;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.stats.VariantStats;
import org.opencb.biodata.tools.variant.stats.VariantStatsCalculator;
import org.opencb.biodata.tools.variant.stats.writer.VariantStatsTsvExporter;
import org.opencb.commons.ProgressLogger;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.run.ParallelTaskRunner;
import org.opencb.commons.run.Task;
import org.opencb.opencga.core.tools.variant.VariantStatsAnalysisExecutor;
import org.opencb.opencga.core.annotations.ToolExecutor;
import org.opencb.opencga.core.exception.ToolException;
import org.opencb.opencga.core.exception.ToolExecutorException;
import org.opencb.opencga.analysis.variant.manager.VariantStorageAnalysisExecutor;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.storage.core.exceptions.StorageEngineException;
import org.opencb.opencga.analysis.variant.manager.VariantStorageManager;
import org.opencb.opencga.storage.core.variant.VariantStorageEngine;
import org.opencb.opencga.storage.core.variant.adaptors.VariantField;
import org.opencb.opencga.storage.core.variant.adaptors.VariantQueryParam;
import org.opencb.opencga.storage.core.variant.io.db.VariantDBReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;

@ToolExecutor(id = "opencga-local", tool = VariantStatsAnalysis.ID,
        framework = ToolExecutor.Framework.LOCAL,
        source = ToolExecutor.Source.STORAGE)
public class VariantStatsLocalAnalysisExecutor extends VariantStatsAnalysisExecutor implements VariantStorageAnalysisExecutor {

    private final Logger logger = LoggerFactory.getLogger(VariantStatsLocalAnalysisExecutor.class);

    @Override
    public void run() throws ToolException {
        if (isIndex()) {
            calculateAndIndex();
        } else {
            calculate();
        }
    }

    private void calculateAndIndex() throws ToolExecutorException {
        QueryOptions calculateStatsOptions = new QueryOptions(executorParams);

        VariantStorageEngine variantStorageEngine = getVariantStorageEngine();
        try {
            calculateStatsOptions.putAll(getVariantsQuery());

            variantStorageEngine.calculateStats(getStudy(), getCohorts(), calculateStatsOptions);
            variantStorageEngine.close();
        } catch (IOException | StorageEngineException e) {
            throw new ToolExecutorException(e);
        }
    }

    private void calculate() throws ToolException {
        VariantStorageManager manager = getVariantStorageManager();
        Map<String, List<String>> cohorts = getCohorts();
        Set<String> allSamples = new HashSet<>();
        cohorts.values().forEach(allSamples::addAll);
        Query query = new Query(getVariantsQuery())
                .append(VariantQueryParam.STUDY.key(), getStudy())
                .append(VariantQueryParam.INCLUDE_SAMPLE.key(), allSamples);

        QueryOptions queryOptions = new QueryOptions(QueryOptions.EXCLUDE, Arrays.asList(VariantField.STUDIES_STATS));

        try (OutputStream os = new BufferedOutputStream(FileUtils.openOutputStream(getOutputFile().toFile()))) {
            VariantDBReader reader = new VariantDBReader(manager.iterator(query, queryOptions, getToken()));

            ProgressLogger progressLogger = new ProgressLogger("Variants processed:");
            Task<Variant, Variant> task = Task.forEach(variant -> {
                StudyEntry study = variant.getStudies().get(0);
                HashMap<String, VariantStats> map = new HashMap<>();
                for (Map.Entry<String, List<String>> entry : cohorts.entrySet()) {
                    map.put(entry.getKey(), VariantStatsCalculator.calculate(variant, study, entry.getValue()));
                }
                study.setStats(map);
                progressLogger.increment(1);
                return variant;
            });

            VariantStatsTsvExporter writer = new VariantStatsTsvExporter(os, getStudy(), new ArrayList<>(cohorts.keySet()));

            ParallelTaskRunner.Config config = ParallelTaskRunner.Config.builder().build();
            ParallelTaskRunner<Variant, Variant> ptr = new ParallelTaskRunner<>(reader, task, writer, config);

            ptr.run();

            addAttribute("numVariantStats", writer.getWrittenVariants());
        } catch (ExecutionException | IOException | CatalogException | StorageEngineException e) {
            throw new ToolExecutorException(e);
        }
    }
}
