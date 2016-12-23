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

package io.crate.jobs.transport;

import io.crate.exceptions.ContextMissingException;
import io.crate.executor.transport.kill.KillJobsRequest;
import io.crate.executor.transport.kill.KillResponse;
import io.crate.executor.transport.kill.TransportKillAllNodeAction;
import io.crate.executor.transport.kill.TransportKillJobsNodeAction;
import io.crate.jobs.DummySubContext;
import io.crate.jobs.JobContextService;
import io.crate.jobs.JobExecutionContext;
import io.crate.operation.collect.StatsTables;
import io.crate.test.integration.CrateUnitTest;
import org.elasticsearch.Version;
import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.test.cluster.NoopClusterService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.Transport;
import org.elasticsearch.transport.TransportService;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class NodeDisconnectJobMonitorServiceTest extends CrateUnitTest {

    @Test
    public void testOnNodeDisconnectedKillsJobOriginatingFromThatNode() throws Exception {
        JobContextService jobContextService = new JobContextService(
            Settings.EMPTY, new NoopClusterService(), mock(StatsTables.class));

        JobExecutionContext.Builder builder = jobContextService.newBuilder(UUID.randomUUID());
        builder.addSubContext(new DummySubContext());
        JobExecutionContext context = jobContextService.createContext(builder);

        ThreadPool threadPool = mock(ThreadPool.class);
        when(threadPool.schedule(any(TimeValue.class), anyString(), any(Runnable.class))).thenAnswer((Answer<Object>) invocation -> {
            ((Runnable) invocation.getArguments()[2]).run();
            return null;
        });

        NodeDisconnectJobMonitorService monitorService = new NodeDisconnectJobMonitorService(
            Settings.EMPTY,
            threadPool,
            jobContextService,
            mock(TransportService.class),
            mock(TransportKillJobsNodeAction.class),
            mock(DiscoveryNodes.class));

        monitorService.onNodeDisconnected(new DiscoveryNode("noop_id", DummyTransportAddress.INSTANCE, Version.CURRENT));

        expectedException.expect(ContextMissingException.class);
        jobContextService.getContext(context.jobId());
    }

    @Test
    public void testOnParticipatingNodeDisconnectedKillsJob() throws Exception {
        JobContextService jobContextService = new JobContextService(
            Settings.EMPTY, new NoopClusterService(), mock(StatsTables.class));

        DiscoveryNodes discoveryNodes = DiscoveryNodes.builder()
            .localNodeId("coordinator_node_id")
            .put(new DiscoveryNode("coordinator_node_id", DummyTransportAddress.INSTANCE, Version.CURRENT))
            .put(new DiscoveryNode("data_node_1", DummyTransportAddress.INSTANCE, Version.CURRENT))
            .put(new DiscoveryNode("data_node_2", DummyTransportAddress.INSTANCE, Version.CURRENT)).build();

        JobExecutionContext.Builder builder = jobContextService.newBuilder(UUID.randomUUID(), discoveryNodes.getLocalNodeId(), Arrays.asList(discoveryNodes.getLocalNodeId(), "data_node_1", "data_node_2"));
        builder.addSubContext(new DummySubContext());
        JobExecutionContext context = jobContextService.createContext(builder);
        TransportKillJobsNodeAction killAction = mock(TransportKillJobsNodeAction.class);

        NodeDisconnectJobMonitorService monitorService = new NodeDisconnectJobMonitorService(
            Settings.EMPTY,
            mock(ThreadPool.class),
            jobContextService,
            mock(TransportService.class),
            killAction,
            discoveryNodes);

        monitorService.onNodeDisconnected(discoveryNodes.get("data_node_1"));
        // verify(killAction, times(1)).broadcast(any(KillJobsRequest.class), any(ActionListener.class));
        expectedException.expect(ContextMissingException.class);
        jobContextService.getContext(context.jobId());
    }
}
