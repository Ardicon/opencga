/*
 * Copyright 2015-2017 OpenCB
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

package org.opencb.opencga.storage.hadoop.variant.converters.stats;

import org.apache.hadoop.hbase.client.Put;
import org.opencb.biodata.models.feature.Genotype;
import org.opencb.biodata.models.variant.protobuf.VariantProto;
import org.opencb.biodata.models.variant.stats.VariantStats;
import org.opencb.biodata.tools.Converter;
import org.opencb.opencga.storage.core.metadata.models.StudyMetadata;
import org.opencb.opencga.storage.core.variant.stats.VariantStatsWrapper;
import org.opencb.opencga.storage.hadoop.variant.GenomeHelper;
import org.opencb.opencga.storage.hadoop.variant.adaptors.phoenix.PhoenixHelper.Column;
import org.opencb.opencga.storage.hadoop.variant.adaptors.phoenix.VariantPhoenixHelper;
import org.opencb.opencga.storage.hadoop.variant.converters.AbstractPhoenixConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;

import static org.opencb.opencga.storage.hadoop.variant.adaptors.phoenix.VariantPhoenixKeyFactory.generateVariantRowKey;

/**
 * Created on 07/07/16.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class VariantStatsToHBaseConverter extends AbstractPhoenixConverter implements Converter<VariantStatsWrapper, Put> {

    private final GenomeHelper genomeHelper;
    private final StudyMetadata studyMetadata;
    private final int studyId;
    private final Logger logger = LoggerFactory.getLogger(VariantStatsToHBaseConverter.class);
    private final Map<String, Integer> cohortIds;

    public VariantStatsToHBaseConverter(GenomeHelper genomeHelper, StudyMetadata studyMetadata, Map<String, Integer> cohortIds) {
        super(GenomeHelper.COLUMN_FAMILY_BYTES);
        this.genomeHelper = genomeHelper;
        this.studyMetadata = studyMetadata;
        this.studyId = studyMetadata.getId();
        this.cohortIds = cohortIds;
    }

    @Override
    public Put convert(VariantStatsWrapper variantStatsWrapper) {
        if (variantStatsWrapper.getCohortStats() == null || variantStatsWrapper.getCohortStats().isEmpty()) {
            return null;
        }

        byte[] row = generateVariantRowKey(
                variantStatsWrapper.getChromosome(), variantStatsWrapper.getStart(), variantStatsWrapper.getEnd(),
                variantStatsWrapper.getReference(), variantStatsWrapper.getAlternate(), variantStatsWrapper.getSv());
        Put put = new Put(row);
        for (Map.Entry<String, VariantStats> entry : variantStatsWrapper.getCohortStats().entrySet()) {
            Integer cohortId = cohortIds.get(entry.getKey());
            if (cohortId == null) {
                continue;
            }
            Column mafColumn = VariantPhoenixHelper.getStatsMafColumn(studyId, cohortId);
            Column mgfColumn = VariantPhoenixHelper.getStatsMgfColumn(studyId, cohortId);
            Column cohortColumn = VariantPhoenixHelper.getStatsFreqColumn(studyId, cohortId);
            Column statsColumn = VariantPhoenixHelper.getStatsColumn(studyId, cohortId);

            VariantStats stats = entry.getValue();
            add(put, mafColumn, stats.getMaf());
            add(put, mgfColumn, stats.getMgf());
            add(put, cohortColumn, Arrays.asList(stats.getRefAlleleFreq(), stats.getAltAlleleFreq()));

            VariantProto.VariantStats.Builder builder = VariantProto.VariantStats.newBuilder()
                    .setAltAlleleFreq(stats.getAltAlleleFreq())
                    .setAltAlleleCount(stats.getAltAlleleCount())
                    .setRefAlleleFreq(stats.getRefAlleleFreq())
                    .setRefAlleleCount(stats.getRefAlleleCount())
                    .setAlleleCount(stats.getAlleleCount())
                    .setMissingAlleleCount(stats.getMissingAlleleCount())
                    .setMissingGenotypeCount(stats.getMissingGenotypeCount());

            if (stats.getMafAllele() != null) {
                builder.setMafAllele(stats.getMafAllele());
            }
            builder.setMaf(stats.getMaf());

            if (stats.getMgfGenotype() != null) {
                builder.setMgfGenotype(stats.getMgfGenotype());
            }
            builder.setMgf(stats.getMgf());

            if (stats.getGenotypeCount() != null) {
                for (Map.Entry<Genotype, Integer> e : stats.getGenotypeCount().entrySet()) {
                    builder.putGenotypeCount(e.getKey().toString(), e.getValue());
                }
//                assert builder.getGenotypeCount() == stats.getGenotypeCount().size();
            }

            if (stats.getGenotypeFreq() != null) {
                for (Map.Entry<Genotype, Float> e : stats.getGenotypeFreq().entrySet()) {
                    builder.putGenotypeFreq(e.getKey().toString(), e.getValue());
                }
//                assert builder.getGenotypeFreqCount() == stats.getGenotypeFreq().size();
            }

            add(put, statsColumn, builder.build().toByteArray());
        }
        return put;
    }

}
