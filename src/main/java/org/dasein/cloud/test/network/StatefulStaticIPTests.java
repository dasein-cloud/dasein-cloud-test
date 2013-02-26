package org.dasein.cloud.test.network;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.compute.VMLaunchOptions;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineSupport;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.IpAddress;
import org.dasein.cloud.network.IpAddressSupport;
import org.dasein.cloud.network.NetworkServices;
import org.dasein.cloud.network.Subnet;
import org.dasein.cloud.network.VLAN;
import org.dasein.cloud.network.VLANSupport;
import org.dasein.cloud.test.DaseinTestManager;
import org.dasein.cloud.test.compute.ComputeResources;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import javax.annotation.Nonnull;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * [Class Documentation]
 * <p>Created by George Reese: 2/23/13 10:22 PM</p>
 *
 * @author George Reese
 */
public class StatefulStaticIPTests {
    static private DaseinTestManager tm;

    @BeforeClass
    static public void configure() {
        tm = new DaseinTestManager(StatefulStaticIPTests.class);
    }

    @AfterClass
    static public void cleanUp() {
        if( tm != null ) {
            tm.close();
        }
    }

    @Rule
    public final TestName name = new TestName();

    private String testIpAddress;
    private String testVlanId;
    private String testVMId;

    public StatefulStaticIPTests() { }

    @Before
    public void before() {
        tm.begin(name.getMethodName());
        assumeTrue(!tm.isTestSkipped());
        testVlanId = tm.getTestVLANId(DaseinTestManager.STATEFUL, true, null);
        if( testVlanId != null ) {
            NetworkServices services = tm.getProvider().getNetworkServices();

            if( services != null ) {
                VLANSupport support = services.getVlanSupport();

                try {
                    if( support != null && support.supportsInternetGatewayCreation() && !support.isConnectedViaInternetGateway(testVlanId) ) {
                        support.createInternetGateway(testVlanId);
                    }
                }
                catch( Throwable ignore ) {
                    // ignore
                }
            }
        }
        if( name.getMethodName().equals("releaseFromPool") ) {
            testIpAddress = tm.getTestStaticIpId(DaseinTestManager.REMOVED, true, null, false, null);
            if( testIpAddress == null ) {
                testIpAddress = tm.getTestStaticIpId(DaseinTestManager.REMOVED, true, null, true, null);
            }
        }
        else if( name.getMethodName().startsWith("assignPost") || name.getMethodName().startsWith("forward") || name.getMethodName().equals("releaseFromServer") ) {
            IPVersion version = IPVersion.IPV4;

            if( name.getMethodName().contains("IPv6") ) {
                version = IPVersion.IPV6;
            }
            if( !name.getMethodName().endsWith("InVLAN") ) {
                testVMId = tm.getTestVMId(DaseinTestManager.STATEFUL, VmState.RUNNING, true, null);
                if( testVMId != null ) {
                    VirtualMachine vm = null;

                    try {
                        //noinspection ConstantConditions
                        vm = tm.getProvider().getComputeServices().getVirtualMachineSupport().getVirtualMachine(testVMId);
                    }
                    catch( Throwable ignore ) {
                        // ignore
                    }
                    if( vm != null && vm.getProviderVlanId() != null ) {
                        testVMId = null;
                    }
                    else if( vm != null ) {
                        testIpAddress = tm.getTestStaticIpId(DaseinTestManager.STATEFUL, true, version, false, null);
                    }
                }
            }
            else if( testVlanId != null ) {
                testVMId = tm.getTestVMId(DaseinTestManager.STATEFUL + "vlan", VmState.RUNNING, false, null);
                if( testVMId == null ) {
                    String productId = tm.getTestVMProductId();
                    String imageId = tm.getTestImageId(DaseinTestManager.STATELESS, false);

                    if( productId != null && imageId != null ) {
                        VMLaunchOptions options = VMLaunchOptions.getInstance(productId, imageId, "dsnnetl" + (System.currentTimeMillis()%10000), "Dasein Network Launch " + System.currentTimeMillis(), "Test launch for a VM in a network");
                        String vlanId = tm.getTestSubnetId(DaseinTestManager.STATEFUL, true, testVlanId, null);
                        String dataCenterId = null;

                        if( vlanId == null ) {
                            vlanId = testVlanId;

                            try {
                                @SuppressWarnings("ConstantConditions") VLAN vlan = tm.getProvider().getNetworkServices().getVlanSupport().getVlan(testVlanId);

                                if( vlan != null ) {
                                    dataCenterId = vlan.getProviderDataCenterId();
                                }
                            }
                            catch( Throwable ignore ) {
                                // ignore
                            }
                        }
                        else {
                            try {
                                @SuppressWarnings("ConstantConditions") Subnet subnet = tm.getProvider().getNetworkServices().getVlanSupport().getSubnet(testVlanId);

                                if( subnet != null ) {
                                    dataCenterId = subnet.getProviderDataCenterId();
                                }
                            }
                            catch( Throwable ignore ) {
                                // ignore
                            }
                        }
                        if( dataCenterId == null ) {
                            try {
                                for( DataCenter dc : tm.getProvider().getDataCenterServices().listDataCenters(tm.getContext().getRegionId()) ) {
                                    if( dc.isActive() && dc.isAvailable() ) {
                                        dataCenterId = dc.getProviderDataCenterId();
                                        break;
                                    }
                                }
                            }
                            catch( Throwable ignore ) {
                                // ignore
                            }
                        }
                        assert dataCenterId != null;

                        options.inDataCenter(dataCenterId);
                        options.inVlan(null, dataCenterId, vlanId);

                        ComputeResources compute = DaseinTestManager.getComputeResources();

                        if( compute != null ) {
                            try {
                                //noinspection ConstantConditions
                                testVMId = compute.provisionVM(tm.getProvider().getComputeServices().getVirtualMachineSupport(), DaseinTestManager.STATEFUL + "vlan", options, dataCenterId);
                            }
                            catch( Throwable ignore ) {
                                // ignore
                            }
                        }
                    }
                }
                if( testVMId != null ) {
                    try {
                        VirtualMachine vm = tm.getProvider().getComputeServices().getVirtualMachineSupport().getVirtualMachine(testVMId);

                        testVlanId = vm.getProviderVlanId();
                        testIpAddress = tm.getTestStaticIpId(DaseinTestManager.STATEFUL, true, version, true, testVlanId);
                    }
                    catch( Throwable ignore ) {
                        // ignore
                    }
                }
            }
        }
    }

    @After
    public void after() {
        try {
            testVlanId = null;
            testVMId = null;
            if( testIpAddress != null ) {
                NetworkServices services = tm.getProvider().getNetworkServices();

                if( services != null ) {
                    IpAddressSupport support = services.getIpAddressSupport();

                    if( support != null ) {
                        try {
                            support.releaseFromServer(testIpAddress);
                        }
                        catch( Throwable ignore ) {
                            // ignore
                        }
                    }
                }
                testIpAddress = null;
            }
        }
        finally {
            tm.end();
        }
    }

    private void request(@Nonnull IPVersion version, boolean forVLAN) throws CloudException, InternalException {
        NetworkServices services = tm.getProvider().getNetworkServices();

        if( services == null ) {
            tm.ok("Network services are not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            return;
        }
        IpAddressSupport support = services.getIpAddressSupport();

        if( support == null ) {
            tm.ok("Static IP addresses are not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            return;
        }
        NetworkResources network = DaseinTestManager.getNetworkResources();

        assertNotNull("Testing failed to initialize properly as there are no network resources", network);
        if( !support.isSubscribed() ) {
            try {
                network.provisionAddress(support, "provision", version, null);
                fail("The account is supposedly not subscribed to IP address support, but a request operation completed");
            }
            catch( CloudException expected ) {
                tm.ok("Caught a cloud exception attempting to request an address of type " + version + " in an account where there is no subscription");
            }
        }
        else if( support.isRequestable(version) && (!forVLAN || support.supportsVLANAddresses(version)) ) {
            String addressId;

            if( !forVLAN ) {
                addressId = network.provisionAddress(support, "provision", version, null);
            }
            else {
                addressId = network.provisionAddress(support, "provision", version, testVlanId);
            }
            tm.out("New " + version + " Address", addressId);
            assertNotNull("Requesting a new IP address may not result in a null address ID", addressId);
        }
        else {
            try {
                if( !forVLAN ) {
                    network.provisionAddress(support, "provision", version, null);
                }
                else {
                    network.provisionAddress(support, "provision", version, UUID.randomUUID().toString());
                }
                fail("Requesting addresses of " + version + " is supposedly not supported, but the operation completed");
            }
            catch( OperationNotSupportedException expected ) {
                tm.ok("Caught OperationNotSupportedException when attempting to request an IP address of version " + version);
            }
        }
    }

    @Test
    public void requestIPv4() throws CloudException, InternalException {
        request(IPVersion.IPV4, false);
    }

    @Test
    public void requestIPv6() throws CloudException, InternalException {
        request(IPVersion.IPV6, false);
    }

    @Test
    public void requestIPv4InVLAN() throws CloudException, InternalException {
        request(IPVersion.IPV4, true);
    }

    @Test
    public void requestIPv6inVLAN() throws CloudException, InternalException {
        request(IPVersion.IPV6, true);
    }

    private void assignPostLaunch(@Nonnull IPVersion version) throws CloudException, InternalException {
        NetworkServices services = tm.getProvider().getNetworkServices();

        if( services == null ) {
            tm.ok("Network services are not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            return;
        }
        IpAddressSupport support = services.getIpAddressSupport();

        if( support == null ) {
            tm.ok("Static IP addresses are not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            return;
        }
        if( testIpAddress == null ) {
            if( !support.isSubscribed() ) {
                tm.ok("No IP address subscription exists for this account in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName() + ", so this test is invalid");
            }
            else if( !support.isRequestable(version) ) {
                tm.warn("Unable to provision new IP addresses in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName() + ", so this test cannot be run");
            }
            else {
                if( name.getMethodName().contains("VLAN") && !support.supportsVLANAddresses(version) ) {
                    tm.ok("No VLAN IP addresses are supported");
                }
                else {
                    fail("Unable to get a test IP address for running the test " + name.getMethodName());
                }
            }
            return;
        }
        if( testVMId == null ) {
            fail("Unable to get a test VM for running the test " + name.getMethodName());
        }
        if( support.isAssignablePostLaunch(version) ) {
            @SuppressWarnings("ConstantConditions") VirtualMachineSupport vmSupport = tm.getProvider().getComputeServices().getVirtualMachineSupport();

            assertNotNull("No virtual machine support exists in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName(), vmSupport);
            VirtualMachine vm = vmSupport.getVirtualMachine(testVMId);

            assertNotNull("The test virtual machine disappeared before the test could run", vm);
            tm.out("Before Assignment", vm.getProviderAssignedIpAddressId());
            assertTrue("The current assignment to the test virtual machine is the test IP address, cannot reasonably tests this", !testIpAddress.equals(vm.getProviderAssignedIpAddressId()));
            support.assign(testIpAddress, testVMId);
            try { Thread.sleep(3000L); }
            catch( InterruptedException ignore ) { }
            vm = vmSupport.getVirtualMachine(testVMId);
            assertNotNull("Virtual machine disappeared post-assignment", vm);
            tm.out("After Assignment", vm.getProviderAssignedIpAddressId());
            assertEquals("The IP address assigned to the virtual machine does not match the test IP address", testIpAddress, vm.getProviderAssignedIpAddressId());
        }
        else {
            try {
                support.assign(testIpAddress, testVMId);
                fail("Assigning an IP address post-launch succeeded even though meta-data suggests it should not have");
            }
            catch( OperationNotSupportedException expected ) {
                tm.ok("Caught OperationNotSupportedException when attempting to assign an IP to a VM post-launch when such assignments are not allowed");
            }
        }
    }

    @Test
    public void assignPostLaunchIPv4() throws CloudException, InternalException {
        assignPostLaunch(IPVersion.IPV4);
    }

    @Test
    public void assignPostLaunchIPv6() throws CloudException, InternalException {
        assignPostLaunch(IPVersion.IPV6);
    }

    @Test
    public void assignPostLaunchIPv4InVLAN() throws CloudException, InternalException {
        assignPostLaunch(IPVersion.IPV4);
    }

    @Test
    public void assignPostLaunchIPv6inVLAN() throws CloudException, InternalException {
        assignPostLaunch(IPVersion.IPV6);
    }
    @Test
    public void releaseFromPool() throws CloudException, InternalException {
        NetworkServices services = tm.getProvider().getNetworkServices();

        if( services == null ) {
            tm.ok("Network services are not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            return;
        }
        IpAddressSupport support = services.getIpAddressSupport();

        if( support == null ) {
            tm.ok("Static IP addresses are not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            return;
        }
        if( testIpAddress == null ) {
            if( !support.isRequestable(IPVersion.IPV4) && !support.isRequestable(IPVersion.IPV6) ) {
                tm.ok("Requesting/releasing addresses is not supported in " + tm.getContext().getRegionId() + " of " + tm.getProvider().getCloudName());
            }
            else {
                fail("There is no test IP address to use in verifying the ability to release addresses from the pool");
            }
        }
        else {
            IpAddress address = support.getIpAddress(testIpAddress);

            assertNotNull("Test IP addresss " + address + " does not exist", address);
            support.releaseFromPool(testIpAddress);
            address = support.getIpAddress(testIpAddress);
            tm.out("Result", address);
            assertNull("The test IP address " + testIpAddress + " still exists in the IP address pool", address);
        }
    }
}
