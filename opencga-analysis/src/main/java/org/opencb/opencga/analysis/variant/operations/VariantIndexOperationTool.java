package org.opencb.opencga.analysis.variant.operations;

import io.jsonwebtoken.lang.Collections;
import org.opencb.opencga.core.api.ParamConstants;
import org.opencb.opencga.core.models.common.Enums;
import org.opencb.opencga.core.models.variant.VariantIndexParams;
import org.opencb.opencga.core.tools.annotations.Tool;
import org.opencb.opencga.storage.core.StoragePipelineResult;
import org.opencb.opencga.storage.core.variant.VariantStorageOptions;
import org.opencb.opencga.storage.core.variant.io.VariantReaderUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.opencb.opencga.analysis.variant.manager.operations.VariantFileIndexerOperationManager.LOAD;
import static org.opencb.opencga.analysis.variant.manager.operations.VariantFileIndexerOperationManager.TRANSFORM;

@Tool(id = VariantIndexOperationTool.ID, description = VariantIndexOperationTool.DESCRIPTION,
        type = Tool.Type.OPERATION, resource = Enums.Resource.VARIANT)
public class VariantIndexOperationTool extends OperationTool {
    public static final String ID = "variant-index";
    public static final String DESCRIPTION = "Index variant files into the variant storage";

    private VariantIndexParams indexParams = new VariantIndexParams();
    private String study;

    public void setStudy(String study) {
        params.put(ParamConstants.STUDY_PARAM, study);
    }

    public void setFile(String file) {
        indexParams.setFile(file);
    }

    public void setTransform(boolean transform) {
        indexParams.setTransform(transform);
    }

    public void setLoad(boolean load) {
        indexParams.setLoad(load);
    }

    @Override
    protected void check() throws Exception {
        super.check();

        indexParams.updateParams(params);
        study = getStudyFqn();

        params.put(LOAD, indexParams.isLoad());
        params.put(TRANSFORM, indexParams.isTransform());
        if (indexParams.isTransform() && !indexParams.isLoad()) {
            // Ensure keeping intermediate files if only transforming
            keepIntermediateFiles = true;
        }

        params.put(VariantStorageOptions.MERGE_MODE.key(), indexParams.getMerge());

        params.put(VariantStorageOptions.STATS_CALCULATE.key(), indexParams.isCalculateStats());
        params.put(VariantStorageOptions.EXTRA_FORMAT_FIELDS.key(), indexParams.getIncludeExtraFields());
        params.put(VariantStorageOptions.EXCLUDE_GENOTYPES.key(), indexParams.isExcludeGenotype());
        params.put(VariantStorageOptions.STATS_AGGREGATION.key(), indexParams.getAggregated());
        params.put(VariantStorageOptions.STATS_AGGREGATION_MAPPING_FILE.key(), indexParams.getAggregationMappingFile());
        params.put(VariantStorageOptions.GVCF.key(), indexParams.isGvcf());

//        queryOptions.putIfNotNull(VariantFileIndexerStorageOperation.TRANSFORMED_FILES, indexParams.transformedPaths);

        params.put(VariantStorageOptions.ANNOTATE.key(), indexParams.isAnnotate());
        if (indexParams.getAnnotator() != null) {
            params.put(VariantStorageOptions.ANNOTATOR.key(),
                    indexParams.getAnnotator());
        }
        params.put(VariantStorageOptions.ANNOTATION_OVERWEITE.key(), indexParams.isOverwriteAnnotations());
        params.put(VariantStorageOptions.RESUME.key(), indexParams.isResume());
        params.put(VariantStorageOptions.LOAD_SPLIT_DATA.key(), indexParams.isLoadSplitData());
        params.put(VariantStorageOptions.POST_LOAD_CHECK_SKIP.key(), indexParams.isSkipPostLoadCheck());
        params.put(VariantStorageOptions.INDEX_SEARCH.key(), indexParams.isIndexSearch());
    }

    @Override
    protected void run() throws Exception {
        step(() -> {
            List<StoragePipelineResult> results =
                    variantStorageManager.index(study, indexParams.getFile(), getOutDir(keepIntermediateFiles).toString(), params, token);
            addAttribute("indexedFiles", Collections.size(results));
            if (Collections.isEmpty(results)) {
                addWarning("Nothing to do!");
            }

            if (!keepIntermediateFiles) {
                File[] files = getScratchDir().toFile().listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().endsWith(VariantReaderUtils.MALFORMED_FILE + ".txt")) {
                            Files.move(file.toPath(), getOutDir());
                        }
                    }
                }
            }
        });
    }
}
