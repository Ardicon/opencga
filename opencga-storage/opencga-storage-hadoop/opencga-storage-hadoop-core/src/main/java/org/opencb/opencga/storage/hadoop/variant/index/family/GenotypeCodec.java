package org.opencb.opencga.storage.hadoop.variant.index.family;

import org.opencb.biodata.models.feature.Genotype;
import org.opencb.opencga.storage.core.variant.adaptors.GenotypeClass;

/**
 * Created on 03/04/19.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class GenotypeCodec {

    public static final int NUM_CODES = 16;

    public static final byte HOM_REF_UNPHASED   = 0;  //  0/0
    public static final byte HET_REF_UNPHASED   = 1;  //  0/1
    public static final byte HOM_ALT_UNPHASED   = 2;  //  1/1

    public static final byte HOM_REF_PHASED     = 3;  //  0|0
    public static final byte HET_REF_01_PHASED  = 4;  //  0|1
    public static final byte HET_REF_10_PHASED  = 5;  //  1|0
    public static final byte HOM_ALT_PHASED     = 6;  //  1|1

    public static final byte HEMI_REF           = 7;  //  0
    public static final byte HEMI_ALT           = 8;  //  1

    public static final byte MULTI_HOM          = 9;  //  2/2
    public static final byte MULTI_HET          = 10; //  1/2, 0/2, ..
    public static final byte MISSING_HOM        = 11; //  ./.
    public static final byte MISSING_HET        = 12; //  ./0 ./1 ...
    public static final byte UNKNOWN            = 13; //  ?/?

    public static final byte UNUSED_14          = 14; //
    public static final byte UNUSED_15          = 15; //

    // GT codes that refer to only one possible genotype
    private static final boolean[] AMBIGUOUS_GT_CODE = new boolean[]{
            false,  // HOM_REF_UNPHASED
            false,  // HET_REF_UNPHASED
            false,  // HOM_ALT_UNPHASED
            false,  // HOM_REF_PHASED
            false,  // HET_REF_01_PHASED
            false,  // HET_REF_10_PHASED
            false,  // HOM_ALT_PHASED
            false,  // HEMI_REF
            false,  // HEMI_ALT
            true,   // MULTI_HOM
            true,   // MULTI_HET
            true,   // MISSING_HOM
            true,   // MISSING_HET
            true,   // UNKNOWN
            true,   // UNUSED_14
            true,   // UNUSED_15
    };

    public static byte[] split(byte code) {
        return new byte[]{(byte) (code >>> 4 & 0b00001111), (byte) (code & 0b00001111)};
    }

    public static byte join(byte fatherCode, byte motherCode) {
        return (byte) (fatherCode << 4 | motherCode);
    }

    public static byte encode(String fatherGenotype, String motherGenotype) {
        byte fatherCode = encode(fatherGenotype);
        byte motherCode = encode(motherGenotype);
        return join(fatherCode, motherCode);
    }

    public static byte encode(String genotype) {

        if (genotype == null || genotype.isEmpty()) {
            return UNKNOWN;
        }

        switch (genotype) {
            case "0/0":
                return HOM_REF_UNPHASED;
            case "0/1":
                return HET_REF_UNPHASED;
            case "1/1":
                return HOM_ALT_UNPHASED;

            case "0|0" :
                return HOM_REF_PHASED;
            case "0|1" :
                return HET_REF_01_PHASED;
            case "1|0" :
                return HET_REF_10_PHASED;
            case "1|1" :
                return HOM_ALT_PHASED;

            case "0":
                return HEMI_REF;
            case "1":
                return HEMI_ALT;

            case GenotypeClass.UNKNOWN_GENOTYPE:
            case GenotypeClass.NA_GT_VALUE:
                return UNKNOWN;

            case "./.":
            case ".":
            case ".|.":
                return MISSING_HOM;

            default:
                if (genotype.contains(".")) {
                    return MISSING_HET;
                } else {
                    try {
                        Genotype gt = new Genotype(genotype);
                        int[] alleles = gt.getAllelesIdx();
                        for (int i = 1; i < alleles.length; i++) {
                            if (alleles[i] != alleles[0]) {
                                return MULTI_HET;
                            }
                        }
                        return MULTI_HOM;
                    } catch (IllegalArgumentException e) {
                        return UNKNOWN;
                    }
                }
        }
    }

    public static boolean isAmbiguousCode(int i) {
        return AMBIGUOUS_GT_CODE[i];
    }
}
