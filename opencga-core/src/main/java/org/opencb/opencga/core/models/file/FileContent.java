package org.opencb.opencga.core.models.file;

public class FileContent {

    /**
     * File id.
     */
    private String fileId;

    /**
     * Flag indicating whether the content has reached the end of file.
     */
    private boolean eof;

    /**
     * Final byte of the file read.
     */
    private long offset;

    /**
     * Number of bytes returned.
     */
    private int bytes;

    /**
     * Number of lines skipped before starting reading.
     */
    private int skippedLines;

    /**
     * Number of lines read.
     */
    private int lines;

    /**
     * Partial or full content of the file.
     */
    private String content;

    public FileContent() {
    }

    public FileContent(String fileId, boolean eof, long offset, int bytes, int skippedLines, int lines, String content) {
        this.fileId = fileId;
        this.eof = eof;
        this.offset = offset;
        this.bytes = bytes;
        this.skippedLines = skippedLines;
        this.lines = lines;
        this.content = content;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("FileContent{");
        sb.append("fileId='").append(fileId).append('\'');
        sb.append(", eof=").append(eof);
        sb.append(", offset=").append(offset);
        sb.append(", bytes=").append(bytes);
        sb.append(", skippedLines=").append(skippedLines);
        sb.append(", lines=").append(lines);
        sb.append(", content='").append(content).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public String getFileId() {
        return fileId;
    }

    public FileContent setFileId(String fileId) {
        this.fileId = fileId;
        return this;
    }

    public boolean isEof() {
        return eof;
    }

    public FileContent setEof(boolean eof) {
        this.eof = eof;
        return this;
    }

    public long getOffset() {
        return offset;
    }

    public FileContent setOffset(long offset) {
        this.offset = offset;
        return this;
    }

    public int getBytes() {
        return bytes;
    }

    public FileContent setBytes(int bytes) {
        this.bytes = bytes;
        return this;
    }

    public int getSkippedLines() {
        return skippedLines;
    }

    public FileContent setSkippedLines(int skippedLines) {
        this.skippedLines = skippedLines;
        return this;
    }

    public int getLines() {
        return lines;
    }

    public FileContent setLines(int lines) {
        this.lines = lines;
        return this;
    }

    public String getContent() {
        return content;
    }

    public FileContent setContent(String content) {
        this.content = content;
        return this;
    }
}
