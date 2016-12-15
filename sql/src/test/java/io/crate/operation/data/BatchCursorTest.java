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

package io.crate.operation.data;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.crate.core.collections.Row;
import io.crate.core.collections.RowN;
import io.crate.operation.collect.CrateCollector;
import org.junit.Test;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class BatchCursorTest {


    static abstract class BaseBatchConsumer implements BatchConsumer {
        protected  BatchCursor cursor;

        private void consume() {
            if (cursor.status() == BatchCursor.Status.ON_ROW) {
                do {
                    handleRow();
                } while (cursor.moveNext());
            }
            if (!cursor.allLoaded()) {
                cursor.loadNextBatch().addListener(this::consume, MoreExecutors.directExecutor());
            } else {
                cursor.close();
            }
        }

        protected abstract void handleRow();

        @Override
        public void accept(BatchCursor batchCursor) {
            this.cursor = batchCursor;
            consume();
        }
    }


    static class BatchPrinter extends BaseBatchConsumer {
        protected void handleRow() {
            System.out.println("row: " + Arrays.toString(cursor.materialize()));
        }
    }

    static class BatchCollector extends BaseBatchConsumer {

        private final List<Row> rows = new ArrayList<>();

        @Override
        protected void handleRow() {
            rows.add(new RowN(cursor.materialize()));
        }
    }

    static class DataCursor implements BatchCursor {

        private final Object[][] data;
        private final int rowSize;
        private int idx = 0;

        DataCursor(Object[][] data, int rowSize) {
            this.data = data;
            this.rowSize = rowSize;
        }


        @Override
        public boolean moveFirst() {
            return data.length > (idx = 0);
        }

        @Override
        public boolean moveNext() {
            return data.length > ++idx;
        }

        @Override
        public void close() {
            idx = -1;
        }

        @Override
        public Status status() {
            if (idx >= data.length) {
                return Status.OFF_ROW;
            } else if (idx == -1) {
                return Status.CLOSED;
            }
            return Status.ON_ROW;
        }

        @Override
        public ListenableFuture<Void> loadNextBatch() {
            throw new IllegalStateException("loadNextBatch not allowed on loaded cursor");
        }

        @Override
        public boolean allLoaded() {
            return true;
        }

        @Override
        public int size() {
            return rowSize;
        }

        @Override
        public Object get(int index) {
            assert index <= rowSize;
            return data[idx][index];
        }

        @Override
        public Object[] materialize() {
            return Arrays.copyOf(data[idx], rowSize);
        }
    }

    static class SomeDataSource implements CrateCollector {

        private final BatchConsumer downstream;
        private final BatchCursor data;


        SomeDataSource(BatchConsumer downstream, BatchCursor data) {
            this.downstream = downstream;
            this.data = data;
        }

        @Override
        public void doCollect() {
            downstream.accept(data);
        }

        @Override
        public void kill(@Nullable Throwable throwable) {

        }
    }

    class LimitProjector implements BatchProjector {

        private final int limit;

        LimitProjector(int limit) {
            this.limit = limit;
        }

        @Override
        public BatchCursor apply(BatchCursor batchCursor) {
            return new Cursor(batchCursor);
        }

        private class Cursor extends BatchCursorProxy {

            private int pos = 1;
            private boolean limitReached = false;

            public Cursor(BatchCursor delegate) {
                super(delegate);
            }

            @Override
            public boolean moveNext() {
                assert pos <= limit || limit == 0: "pos run over limit";
                if (pos >= limit) {
                    limitReached = true;
                    return false;
                }
                pos++;
                return super.moveNext();
            }

            @Override
            public Status status() {
                Status s = super.status();
                // if the limit is reached and the upstream is still on a row return off row;
                if (s == Status.ON_ROW && limitReached) {
                    return Status.OFF_ROW;
                }
                return s;
            }

            @Override
            public ListenableFuture<?> loadNextBatch() {
                if (limitReached) {
                    throw new IllegalStateException("all data loaded already");
                }
                return super.loadNextBatch();
            }

            @Override
            public boolean moveFirst() {
                limitReached = limit == (pos = 1);
                return super.moveFirst();
            }

            @Override
            public boolean allLoaded() {
                return limitReached || super.allLoaded();
            }

            @Override
            public Object get(int index) {
                if (limitReached) {
                    throw new IllegalStateException("not on a row");
                }
                return super.get(index);
            }

            @Override
            public Object[] materialize() {
                if (limitReached) {
                    throw new IllegalStateException("not on a row");
                }
                return super.materialize();
            }
        }

    }


    @Test
    public void testLimitProjector() throws Exception {
        DataCursor d = new DataCursor(new Object[][]{{1, 2}, {3, 4}, {5, 6}, {7, 8}}, 2);
        BatchProjector lp = new LimitProjector(3);
        BatchCollector c = new BatchCollector();
        BatchConsumer chain = lp.andThen(c);
        SomeDataSource s = new SomeDataSource(chain, d);
        s.doCollect();

        assertThat(c.rows.size(), is(3));

    }

}
