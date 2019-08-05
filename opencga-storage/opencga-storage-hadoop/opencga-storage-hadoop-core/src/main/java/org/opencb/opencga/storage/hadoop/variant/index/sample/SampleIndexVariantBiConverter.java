package org.opencb.opencga.storage.hadoop.variant.index.sample;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.phoenix.schema.types.PVarchar;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.opencga.storage.hadoop.variant.adaptors.phoenix.VariantPhoenixKeyFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.opencb.opencga.storage.hadoop.variant.index.sample.SampleIndexSchema.MENDELIAN_ERROR_COLUMN_BYTES;
import static org.opencb.opencga.storage.hadoop.variant.index.sample.SampleIndexSchema.META_PREFIX;

/**
 * Created on 11/04/19.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class SampleIndexVariantBiConverter {

    private static final char STRING_SEPARATOR = ',';

    public static final int SEPARATOR_LENGTH = 1;
    public static final int INT24_LENGTH = 3;
    public static final byte BYTE_SEPARATOR = 0;

    public int expectedSize(Variant variant, boolean interVariantSeparator) {
        return expectedSize(variant.getReference(), getAlternate(variant), interVariantSeparator);
    }

    protected int expectedSize(String reference, String alternate, boolean interVariantSeparator) {
        if (AlleleCodec.valid(reference, alternate)) {
            return INT24_LENGTH; // interVariantSeparator not needed when coding alleles
        } else {
            return INT24_LENGTH + reference.length() + SEPARATOR_LENGTH + alternate.length()
                    + (interVariantSeparator ? SEPARATOR_LENGTH : 0);
        }
    }

    @Deprecated
    public byte[] toBytesSimpleString(Collection<Variant> variants) {
        return Bytes.toBytes(variants.stream().map(Variant::toString).collect(Collectors.joining(",")));
    }

    public byte[] toBytesFromStrings(Collection<String> variantsStr) {
        List<Variant> variants = new ArrayList<>(variantsStr.size());
        for (String s : variantsStr) {
            variants.add(new Variant(s));
        }
        return toBytes(variants);
    }

    public byte[] toBytes(Collection<Variant> variants) {
        int size = 0;
        for (Variant variant : variants) {
            size += expectedSize(variant, true);
        }
        byte[] bytes = new byte[size];
        toBytes(variants, bytes, 0);
        return bytes;
    }

    protected int toBytes(Collection<Variant> variants, byte[] bytes, int offset) {
        int length = 0;
        for (Variant variant : variants) {
            length += toBytes(variant, bytes, offset + length, true);
        }
        return length;
    }

    public byte[] toBytes(Variant variant) {
        return toBytes(variant, false);
    }

    public byte[] toBytes(Variant variant, boolean interVariantSeparator) {
        String alternate = getAlternate(variant);
        byte[] bytes = new byte[expectedSize(variant.getReference(), alternate, interVariantSeparator)];
        toBytes(getRelativeStart(variant), variant.getReference(), alternate, bytes, 0, interVariantSeparator);
        return bytes;
    }

    public void toBytes(Variant variant, ByteArrayOutputStream stream) throws IOException {
        stream.write(toBytes(variant, true));
    }

    public int toBytes(Variant variant, byte[] bytes, int offset) {
        return toBytes(variant, bytes, offset, false);
    }

    public int toBytes(Variant variant, byte[] bytes, int offset, boolean interVariantSeparator) {
        return toBytes(getRelativeStart(variant), variant.getReference(), getAlternate(variant), bytes, offset, interVariantSeparator);
    }

    protected int toBytes(int relativeStart, String reference, String alternate, byte[] bytes, int offset, boolean interVariantSeparator) {
        if (AlleleCodec.valid(reference, alternate)) {
            int length = append24bitInteger(relativeStart, bytes, offset);
            bytes[offset] |= AlleleCodec.encode(reference, alternate);
            return length;
        } else {
            int length = 0;
            length += append24bitInteger(relativeStart, bytes, offset + length);
            length += appendString(reference, bytes, offset + length);
            length += appendSeparator(bytes, offset + length);
            length += appendString(alternate, bytes, offset + length);
            if (interVariantSeparator) {
                length += appendSeparator(bytes, offset + length);
            }
            return length;
        }
    }

    public Variant toVariant(String chromosome, int batchStart, byte[] bytes) {
        return toVariant(chromosome, batchStart, bytes, 0);
    }

    public Variant toVariant(String chromosome, int batchStart, byte[] bytes, int offset) {
        if (hasEncodedAlleles(bytes, offset)) {
            return toVariantEncodedAlleles(chromosome, batchStart, bytes, offset);
        } else {
            int referenceLength = readNextSeparator(bytes, offset + INT24_LENGTH);
            int alternateLength = readNextSeparator(bytes, offset + INT24_LENGTH + referenceLength + SEPARATOR_LENGTH);
            return toVariant(chromosome, batchStart, bytes, offset, referenceLength, alternateLength);
        }
    }

    public List<Variant> toVariants(Cell cell) {
        List<Variant> variants;
        byte[] column = CellUtil.cloneQualifier(cell);
        if (column[0] != META_PREFIX && column[0] != MENDELIAN_ERROR_COLUMN_BYTES[0]) {
            byte[] row = CellUtil.cloneRow(cell);
            String chromosome = SampleIndexSchema.chromosomeFromRowKey(row);
            int batchStart = SampleIndexSchema.batchStartFromRowKey(row);
            variants = toVariants(chromosome, batchStart, cell.getValueArray(), cell.getValueOffset(), cell.getValueLength());
        } else {
            variants = Collections.emptyList();
        }
        return variants;
    }

    public List<Variant> toVariants(String chromosome, int batchStart, byte[] bytes, int offset, int length) {
        SampleIndexVariantIterator it = toVariantsIterator(chromosome, batchStart, bytes, offset, length);
        List<Variant> variants = new ArrayList<>(it.getApproxSize());
        it.forEachRemaining(variants::add);
        return variants;
    }

    public SampleIndexVariantIterator toVariantsIterator(String chromosome, int batchStart, byte[] bytes, int offset, int length) {
        if (length <= 0) {
            return SampleIndexVariantIterator.emptyIterator();
        } else {
            if (StringSampleIndexVariantIterator.isStringCodified(chromosome, bytes, offset, length)) {
                return new StringSampleIndexVariantIterator(bytes, offset, length);
            } else {
                return new ByteSampleIndexVariantIterator(chromosome, batchStart, bytes, offset, length);
            }
        }
    }

    public SampleIndexVariantIterator toVariantsCountIterator(int count) {
        return new CountSampleIndexVariantIterator(count);
    }

    public abstract static class SampleIndexVariantIterator implements Iterator<Variant> {

        protected byte[] annotationIndex;
        protected int nonIntergenicCount = 0;

        public SampleIndexVariantIterator setAnnotationIndex(byte[] annotationIndex) {
            if (nextIndex() != 0) {
                throw new IllegalStateException("Can not change annotation index after moving the iterator!");
            }
            this.annotationIndex = annotationIndex;
            return this;
        }

        /**
         * @return the index of the element that would be returned by a
         * subsequent call to {@code next}.
         */
        public abstract int nextIndex();

        public int nextNonIntergenicIndex() {
            if (annotationIndex == null) {
                return -1;
            } else if (SampleIndexEntryFilter.isNonIntergenic(annotationIndex, nextIndex())) {
                return nonIntergenicCount;
            } else {
                throw new IllegalStateException("Next variant is not intergenic!");
            }
        }

        /**
         * @return {@code true} if the iteration has more elements
         */
        public abstract boolean hasNext();

        /**
         * Skip next element. Avoid conversion.
         */
        public abstract void skip();

        /**
         * @return next variant
         */
        public abstract Variant next();

        public abstract int getApproxSize();

        public static SampleIndexVariantIterator emptyIterator() {
            return EmptySampleIndexVariantIterator.EMPTY_ITERATOR;
        }

        protected void increaseNonIntergenicCounter() {
            // If the variant to be returned is non-intergenic, increase the number of non-intergenic variants.
            if (annotationIndex != null) {
                if (SampleIndexEntryFilter.isNonIntergenic(annotationIndex, nextIndex())) {
                    nonIntergenicCount++;
                }
            }
        }
    }

    private static final class EmptySampleIndexVariantIterator extends SampleIndexVariantIterator {

        private EmptySampleIndexVariantIterator() {
        }

        private static final EmptySampleIndexVariantIterator EMPTY_ITERATOR = new EmptySampleIndexVariantIterator();

        @Override
        public int nextIndex() {
            return 0;
        }

        @Override
        public int nextNonIntergenicIndex() {
            return -1;
        }

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public void skip() {
        }

        public int getApproxSize() {
            return 0;
        }

        @Override
        public Variant next() {
            throw new NoSuchElementException("Empty iterator");
        }
    }

    private static class StringSampleIndexVariantIterator extends SampleIndexVariantIterator {
        private final ListIterator<String> variants;
        private final int size;

        StringSampleIndexVariantIterator(byte[] value, int offset, int length) {
            List<String> values = split(value, offset, length);
            size = values.size();
            variants = values.listIterator();
        }

        @Override
        public int nextIndex() {
            return variants.nextIndex();
        }

        @Override
        public boolean hasNext() {
            return variants.hasNext();
        }

        @Override
        public void skip() {
            increaseNonIntergenicCounter();
            variants.next();
        }

        @Override
        public Variant next() {
            increaseNonIntergenicCounter();
            return new Variant(variants.next());
        }

        public int getApproxSize() {
            return size;
        }

        public static boolean isStringCodified(String chromosome, byte[] bytes, int offset, int length) {
            // Compare only the first letters to run a "startsWith"
            byte[] startsWith = Bytes.toBytes(chromosome + ':');
            int compareLength = startsWith.length;
            if (length > compareLength
                    && Bytes.compareTo(bytes, offset, compareLength, startsWith, 0, compareLength) == 0) {
                List<String> values = split(bytes, offset, Math.min(length, 20));
                String[] split = values.get(0).split(":");
                if (split.length > 1 && StringUtils.isNumeric(split[1])) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class CountSampleIndexVariantIterator extends SampleIndexVariantIterator {

        private final int count;
        private int i;
        private static final Variant DUMMY_VARIANT = new Variant("1:10:A:T");

        CountSampleIndexVariantIterator(int count) {
            this.count = count;
            i = 0;
        }

        @Override
        public int nextIndex() {
            return i;
        }

        @Override
        public boolean hasNext() {
            return i != count;
        }

        @Override
        public void skip() {
            increaseNonIntergenicCounter();
            i++;
        }

        @Override
        public Variant next() {
            increaseNonIntergenicCounter();
            i++;
            return DUMMY_VARIANT;
        }

        @Override
        public int getApproxSize() {
            return count;
        }
    }

    private class ByteSampleIndexVariantIterator extends SampleIndexVariantIterator {
        private final String chromosome;
        private final int batchStart;
        private final byte[] bytes;
        private final int length;
        private final int offset;
        private int currentOffset;

        private int idx;

        private boolean hasNext;
        private boolean encodedRefAlt;
        private int variantLength;
        private int referenceLength;
        private int alternateLength;

        ByteSampleIndexVariantIterator(String chromosome, int batchStart, byte[] bytes, int offset, int length) {
            this.chromosome = chromosome;
            this.batchStart = batchStart;
            this.bytes = bytes;
            this.offset = offset;
            this.length = length;
            this.currentOffset = offset;

            this.idx = -1;
            movePointer();
        }

        @Override
        public int nextIndex() {
            return idx;
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public Variant next() {
            increaseNonIntergenicCounter();
            Variant variant;
            if (encodedRefAlt) {
                variant = toVariantEncodedAlleles(chromosome, batchStart, bytes, currentOffset);
            } else {
                variant = toVariant(chromosome, batchStart, bytes, currentOffset, referenceLength, alternateLength);
            }
            movePointer();
            return variant;
        }

        @Override
        public void skip() {
            increaseNonIntergenicCounter();
            movePointer();
        }

        @Override
        public int getApproxSize() {
            double expectedVariantSize = 8.0;
            double approximation = 1.2;
            return (int) (length / expectedVariantSize * approximation);
        }

        private void movePointer() {
            currentOffset += variantLength;

            if (length - (currentOffset - offset) >= (INT24_LENGTH)) {
                hasNext = true;
                idx++;
                if (hasEncodedAlleles(bytes, currentOffset)) {
                    encodedRefAlt = true;
                    variantLength = INT24_LENGTH;
                } else {
                    encodedRefAlt = false;
                    referenceLength = readNextSeparator(bytes, currentOffset + INT24_LENGTH);
                    alternateLength = readNextSeparator(bytes, currentOffset + INT24_LENGTH + referenceLength + SEPARATOR_LENGTH);
                    variantLength = INT24_LENGTH + referenceLength + SEPARATOR_LENGTH + alternateLength + SEPARATOR_LENGTH;
                }
            } else {
                hasNext = false;
                referenceLength = 0;
                alternateLength = 0;
                variantLength = 0;
            }
        }

    }

    private static boolean hasEncodedAlleles(byte[] bytes, int offset) {
        return (bytes[offset] & 0xF0) != 0;
    }

    private Variant toVariantEncodedAlleles(String chromosome, int batchStart, byte[] bytes, int offset) {
        String[] refAlt = AlleleCodec.decode(bytes[offset]);
        int start = batchStart + (read24bitInteger(bytes, offset) & 0x0F_FF_FF);

        return VariantPhoenixKeyFactory.buildVariant(chromosome, start, refAlt[0], refAlt[1], null);
    }

    private Variant toVariant(String chromosome, int batchStart, byte[] bytes, int offset, int referenceLength, int alternateLength) {
        int start = batchStart + read24bitInteger(bytes, offset);
        offset += INT24_LENGTH;
        String reference = readString(bytes, offset, referenceLength);
        offset += referenceLength + SEPARATOR_LENGTH; // add reference, and separator
        String alternate = readString(bytes, offset, alternateLength);

        return VariantPhoenixKeyFactory.buildVariant(chromosome, start, reference, alternate, null);
    }

    private int readNextSeparator(byte[] bytes, int offset) {
        for (int i = offset; i < bytes.length; i++) {
            if (bytes[i] == 0) {
                return i - offset;
            }
        }
        return bytes.length - offset;
    }

    protected int getRelativeStart(Variant variant) {
        return variant.getStart() % SampleIndexSchema.BATCH_SIZE;
    }

    protected String getAlternate(Variant v) {
        return VariantPhoenixKeyFactory.buildSymbolicAlternate(v.getReference(), v.getAlternate(), v.getEnd(), v.getSv());
    }

    /**
     * Append a 24bit Integer.
     *
     * @param n      the integer to serialize.
     * @param bytes  the byte array into which to put the serialized form of object
     * @param offset the offset from which to start writing the serialized form
     * @return the byte length of the serialized object
     */
    protected int append24bitInteger(int n, byte[] bytes, int offset) {
        bytes[offset + 2] = (byte) n;
        n >>>= 8;
        bytes[offset + 1] = (byte) n;
        n >>>= 8;
        bytes[offset] = (byte) n;

        return INT24_LENGTH;
    }

    protected int read24bitInteger(byte[] bytes, int offset) {
        int n = bytes[offset] & 255;
        n <<= 8;
        n ^= bytes[offset + 1] & 255;
        n <<= 8;
        n ^= bytes[offset + 2] & 255;

        return n;
    }

    /**
     * Serialize string.
     *
     * @param str    The string to serialize
     * @param bytes  the byte array into which to put the serialized form of object
     * @param offset the offset from which to start writing the serialized form
     * @return the byte length of the serialized object
     */
    protected int appendString(String str, byte[] bytes, int offset) {
        return PVarchar.INSTANCE.toBytes(str, bytes, offset);
    }

    protected String readString(byte[] bytes, int offset, int length) {
        return (String) PVarchar.INSTANCE.toObject(bytes, offset, length);
    }

    /**
     * Append a separator.
     *
     * @param bytes  the byte array into which to put the serialized form of object
     * @param offset the offset from which to start writing the serialized form
     * @return the byte length of the serialized object
     */
    protected int appendSeparator(byte[] bytes, int offset) {
        bytes[offset] = BYTE_SEPARATOR;
        return SEPARATOR_LENGTH;
    }

    public static List<String> split(byte[] value) {
        return split(value, 0, value.length);
    }

    public static List<String> split(byte[] value, int offset, int length) {
        List<String> values = new ArrayList<>(length / 10);
        int valueOffset = offset;
        for (int i = offset; i < length + offset; i++) {
            if (value[i] == STRING_SEPARATOR) {
                if (i != valueOffset) { // Skip empty values
                    values.add(Bytes.toString(value, valueOffset, i - valueOffset));
                }
                valueOffset = i + 1;
            }
        }
        if (length + offset != valueOffset) { // Skip empty values
            values.add(Bytes.toString(value, valueOffset, length + offset - valueOffset));
        }
        return values;
    }
}
