/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.operation.collect.collectors;

import io.crate.core.collections.Row;
import io.crate.testing.TestingHelpers;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Test;

public class CollectIterableTest {

    @Test
    public void testFoo() throws Exception {
        IndexWriter iw = new IndexWriter(new RAMDirectory(), new IndexWriterConfig(new StandardAnalyzer()));
        for (int i = 0; i < 10; i++) {
            Document doc = new Document();
            doc.add(new LongField("x", i, Field.Store.NO));
            iw.addDocument(doc);
        }
        DirectoryReader directoryReader = DirectoryReader.open(iw, true);
        IndexSearcher indexSearcher = new IndexSearcher(directoryReader);
        CollectIterable collectIterable = new CollectIterable(indexSearcher, new MatchAllDocsQuery());

        for (Row row : collectIterable) {
            System.out.println(row);
        }
    }
}
