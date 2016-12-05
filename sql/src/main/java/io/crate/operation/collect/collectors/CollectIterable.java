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

import com.google.common.base.Throwables;
import io.crate.core.collections.Row;
import io.crate.core.collections.Row1;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.*;

import java.io.IOException;
import java.util.Iterator;

public class CollectIterable implements Iterable<Row> {

    private final IndexSearcher searcher;
    private final Query query;

    public CollectIterable(IndexSearcher searcher, Query query) {
        this.searcher = searcher;
        this.query = query;
    }

    @Override
    public Iterator<Row> iterator() {
        Weight weight;
        try {
            weight = searcher.createNormalizedWeight(query, false);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        Iterator<LeafReaderContext> leavesIt = searcher.getTopReaderContext().leaves().iterator();

        return new Iterator<Row>() {
            int doc;
            DocIdSetIterator docIdSetIterator;

            @Override
            public boolean hasNext() {
                while (leavesIt.hasNext()) {
                    LeafReaderContext readerContext = leavesIt.next();
                    Scorer scorer;
                    try {
                        scorer = weight.scorer(readerContext);
                    } catch (IOException e) {
                        throw Throwables.propagate(e);
                    }
                    if (scorer == null) {
                        continue;
                    }
                    docIdSetIterator = scorer.iterator();
                    break;
                }
                if (docIdSetIterator == null) {
                    return false;
                }
                try {
                    doc = docIdSetIterator.nextDoc();
                } catch (IOException e) {
                    throw Throwables.propagate(e);
                }
                return doc != DocIdSetIterator.NO_MORE_DOCS;
            }

            @Override
            public Row next() {
                return new Row1(doc);
            }
        };
    }
}
