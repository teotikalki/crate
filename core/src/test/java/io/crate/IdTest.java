/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.util.ArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;

public class IdTest {

    @Test
    public void testAutoGenerated() throws Exception {
        Id id = new Id(ImmutableList.of("_id"), ImmutableList.<String>of(), "_id");

        assertThat(id.values().size(), is(1));
        assertThat(id.stringValue(), is(id.values().get(0)));
    }

    @Test
    public void testAutoGeneratedWithRouting() throws Exception {
        Id id = new Id(ImmutableList.of("_id"), ImmutableList.<String>of(), "foo");

        assertThat(id.values().size(), is(1));
        assertThat(id.stringValue(), is(id.values().get(0)));
    }

    @Test
    public void testSinglePrimaryKey() throws Exception {
        Id id = new Id(ImmutableList.of("id"), ImmutableList.of("1"), "id");

        assertThat(id.values().size(), is(1));
        assertThat(id.stringValue(), is(id.values().get(0)));
    }

    @Test (expected = UnsupportedOperationException.class)
    public void testSinglePrimaryKeyWithoutValue() throws Exception {
        Id id = new Id(ImmutableList.of("id"), ImmutableList.<String>of(), "id");
    }

    @Test
    public void testMultiplePrimaryKey() throws Exception {
        Id id = new Id(ImmutableList.of("id", "name"), ImmutableList.of("1", "foo"), null);

        assertThat(id.values().size(), is(2));
        assertEquals(ImmutableList.of("1", "foo"), id.values());

        Id id1 = Id.fromString(id.stringValue());
        assertEquals(id.values(), id1.values());
    }

    @Test
    public void testMultiplePrimaryKeyWithClusteredBy() throws Exception {
        Id id = new Id(ImmutableList.of("id", "name"), ImmutableList.of("1", "foo"), "name");

        assertThat(id.values().size(), is(2));
        assertEquals(ImmutableList.of("foo", "1"), id.values());

        Id id1 = Id.fromString(id.stringValue());
        assertEquals(id.values(), id1.values());
    }

    @Test
    public void testNull() throws Exception {
        Id id = new Id(ImmutableList.of("id"), new ArrayList<String>(){{add(null);}}, "id");

        assertThat(id.values().size(), is(0));
        assertEquals(null, id.stringValue());
    }

}
