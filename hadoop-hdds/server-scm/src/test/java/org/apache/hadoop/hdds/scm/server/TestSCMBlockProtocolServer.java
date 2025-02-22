/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdds.scm.server;

import org.apache.hadoop.hdds.HddsConfigKeys;
import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.hdds.protocol.DatanodeDetails;
import org.apache.hadoop.hdds.protocol.proto.ScmBlockLocationProtocolProtos;
import org.apache.hadoop.hdds.scm.HddsTestUtils;
import org.apache.hadoop.hdds.scm.ha.SCMHAManagerStub;
import org.apache.hadoop.hdds.scm.ha.SCMContext;
import org.apache.hadoop.hdds.scm.node.NodeManager;
import org.apache.hadoop.hdds.utils.ProtocolMessageMetrics;
import org.apache.hadoop.hdds.scm.protocol.ScmBlockLocationProtocolServerSideTranslatorPB;
import org.apache.hadoop.ozone.ClientVersion;
import org.apache.ozone.test.GenericTestUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.apache.hadoop.hdds.protocol.MockDatanodeDetails.randomDatanodeDetails;

/**
 * Test class for @{@link SCMBlockProtocolServer}.
 */
public class TestSCMBlockProtocolServer {
  private OzoneConfiguration config;
  private SCMBlockProtocolServer server;
  private StorageContainerManager scm;
  private NodeManager nodeManager;
  private ScmBlockLocationProtocolServerSideTranslatorPB service;
  private static final int NODE_COUNT = 10;

  @Before
  public void setUp() throws Exception {
    config = new OzoneConfiguration();
    File dir = GenericTestUtils.getRandomizedTestDir();
    config.set(HddsConfigKeys.OZONE_METADATA_DIRS, dir.toString());
    SCMConfigurator configurator = new SCMConfigurator();
    configurator.setSCMHAManager(SCMHAManagerStub.getInstance(true));
    configurator.setScmContext(SCMContext.emptyContext());
    scm = HddsTestUtils.getScm(config, configurator);
    scm.start();
    scm.exitSafeMode();
    // add nodes to scm node manager
    nodeManager = scm.getScmNodeManager();
    for (int i = 0; i < NODE_COUNT; i++) {
      nodeManager.register(randomDatanodeDetails(), null, null);

    }
    server = scm.getBlockProtocolServer();
    service = new ScmBlockLocationProtocolServerSideTranslatorPB(server, scm,
        Mockito.mock(ProtocolMessageMetrics.class));
  }

  @After
  public void tearDown() throws Exception {
    if (scm != null) {
      scm.stop();
      scm.join();
    }
  }

  @Test
  public void testSortDatanodes() throws Exception {
    List<String> nodes = new ArrayList();
    nodeManager.getAllNodes().stream().forEach(
        node -> nodes.add(node.getNetworkName()));

    // sort normal datanodes
    String client;
    client = nodes.get(0);
    List<DatanodeDetails> datanodeDetails =
        server.sortDatanodes(nodes, client);
    System.out.println("client = " + client);
    datanodeDetails.stream().forEach(
        node -> System.out.println(node.toString()));
    Assert.assertTrue(datanodeDetails.size() == NODE_COUNT);

    // illegal client 1
    client += "X";
    datanodeDetails = server.sortDatanodes(nodes, client);
    System.out.println("client = " + client);
    datanodeDetails.stream().forEach(
        node -> System.out.println(node.toString()));
    Assert.assertTrue(datanodeDetails.size() == NODE_COUNT);
    // illegal client 2
    client = "/default-rack";
    datanodeDetails = server.sortDatanodes(nodes, client);
    System.out.println("client = " + client);
    datanodeDetails.stream().forEach(
        node -> System.out.println(node.toString()));
    Assert.assertTrue(datanodeDetails.size() == NODE_COUNT);

    // unknown node to sort
    nodes.add(UUID.randomUUID().toString());
    ScmBlockLocationProtocolProtos.SortDatanodesRequestProto request =
        ScmBlockLocationProtocolProtos.SortDatanodesRequestProto
            .newBuilder()
            .addAllNodeNetworkName(nodes)
            .setClient(client)
            .build();
    ScmBlockLocationProtocolProtos.SortDatanodesResponseProto resp =
        service.sortDatanodes(request, ClientVersion.CURRENT_VERSION);
    Assert.assertTrue(resp.getNodeList().size() == NODE_COUNT);
    System.out.println("client = " + client);
    resp.getNodeList().stream().forEach(
        node -> System.out.println(node.getNetworkName()));

    // all unknown nodes
    nodes.clear();
    nodes.add(UUID.randomUUID().toString());
    nodes.add(UUID.randomUUID().toString());
    nodes.add(UUID.randomUUID().toString());
    request = ScmBlockLocationProtocolProtos.SortDatanodesRequestProto
        .newBuilder()
        .addAllNodeNetworkName(nodes)
        .setClient(client)
        .build();
    resp = service.sortDatanodes(request, ClientVersion.CURRENT_VERSION);
    System.out.println("client = " + client);
    Assert.assertTrue(resp.getNodeList().size() == 0);
    resp.getNodeList().stream().forEach(
        node -> System.out.println(node.getNetworkName()));
  }
}