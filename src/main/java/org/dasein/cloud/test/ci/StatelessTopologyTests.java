/**
 * Copyright (C) 2009-2016 Dell, Inc.
 * See annotations for authorship information
 * <p>
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.test.ci;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ci.ConvergedInfrastructureServices;
import org.dasein.cloud.ci.Topology;
import org.dasein.cloud.ci.TopologySupport;
import org.dasein.cloud.test.DaseinTestManager;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import javax.annotation.Nonnull;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Tests support for Dasein Cloud topologies which represent complex, multi-resource templates that can be provisioned into
 * running resources.
 * <p>Created by George Reese: 5/31/13 10:57 AM</p>
 * @author George Reese
 * @version 2013.07 initial version
 * @since 2013.07
 */
public class StatelessTopologyTests {
    static private DaseinTestManager tm;

    @BeforeClass
    static public void configure() {
        tm = new DaseinTestManager(StatelessTopologyTests.class);
    }

    @AfterClass
    static public void cleanUp() {
        if( tm != null ) {
            tm.close();
        }
    }

    @Rule
    public final TestName name = new TestName();

    private String testTopologyId;

    public StatelessTopologyTests() { }

    @Before
    public void before() {
        tm.begin(name.getMethodName());
        assumeTrue(!tm.isTestSkipped());
        testTopologyId = tm.getTestTopologyId(DaseinTestManager.STATELESS, false);
    }

    @After
    public void after() {
        tm.end();
    }

    private void assertTopology(@Nonnull Topology topology) {
        assertNotNull("Topology ID may not be null", topology.getProviderTopologyId());
        assertNotNull("Topology name may not be null", topology.getName());
        assertNotNull("Topology description may not be null", topology.getDescription());
        assertTrue("Topology creation timestamp may not be negative", topology.getCreationTimestamp() >= 0L);
        assertNotNull("Topology tags may not be null", topology.getTags());
        assertNotNull("Owner ID may not be null", topology.getProviderOwnerId());
    }

    @Test
    public void getBogusTopology() throws CloudException, InternalException {
        ConvergedInfrastructureServices services = tm.getProvider().getConvergedInfrastructureServices();

        if( services != null ) {
            TopologySupport support = services.getTopologySupport();

            if( support != null ) {
                Topology t = support.getTopology(UUID.randomUUID().toString());

                tm.out("Bogus Topology", t);
                assertNull("Bogus topology was supposed to be none, but got a valid topology.", t);
            }
            else {
                tm.ok("No topology support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }

    @Test
    public void getTopology() throws CloudException, InternalException {
        ConvergedInfrastructureServices services = tm.getProvider().getConvergedInfrastructureServices();

        if( services != null ) {
            TopologySupport support = services.getTopologySupport();

            if( support != null ) {
                if( testTopologyId != null ) {
                    Topology t = support.getTopology(testTopologyId);

                    tm.out("Topology", t);
                    assertNotNull("Failed to find the test topology among possible images", t);
                }
                else {
                    if( !support.isSubscribed() ) {
                        tm.warn("No topology ID was identified, so this test is not valid");
                    }
                    else {
                        fail("No test topology exists for the getTopology test");
                    }
                }
            }
            else {
                tm.ok("No topology support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }

    @Test
    public void topologyContent() throws CloudException, InternalException {
        ConvergedInfrastructureServices services = tm.getProvider().getConvergedInfrastructureServices();

        if( services != null ) {
            TopologySupport support = services.getTopologySupport();

            if( support != null ) {
                if( testTopologyId != null ) {
                    Topology t = support.getTopology(testTopologyId);

                    assertNotNull("Failed to find the test topology among possible topologies", t);
                    tm.out("Topology ID", t.getProviderTopologyId());
                    tm.out("Name", t.getName());
                    tm.out("Created", new Date(t.getCreationTimestamp()));
                    tm.out("Owner Account", t.getProviderOwnerId());
                    tm.out("Region ID", t.getProviderRegionId());
                    tm.out("Data Center ID", t.getProviderDataCenterId());

                    Map<String,String> tags = t.getTags();

                    //noinspection ConstantConditions
                    if( tags != null ) {
                        for( Map.Entry<String,String> entry : tags.entrySet() ) {
                            tm.out("Tag " + entry.getKey(), entry.getValue());
                        }
                    }

                    tm.out("Description", t.getDescription());

                    assertTopology(t);

                }
                else {
                    if( !support.isSubscribed() ) {
                        tm.warn("No topology ID was identified, so this test is not valid");
                    }
                    else {
                        fail("No test topology exists for the topologyContent test");
                    }
                }
            }
            else {
                tm.ok("No topology support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }

    @Test
    public void listPrivateTopologies() throws CloudException, InternalException {
        ConvergedInfrastructureServices services = tm.getProvider().getConvergedInfrastructureServices();

        if( services != null ) {
            TopologySupport support = services.getTopologySupport();

            if( support != null ) {
                Iterable<Topology> topologies = support.listTopologies(null);
                int count = 0;

                assertNotNull("listTopologies() must return a non-null list of topologies even if a private library is not supported", topologies);
                for( Topology t : topologies ) {
                    count++;
                    tm.out("Topology", t);
                }
                tm.out("Total Topology Count", count);
                for( Topology t : topologies ) {
                    assertTopology(t);
                }
                if( count < 1 ) {
                    tm.warn("No topologies were provided so this test may not be valid");
                }
            }
            else {
                tm.ok("No topology support in this cloud");
            }
        }
        else {
            tm.ok("No compute services in this cloud");
        }
    }
}