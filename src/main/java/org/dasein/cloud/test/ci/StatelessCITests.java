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
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.ci.ConvergedInfrastructure;
import org.dasein.cloud.ci.ConvergedInfrastructureProvisionOptions;
import org.dasein.cloud.ci.ConvergedInfrastructureResource;
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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * User: daniellemayne
 * Date: 21/03/2016
 * Time: 09:54
 */
public class StatelessCITests {
    static private DaseinTestManager tm;

    private ConvergedInfrastructureProvisionOptions options;
    private String testResourcePoolId = null;
    private String testConvergedInfrastructureID = null;

    @BeforeClass
    static public void configure() {
        tm = new DaseinTestManager(StatelessCITests.class);
    }

    @AfterClass
    static public void cleanUp() {
        if( tm != null ) {
            tm.close();
        }
    }

    @Rule
    public final TestName name = new TestName();


    public StatelessCITests() {}

    @Before
    public void before() {
        tm.begin(name.getMethodName());
        assumeTrue(!tm.isTestSkipped());
        testConvergedInfrastructureID = tm.getTestCIId(DaseinTestManager.STATELESS, false);

        if ( name.getMethodName().startsWith("validate") ) {
            try {
                testResourcePoolId = null;
                if ( tm.getProvider().getDataCenterServices().getCapabilities().supportsResourcePools() ) {
                    DataCenterServices dc = tm.getProvider().getDataCenterServices();

                    Iterable<ResourcePool> testRPList = dc.listResourcePools(tm.getDefaultDataCenterId(true));
                    if ( testRPList != null && testRPList.iterator().hasNext() ) {
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
                    options = ConvergedInfrastructureProvisionOptions.getInstance("dsn-ci",
                            testResourcePoolId, null, testTemplateContent, testParametersContent, supportsTemplateContent);
                } else {
                    tm.warn("Unable to find converged infrastructure template for testing. Test invalid");
                    return;
                }
            } catch ( Exception e ) {
            }
        }
    }

    @After
    public void after() {
        tm.end();
    }

    private void assertConvergedInfrastructure(ConvergedInfrastructure ci) {
        assertNotNull("Converged Infrastucture id should not be null", ci.getProviderCIId());
        tm.out("Id", ci.getProviderCIId());
        assertNotNull("Converged Infrastructure name should not be null", ci.getName());
        tm.out("Name", ci.getName());
        tm.out("Description", ci.getDescription());
        assertNotNull("Converged Infrastructure state should not be null", ci.getCiState());
        tm.out("State", ci.getCiState());

        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        calendar.setTimeInMillis(ci.getProvisioningTimestamp());
        String t = sdf.format(calendar.getTime());
        tm.out("Timestamp", t);

        assertNotNull("List of resources can be empty but should not be null", ci.getResources());
        for (ConvergedInfrastructureResource resource : ci.getResources()) {
            tm.out("Resource", resource.getResourceType().toString()+": "+resource.getResourceId());
        }
        assertNotNull("Datacenter id should not be null", ci.getProviderDatacenterId());
        tm.out("Datacenter id", ci.getProviderDatacenterId());
        assertNotNull("Region id should not be null", ci.getProviderRegionId());
        tm.out("Region id", ci.getProviderRegionId());
        tm.out("Resource pool id", ci.getProviderResourcePoolId());
        assertNotNull("Tags may be empty list but should not be null", ci.getTags());
        for( Map.Entry<String, String> entry : ci.getTags().entrySet() ) {
            tm.out("Tag " + entry.getKey(), entry.getValue());
        }
    }

    @Test
    public void getConvergedInfrastructure() throws CloudException, InternalException {
        ConvergedInfrastructureServices services = tm.getProvider().getConvergedInfrastructureServices();
        if( services == null ) {
            tm.ok("No Converged Infrastructure services in this cloud");
            return;
        }

        ConvergedInfrastructureSupport support = services.getConvergedInfrastructureSupport();
        if( support == null ) {
            tm.ok("No CI support in this cloud");
            return;
        }
        if (testConvergedInfrastructureID != null) {
            ConvergedInfrastructure ci = support.getConvergedInfrastructure(testConvergedInfrastructureID);
            assertNotNull("Test converged infrastructure "+testConvergedInfrastructureID+" not found", ci);
            assertConvergedInfrastructure(ci);
        }
        else {
            tm.warn("Unable to find test converged infrastructure object for getConvergedInfrastructure. Test may not be valid");
        }
    }

    @Test
    public void listConvergedInfrastructures() throws CloudException, InternalException {
        ConvergedInfrastructureServices services = tm.getProvider().getConvergedInfrastructureServices();
        if( services == null ) {
            tm.ok("No Converged Infrastructure services in this cloud");
            return;
        }

        ConvergedInfrastructureSupport support = services.getConvergedInfrastructureSupport();
        if( support == null ) {
            tm.ok("No CI support in this cloud");
            return;
        }

        int count = 0;
        Iterable<ConvergedInfrastructure> convergedInfrastructures = support.listConvergedInfrastructures(null);
        assertNotNull("Converged infrastructure list may be empty but should not be null", convergedInfrastructures);
        for (ConvergedInfrastructure ci : convergedInfrastructures) {
            assertConvergedInfrastructure(ci);
            count++;
        }
        if (count == 0) {
            tm.warn("No converged infrastructure resources found. Test may not be valid");
        }
    }

    @Test
    public void listConvergedInfrastructureStatus() throws CloudException, InternalException {
        ConvergedInfrastructureServices services = tm.getProvider().getConvergedInfrastructureServices();

        if (services == null) {
            tm.ok("No Converged Infrastructure services in this cloud");
            return;
        }
        ConvergedInfrastructureSupport support = services.getConvergedInfrastructureSupport();

        if( support == null ) {
            tm.ok("No CI support in this cloud");
            return;
        }
        int count = 0;

        Iterable<ResourceStatus> ciStatus = support.listConvergedInfrastructureStatus();
        assertNotNull("Converged infrastructure list may be empty but should not be null", ciStatus);
        for (ResourceStatus status : ciStatus) {
            tm.out(status.getProviderResourceId()+": "+status.getResourceStatus().toString());
            count++;
        }
        if (count == 0) {
            tm.warn("No converged infrastructure resources found. Test may not be valid");
        }
    }

    /*
     * verify the provided converged infrastructure request would be accepted by the cloud.
     */
    @Test
    public void validateConvergedInfrastructure() throws CloudException, InternalException {
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
        try {
            support.validateDeployment(options);
        }
        catch ( Exception e ) {
            fail("Deployment validation failed: "+e.getMessage());
        }

    }
}
