/**
 * Copyright (C) 2009-2015 Dell, Inc.
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
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
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ci.ConvergedInfrastructureProvisionOptions;
import org.dasein.cloud.ci.ConvergedInfrastructureServices;
import org.dasein.cloud.ci.ConvergedInfrastructureSupport;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.ResourcePool;
import org.dasein.cloud.test.DaseinTestManager;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Tests support for Dasein Cloud CIs which represent complex, multi-resource groups.
 */
public class StatefulCITests {
    static private DaseinTestManager tm;
    private String testResourcePoolId = null;

    @BeforeClass
    static public void configure() {
        tm = new DaseinTestManager(StatefulCITests.class);
    }

    @AfterClass
    static public void cleanUp() {
        if( tm != null ) {
            tm.close();
        }
    }

    @Rule
    public final TestName name = new TestName();

    private String testCIId;
    private ConvergedInfrastructureProvisionOptions options;

    public StatefulCITests() { }

    @Before
    public void before() {
        tm.begin(name.getMethodName());
        assumeTrue(!tm.isTestSkipped());

        try {
            testResourcePoolId = null;
            if (tm.getProvider().getDataCenterServices().getCapabilities().supportsResourcePools()) {
                DataCenterServices dc = tm.getProvider().getDataCenterServices();

                Iterable<ResourcePool> testRPList = dc.listResourcePools(tm.getDefaultDataCenterId(true));
                if (testRPList != null && testRPList.iterator().hasNext()) {
                    ResourcePool rp = testRPList.iterator().next();
                    testResourcePoolId = rp.getProvideResourcePoolId();
                }
            }

            String testTemplateContent = "", testParametersContent = "";
            boolean supportsTemplateContent = false;
            CIResources ciResources = DaseinTestManager.getCiResources();
            testTemplateContent = ciResources.getTestTemplateContent();
            testParametersContent = ciResources.getTestParametersContent();
            ConvergedInfrastructureServices services = tm.getProvider().getConvergedInfrastructureServices();
            if ( services != null ) {
                ConvergedInfrastructureSupport support = services.getConvergedInfrastructureSupport();
                if ( support != null ) {
                    try {
                        Requirement templateContentLaunchRequirement = support.getCapabilities().identifyTemplateContentLaunchRequirement();
                        supportsTemplateContent = !templateContentLaunchRequirement.equals(Requirement.NONE);
                    } catch ( Exception e ) {}
                }
            }
            if ( testTemplateContent != null ) {
                options = ConvergedInfrastructureProvisionOptions.getInstance("dsn-ci" + System.currentTimeMillis(),
                        testResourcePoolId, null, testTemplateContent, testParametersContent, supportsTemplateContent);
            } else {
                tm.warn("Unable to find converged infrastructure template for testing. Test invalid");
                return;
            }

            if (name.getMethodName().startsWith("deleteCIFromTopology")) {
                if( tm.getProvider().getConvergedInfrastructureServices() != null && tm.getProvider().getConvergedInfrastructureServices().getConvergedInfrastructureSupport() != null ) {
                    testCIId = tm.getTestCIId(DaseinTestManager.REMOVED, true);
                }

            }
        } catch ( Exception e ) {
        }
    }

    @After
    public void after() {
        tm.end();
    }

    /*
     * create new CI and verify it.
     */
    @Test
    public void createCIFromTopology() throws CloudException, InternalException {
        ConvergedInfrastructureServices services = tm.getProvider().getConvergedInfrastructureServices();
        if( services == null ) {
            tm.ok("No converged infrastructure services in this cloud");
            return;
        }

        ConvergedInfrastructureSupport support = services.getConvergedInfrastructureSupport();
        if( support == null ) {
            tm.ok("No CI support in this cloud");
            return;
        }
        if (support.getCapabilities().identifyResourcePoolLaunchRequirement().equals(Requirement.REQUIRED) ) {
            if (testResourcePoolId == null) {
                fail("Unable to find test resource pool id in validateConvergedInfratrcture for cloud which has resource pool requirement");
            }
        }
        String result = DaseinTestManager.getCiResources().provisionConvergedInfrastructure(options, DaseinTestManager.STATEFUL);
        assertNotNull(result);
    }

    /*
     * delete a CI
     */
    @Test
    public void deleteCIFromTopology() throws CloudException, InternalException {
        ConvergedInfrastructureServices services = tm.getProvider().getConvergedInfrastructureServices();
        if( services == null ) {
            tm.ok("No converged infrastructure services in this cloud");
            return;
        }

        ConvergedInfrastructureSupport support = services.getConvergedInfrastructureSupport();
        if( support == null ) {
            tm.ok("No CI support in this cloud");
            return;
        }
        if (testCIId != null) {
            support.terminate(testCIId, "die");
        }
    }
}