package org.opencb.opencga.storage.mongodb.variant.load.stage;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import org.bson.Document;
import org.bson.types.Binary;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.VariantType;
import org.opencb.commons.run.ParallelTaskRunner;
import org.opencb.opencga.core.common.ProgressLogger;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.opencb.opencga.storage.mongodb.variant.load.stage.MongoDBVariantStageLoader.STRING_ID_CONVERTER;
import static org.opencb.opencga.storage.mongodb.variant.load.stage.MongoDBVariantStageLoader.VARIANT_CONVERTER_DEFAULT;

/**
 * Created on 18/11/16.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class MongoDBVariantStageConverterTask implements ParallelTaskRunner.Task<Variant, ListMultimap<Document, Binary>> {

    private final ProgressLogger progressLogger;
    private final AtomicLong skippedVariants;

    public MongoDBVariantStageConverterTask(ProgressLogger progressLogger) {
        this.progressLogger = progressLogger;
        skippedVariants = new AtomicLong(0);
    }

    @Override
    public void pre() {
        skippedVariants.set(0);
    }

    @Override
    public List<ListMultimap<Document, Binary>> apply(List<Variant> variants) throws RuntimeException {
//        final long start = System.nanoTime();
        int localSkippedVariants = 0;

        ListMultimap<Document, Binary> ids = LinkedListMultimap.create();

        for (Variant variant : variants) {
            if (variant.getType().equals(VariantType.NO_VARIATION) || variant.getType().equals(VariantType.SYMBOLIC)) {
                localSkippedVariants++;
                continue;
            }
            Binary binary = VARIANT_CONVERTER_DEFAULT.convertToStorageType(variant);
            Document id = STRING_ID_CONVERTER.convertToStorageType(variant);

            ids.put(id, binary);
        }
        if (progressLogger != null) {
            progressLogger.increment(variants.size(), () -> "up to variant " + variants.get(variants.size() - 1));
        }

        skippedVariants.addAndGet(localSkippedVariants);
        return Collections.singletonList(ids);
    }

    public long getSkippedVariants() {
        return skippedVariants.get();
    }
}
