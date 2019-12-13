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

package org.opencb.opencga.catalog.db.mongodb.converters;

import org.bson.Document;
import org.opencb.commons.datastore.mongodb.GenericDocumentComplexConverter;
import org.opencb.opencga.catalog.db.api.JobDBAdaptor;
import org.opencb.opencga.core.models.File;
import org.opencb.opencga.core.models.Job;

import java.util.*;

/**
 * Created by pfurio on 19/01/16.
 */
public class JobConverter extends GenericDocumentComplexConverter<Job> {

    public JobConverter() {
        super(Job.class);
    }

    @Override
    public Document convertToStorageType(Job object) {
        Document document = super.convertToStorageType(object);
        document.put(JobDBAdaptor.QueryParams.UID.key(), object.getUid());
        document.put(JobDBAdaptor.QueryParams.STUDY_UID.key(), object.getStudyUid());
        document.put(JobDBAdaptor.QueryParams.OUT_DIR.key(), convertFileToDocument(object.getOutDir()));
//        document.put(JobDBAdaptor.QueryParams.TMP_DIR.key(), convertFileToDocument(object.getTmpDir()));
        document.put(JobDBAdaptor.QueryParams.INPUT.key(), convertFilesToDocument(object.getInput()));
        document.put(JobDBAdaptor.QueryParams.OUTPUT.key(), convertFilesToDocument(object.getOutput()));
        document.put(JobDBAdaptor.QueryParams.STDOUT.key(), convertFileToDocument(object.getStdout()));
        document.put(JobDBAdaptor.QueryParams.STDERR.key(), convertFileToDocument(object.getStderr()));
        return document;
    }

    public Document convertFileToDocument(Object file) {
        if (file == null) {
            return new Document(JobDBAdaptor.QueryParams.UID.key(), -1L);
        }
        if (file instanceof File) {
            return new Document(JobDBAdaptor.QueryParams.UID.key(), ((File) file).getUid());
        } else if (file instanceof Map) {
            return new Document(JobDBAdaptor.QueryParams.UID.key(), ((Map) file).get(JobDBAdaptor.QueryParams.UID.key()));
        } else {
            return new Document(JobDBAdaptor.QueryParams.UID.key(), -1L);
        }
    }

    public List<Document> convertFilesToDocument(Object fileList) {
        if (!(fileList instanceof Collection)) {
            return Collections.emptyList();
        }
        List<Object> myList = (List<Object>) fileList;
        if (myList.isEmpty()) {
            return Collections.emptyList();
        }
        List<Document> files = new ArrayList(myList.size());
        for (Object file : myList) {
            files.add(convertFileToDocument(file));
        }
        return files;
    }
}
