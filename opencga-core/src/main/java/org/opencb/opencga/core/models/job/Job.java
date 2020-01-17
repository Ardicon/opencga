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

package org.opencb.opencga.core.models.job;

import org.opencb.opencga.core.models.PrivateStudyUid;
import org.opencb.opencga.core.models.common.Enums;
import org.opencb.opencga.core.models.file.File;
import org.opencb.opencga.core.tools.result.ExecutionResult;

import java.util.List;
import java.util.Map;

/**
 * Created by jacobo on 11/09/14.
 */
public class Job extends PrivateStudyUid {

    private String id;
    private String uuid;
    private String name;
    private String description;

    private ToolInfo tool;

    private String userId;
    private String commandLine;

    private Map<String, Object> params;

    private String creationDate;
    private String modificationDate;

    private Enums.Priority priority;
    private Enums.ExecutionStatus status;

    private File outDir;
    private List<File> input;    // input files to this job
    private List<File> output;   // output files of this job
    private List<String> tags;

    private ExecutionResult execution;

    private File stdout;
    private File stderr;

    private boolean visited;

    private int release;
    private String studyUuid;
    private Map<String, Object> attributes;

    public static final String OPENCGA_PARENTS = "OPENCGA_PARENTS";

    public Job() {
    }

    public Job(String id, String uuid, String name, String description, ToolInfo tool, String userId, String commandLine,
               Map<String, Object> params, String creationDate, String modificationDate, Enums.Priority priority,
               Enums.ExecutionStatus status, File outDir, List<File> input, List<File> output, List<String> tags, ExecutionResult execution,
               boolean visited, File stdout, File stderr, int release, String studyUuid, Map<String, Object> attributes) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
        this.tool = tool;
        this.description = description;
        this.userId = userId;
        this.commandLine = commandLine;
        this.params = params;
        this.creationDate = creationDate;
        this.modificationDate = modificationDate;
        this.priority = priority;
        this.status = status;
        this.outDir = outDir;
        this.input = input;
        this.output = output;
        this.tags = tags;
        this.execution = execution;
        this.visited = visited;
        this.stdout = stdout;
        this.stderr = stderr;
        this.release = release;
        this.studyUuid = studyUuid;
        this.attributes = attributes;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Job{");
        sb.append("id='").append(id).append('\'');
        sb.append(", uuid='").append(uuid).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", tool='").append(tool).append('\'');
        sb.append(", userId='").append(userId).append('\'');
        sb.append(", commandLine='").append(commandLine).append('\'');
        sb.append(", params=").append(params);
        sb.append(", creationDate='").append(creationDate).append('\'');
        sb.append(", modificationDate='").append(modificationDate).append('\'');
        sb.append(", priority='").append(priority).append('\'');
        sb.append(", status=").append(status);
        sb.append(", outDir=").append(outDir);
        sb.append(", input=").append(input);
        sb.append(", output=").append(output);
        sb.append(", tags=").append(tags);
        sb.append(", execution=").append(execution);
        sb.append(", visited=").append(visited);
        sb.append(", stdout=").append(stdout);
        sb.append(", stderr=").append(stderr);
        sb.append(", release=").append(release);
        sb.append(", studyUuid=").append(studyUuid);
        sb.append(", attributes=").append(attributes);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public Job setStudyUid(long studyUid) {
        super.setStudyUid(studyUid);
        return this;
    }

    @Override
    public Job setUid(long uid) {
        super.setUid(uid);
        return this;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Job setId(String id) {
        this.id = id;
        return this;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    public Job setUuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    public String getName() {
        return name;
    }

    public Job setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Job setDescription(String description) {
        this.description = description;
        return this;
    }

    public ToolInfo getTool() {
        return tool;
    }

    public Job setTool(ToolInfo tool) {
        this.tool = tool;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public Job setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public Job setCommandLine(String commandLine) {
        this.commandLine = commandLine;
        return this;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Job setParams(Map<String, Object> params) {
        this.params = params;
        return this;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public Job setCreationDate(String creationDate) {
        this.creationDate = creationDate;
        return this;
    }

    public String getModificationDate() {
        return modificationDate;
    }

    public Job setModificationDate(String modificationDate) {
        this.modificationDate = modificationDate;
        return this;
    }

    public Enums.Priority getPriority() {
        return priority;
    }

    public Job setPriority(Enums.Priority priority) {
        this.priority = priority;
        return this;
    }

    public Enums.ExecutionStatus getStatus() {
        return status;
    }

    public Job setStatus(Enums.ExecutionStatus status) {
        this.status = status;
        return this;
    }

    public File getOutDir() {
        return outDir;
    }

    public Job setOutDir(File outDir) {
        this.outDir = outDir;
        return this;
    }

    public List<File> getInput() {
        return input;
    }

    public Job setInput(List<File> input) {
        this.input = input;
        return this;
    }

    public List<File> getOutput() {
        return output;
    }

    public Job setOutput(List<File> output) {
        this.output = output;
        return this;
    }

    public List<String> getTags() {
        return tags;
    }

    public Job setTags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    public boolean isVisited() {
        return visited;
    }

    public Job setVisited(boolean visited) {
        this.visited = visited;
        return this;
    }

    public ExecutionResult getExecution() {
        return execution;
    }

    public Job setExecution(ExecutionResult execution) {
        this.execution = execution;
        return this;
    }

    public File getStdout() {
        return stdout;
    }

    public Job setStdout(File stdout) {
        this.stdout = stdout;
        return this;
    }

    public File getStderr() {
        return stderr;
    }

    public Job setStderr(File stderr) {
        this.stderr = stderr;
        return this;
    }

    public int getRelease() {
        return release;
    }

    public Job setRelease(int release) {
        this.release = release;
        return this;
    }

    public String getStudyUuid() {
        return studyUuid;
    }

    public Job setStudyUuid(String studyUuid) {
        this.studyUuid = studyUuid;
        return this;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Job setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
        return this;
    }
}
