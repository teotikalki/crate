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

package io.crate.analyze;

import io.crate.sql.parser.SqlParser;
import io.crate.sql.tree.Statement;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(value = Scope.Benchmark)
@Fork(3)
@Warmup(iterations = 10, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 100, timeUnit = TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
public class ParserBenchmark {

    @Benchmark
    public Statement benchCreateTable() throws Exception {
        return SqlParser.createStatement("create table t (" +
            "  \"_i\" integer, " +
            "  \"in\" int," +
            "  \"Name\" string, " +
            "  bo boolean," +
            "  \"by\" byte," +
            "  sh short," +
            "  lo long," +
            "  fl float," +
            "  do double," +
            "  \"ip_\" ip," +
            "  ti timestamp," +
            "  ob object," +
            " \"day\" GENERATED ALWAYS AS date_trunc('day', ti)" +
            ") partitioned by (\"day\") clustered by (lo) into 999 shards with (number_of_replicas=4)");
    }

    @Benchmark
    public Statement benchInsert() throws Exception {
        return SqlParser.createStatement("insert into t (a, b)" +
            " values (1, 2), (3, 4)" +
            " on duplicate key update a = values (a) + 1, b = values(b) - 2");
    }

    @Benchmark
    public Statement benchJoin() throws Exception {
        return SqlParser.createStatement("select * from foo, bar" +
            " where foo.id = bar.id" +
            " and foo.a=foo.b and a is not null");
    }

    @Benchmark
    public Statement benchSubselect() throws Exception {
        return SqlParser.createStatement("select ab" +
            " from (select (ii + y) as iiy, concat(a, b) as ab" +
            " from (select t1.a, t2.b, t2.y, (t1.i + t2.i) as ii " +
            " from t1, t2 where t1.a='a' or t2.b='aa') as t) as tt order by iiy");
    }

    @Benchmark
    public Statement benchSubscript() throws Exception {
        return SqlParser.createStatement("select abc['a'], abc['b'], abc['c'] from t" +
            " where 'value' LIKE ANY (col)" +
            " and abc['a']['b']['c'] > (1+2)");
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(ParserBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build();
        new Runner(opt).run();
    }
}
