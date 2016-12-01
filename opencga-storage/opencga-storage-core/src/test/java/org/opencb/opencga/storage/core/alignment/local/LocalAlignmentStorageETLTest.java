/*
 * Copyright 2015-2016 OpenCB
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

package org.opencb.opencga.storage.core.alignment.local;

import org.junit.Test;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by imedina on 01/11/16.
 */
public class LocalAlignmentStorageETLTest {
    //@Test
    public void postTransform() throws Exception {
        Path inputPath = Paths.get(getClass().getResource("/HG00096.chrom20.small.bam").toURI());
//        String inputPath = "/tmp/kk/ebi.bam";
//        String inputPath = "/tmp/kk/HG00096.chrom20.small.bam";
        System.out.println("inputPath = " + inputPath);
        LocalAlignmentStorageETL storageETL = new LocalAlignmentStorageETL();
        storageETL.transform(inputPath.toUri(), null, inputPath.getParent().toUri());
    }
}