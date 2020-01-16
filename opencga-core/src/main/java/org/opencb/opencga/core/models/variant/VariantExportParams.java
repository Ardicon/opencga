package org.opencb.opencga.core.models.variant;

import org.opencb.commons.datastore.core.Query;

public class VariantExportParams extends VariantQueryParams {
    public static final String DESCRIPTION = "Variant export params";
    private String outdir;
    private String outputFileName;
    private String outputFormat;
    private boolean compress;
    private String variantsFile;
    private String include;
    private String exclude;
    private int limit;
    private int skip;
    private boolean summary;

    public VariantExportParams() {
    }

    public VariantExportParams(Query query, String outdir, String outputFileName, String outputFormat,
                               boolean compress, String variantsFile) {
        super(query);
        this.outdir = outdir;
        this.outputFileName = outputFileName;
        this.outputFormat = outputFormat;
        this.compress = compress;
        this.variantsFile = variantsFile;
    }

    public String getOutdir() {
        return outdir;
    }

    public VariantExportParams setOutdir(String outdir) {
        this.outdir = outdir;
        return this;
    }

    public String getOutputFileName() {
        return outputFileName;
    }

    public VariantExportParams setOutputFileName(String outputFileName) {
        this.outputFileName = outputFileName;
        return this;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public VariantExportParams setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
        return this;
    }

    public boolean isCompress() {
        return compress;
    }

    public VariantExportParams setCompress(boolean compress) {
        this.compress = compress;
        return this;
    }

    public void setVariantsFile(String variantsFile) {
        this.variantsFile = variantsFile;
    }

    public String getVariantsFile() {
        return variantsFile;
    }

    public String getInclude() {
        return include;
    }

    public VariantExportParams setInclude(String include) {
        this.include = include;
        return this;
    }

    public String getExclude() {
        return exclude;
    }

    public VariantExportParams setExclude(String exclude) {
        this.exclude = exclude;
        return this;
    }

    public int getLimit() {
        return limit;
    }

    public VariantExportParams setLimit(int limit) {
        this.limit = limit;
        return this;
    }

    public int getSkip() {
        return skip;
    }

    public VariantExportParams setSkip(int skip) {
        this.skip = skip;
        return this;
    }

    public boolean isSummary() {
        return summary;
    }

    public VariantExportParams setSummary(boolean summary) {
        this.summary = summary;
        return this;
    }
}
