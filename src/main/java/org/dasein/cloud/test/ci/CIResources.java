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

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.CloudProvider;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ci.ConvergedInfrastructure;
import org.dasein.cloud.ci.ConvergedInfrastructureProvisionOptions;
import org.dasein.cloud.ci.ConvergedInfrastructureServices;
import org.dasein.cloud.ci.ConvergedInfrastructureState;
import org.dasein.cloud.ci.ConvergedInfrastructureSupport;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.ResourcePool;
import org.dasein.cloud.test.DaseinTestManager;
import org.dasein.cloud.test.compute.ComputeResources;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * [Class Documentation]
 * <p>Created by George Reese: 6/3/13 4:50 PM</p>
 *
 * @author George Reese
 */
public class CIResources {
    static private final Logger logger = Logger.getLogger(CIResources.class);
    static private final Random random = new Random();

    private CloudProvider   provider;

    private String testTemplateContent = "";
    private String testParametersContent = "";

    private final HashMap<String,String> testInfrastructures = new HashMap<String, String>();

    public String getTestTemplateContent(boolean supportsLiteralContent) {
        if (testTemplateContent.equals("")) {
            if (supportsLiteralContent) {
                populateTemplateContent();
            }
            else {
                //todo get template link/id for clouds that don't support content
                testTemplateContent = "";
            }
        }
        return testTemplateContent;
    }

    public String getTestParametersContent(boolean supportsLiteralContent) {
        if (testParametersContent.equals("")) {
            if (supportsLiteralContent) {
                populateTemplateContent();
            }
            else {
                //todo get parameter link/id for clouds that don't support content
                testParametersContent = "";
            }
        }
        return testParametersContent;
    }

    private void populateTemplateContent() {
        String templateContentFile = null;
        String parameterContentFile = null;
        if ( provider.getCloudName().equals("Azure") ) {
            /**************************AZURE RESOURCE MANAGER SPECIFIC***********************/
            templateContentFile = "/convergedInfrastructure/templateContent.json";
            parameterContentFile = "/convergedInfrastructure/parameterContent.json";
        }
        if (templateContentFile != null) {
            testTemplateContent = getFileAsJsonString(templateContentFile);
        }

        if (parameterContentFile != null) {
            testParametersContent = getFileAsJsonString(parameterContentFile);
        }
    }

    private String getFileAsJsonString(String fileUri) {
        try {
            InputStream input = StatelessCITests.class.getResourceAsStream(fileUri);
            BufferedReader fileReader = new BufferedReader(new InputStreamReader(input));
            StringBuilder json = new StringBuilder();
            String templateLine;
            while ((templateLine = fileReader.readLine()) != null) {
                json.append(templateLine);
                json.append("\n");
            }
            return json.toString();
        } catch ( IOException e ) {
            logger.warn("Unable to read files for template content: " + e.getMessage());
        }
        return null;
    }

    public CIResources(@Nonnull CloudProvider provider) {
        this.provider = provider;
    }

    public int close() {
        ConvergedInfrastructureServices ciServices = provider.getConvergedInfrastructureServices();
        int count = 0;

        if( ciServices != null ) {
            ConvergedInfrastructureSupport ciSupport = ciServices.getConvergedInfrastructureSupport();

            if( ciSupport != null ) {
                for( Map.Entry<String,String> entry : testInfrastructures.entrySet() ) {
                    if( !entry.getKey().equals(DaseinTestManager.STATELESS) ) {
                        try {
                            ConvergedInfrastructure ci = ciSupport.getConvergedInfrastructure(entry.getValue());

                            if( ci != null ) {
                                ciSupport.terminate(entry.getValue(), null);
                                count++;
                            }
                            else {
                                count++;
                            }
                        }
                        catch( Throwable t ) {
                            logger.warn("Failed to de-provision test CI " + entry.getValue() + ": " + t.getMessage());
                        }
                    }
                }
            }
        }
        return count;
    }

    private @Nullable String findStatelessCI() {
        ConvergedInfrastructureServices services = provider.getConvergedInfrastructureServices();

        if( services != null ) {
            ConvergedInfrastructureSupport support = services.getConvergedInfrastructureSupport();

            try {
                if( support != null && support.isSubscribed() ) {
                    ConvergedInfrastructure defaultCI = null;

                    for( ConvergedInfrastructure ci : support.listConvergedInfrastructures(null) ) {
                        if( ci.getCiState().equals(ConvergedInfrastructureState.READY) ) {
                            defaultCI = ci;
                            break;
                        }
                        if( defaultCI == null ) {
                            defaultCI = ci;
                        }
                    }
                    if( defaultCI != null ) {
                        String id = defaultCI.getProviderCIId();

                        testInfrastructures.put(DaseinTestManager.STATELESS, id);
                        return id;
                    }
                }
            }
            catch( Throwable ignore ) {
                // ignore
            }
        }
        return null;
    }

    public
    @Nullable
    String getTestCIId(@Nonnull String label, boolean provisionIfNull) {
        String id = testInfrastructures.get(label);
        if ( id == null ) {
            if ( label.equals(DaseinTestManager.STATELESS) ) {
                for (Map.Entry<String, String> entry : testInfrastructures.entrySet()) {
                    if ( !entry.getKey().startsWith(DaseinTestManager.REMOVED) ) {
                        id = entry.getValue();

                        if ( id != null ) {
                            return id;
                        }
                    }
                }
                id = findStatelessCI();
            }
        }


        if ( id != null ) {
            return id;
        }
        if ( !provisionIfNull ) {
            return null;
        }
        ConvergedInfrastructureServices services = provider.getConvergedInfrastructureServices();

        if ( services != null ) {
            ConvergedInfrastructureSupport support = services.getConvergedInfrastructureSupport();

            if ( support != null ) {
                try {
                    String testResourcePoolId = null;
                    DataCenterServices dc = provider.getDataCenterServices();
                    if ( dc.getCapabilities().supportsResourcePools() ) {
                        if ( support.getCapabilities().identifyResourcePoolLaunchRequirement().equals(Requirement.REQUIRED) ) {
                            ComputeResources computeResources = DaseinTestManager.getComputeResources();
                            Iterable<ResourcePool> testRPList = dc.listResourcePools(computeResources.getTestDataCenterId(true));
                            if ( testRPList != null && testRPList.iterator().hasNext() ) {
                                ResourcePool rp = testRPList.iterator().next();
                                testResourcePoolId = rp.getProvideResourcePoolId();
                            }
                            if ( testResourcePoolId == null ) {
                                return null;
                            }
                        }
                    }
                    boolean supportsLiteralContent = false;
                    try {
                        Requirement templateContentLaunchRequirement = support.getCapabilities().identifyTemplateContentLaunchRequirement();
                        supportsLiteralContent = !templateContentLaunchRequirement.equals(Requirement.NONE);
                        getTestTemplateContent(supportsLiteralContent);
                        getTestParametersContent(supportsLiteralContent);
                    } catch ( Exception e ) {
                    }
                    if (testTemplateContent != null) {
                        ConvergedInfrastructureProvisionOptions options = ConvergedInfrastructureProvisionOptions.getInstance("dsntest-ci" + label,
                                "dsntest-ci description", testResourcePoolId, null, testTemplateContent, testParametersContent, supportsLiteralContent);
                        String ciId = provisionConvergedInfrastructure(options, label);
                        if ( ciId != null ) {
                            return ciId;
                        }
                    }
                } catch ( Throwable ignore ) {
                    return null;
                }
            }
        }
        return null;
    }

    public int report() {
        boolean header = false;
        int count = 0;

        testInfrastructures.remove(DaseinTestManager.STATELESS);
        if( !testInfrastructures.isEmpty() ) {
            logger.info("Provisioned CI Resources:");
            header = true;
            count += testInfrastructures.size();
            DaseinTestManager.out(logger, null, "---> Infrastructures", testInfrastructures.size() + " " + testInfrastructures);
        }
        return count;
    }

    public String provisionConvergedInfrastructure(ConvergedInfrastructureProvisionOptions options, String label) throws CloudException, InternalException {
        String id = null;
        ConvergedInfrastructureServices services = provider.getConvergedInfrastructureServices();

        if( services != null ) {
            ConvergedInfrastructureSupport support = services.getConvergedInfrastructureSupport();

            if ( support != null ) {
                ConvergedInfrastructure ci = support.provision(options);
                if ( ci != null ) {
                    id = ci.getProviderCIId();
                    synchronized (testInfrastructures) {
                        while (testInfrastructures.containsKey(label)) {
                            label = label + random.nextInt(9);
                        }
                        testInfrastructures.put(label, id);
                    }
                }
            }
        }
        return id;
    }
}
