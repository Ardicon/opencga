package org.opencb.opencga.core.models.variant;

import org.opencb.biodata.models.variant.metadata.Aggregation;
import org.opencb.opencga.core.tools.ToolParams;

import java.util.List;

public class VariantStatsAnalysisParams extends ToolParams {
    public static final String DESCRIPTION = "Variant stats params";

    public VariantStatsAnalysisParams() {
    }
    public VariantStatsAnalysisParams(List<String> cohort, List<String> samples, boolean index, String outdir, String outputFileName,
                                      String region, String gene, boolean overwriteStats, boolean updateStats, boolean resume,
                                      Aggregation aggregated, String aggregationMappingFile) {
        this.cohort = cohort;
        this.samples = samples;
        this.index = index;
        this.outdir = outdir;
        this.outputFileName = outputFileName;
        this.region = region;
        this.gene = gene;
        this.overwriteStats = overwriteStats;
        this.updateStats = updateStats;
        this.resume = resume;
        this.aggregated = aggregated;
        this.aggregationMappingFile = aggregationMappingFile;
    }

    private List<String> cohort;
    private List<String> samples;
    private boolean index;
    private String region;
    private String gene;
    private String outdir;
    private String outputFileName;
    private boolean overwriteStats;
    private boolean updateStats;

    private boolean resume;

    private Aggregation aggregated;
    private String aggregationMappingFile;

    public List<String> getCohort() {
        return cohort;
    }

    public VariantStatsAnalysisParams setCohort(List<String> cohort) {
        this.cohort = cohort;
        return this;
    }

    public List<String> getSamples() {
        return samples;
    }

    public VariantStatsAnalysisParams setSamples(List<String> samples) {
        this.samples = samples;
        return this;
    }

    public boolean isIndex() {
        return index;
    }

    public VariantStatsAnalysisParams setIndex(boolean index) {
        this.index = index;
        return this;
    }

    public String getRegion() {
        return region;
    }

    public VariantStatsAnalysisParams setRegion(String region) {
        this.region = region;
        return this;
    }

    public String getGene() {
        return gene;
    }

    public VariantStatsAnalysisParams setGene(String gene) {
        this.gene = gene;
        return this;
    }

    public String getOutdir() {
        return outdir;
    }

    public VariantStatsAnalysisParams setOutdir(String outdir) {
        this.outdir = outdir;
        return this;
    }

    public String getOutputFileName() {
        return outputFileName;
    }

    public VariantStatsAnalysisParams setOutputFileName(String outputFileName) {
        this.outputFileName = outputFileName;
        return this;
    }

    public boolean isOverwriteStats() {
        return overwriteStats;
    }

    public VariantStatsAnalysisParams setOverwriteStats(boolean overwriteStats) {
        this.overwriteStats = overwriteStats;
        return this;
    }

    public boolean isUpdateStats() {
        return updateStats;
    }

    public VariantStatsAnalysisParams setUpdateStats(boolean updateStats) {
        this.updateStats = updateStats;
        return this;
    }

    public boolean isResume() {
        return resume;
    }

    public VariantStatsAnalysisParams setResume(boolean resume) {
        this.resume = resume;
        return this;
    }

    public Aggregation getAggregated() {
        return aggregated;
    }

    public VariantStatsAnalysisParams setAggregated(Aggregation aggregated) {
        this.aggregated = aggregated;
        return this;
    }

    public String getAggregationMappingFile() {
        return aggregationMappingFile;
    }

    public VariantStatsAnalysisParams setAggregationMappingFile(String aggregationMappingFile) {
        this.aggregationMappingFile = aggregationMappingFile;
        return this;
    }
}
