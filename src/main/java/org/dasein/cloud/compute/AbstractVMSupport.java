/**
 * Copyright (C) 2009-2013 enstratius, Inc.
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

package org.dasein.cloud.compute;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.CloudProvider;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.identity.IdentityServices;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.util.APITrace;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.CalendarWrapper;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Megabyte;
import org.dasein.util.uom.storage.Storage;
import org.dasein.util.uom.time.Day;
import org.dasein.util.uom.time.TimePeriod;
import org.dasein.util.uom.time.Week;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

/**
 * Default implementation of virtual machine support for clouds with very little support.
 * <p>Created by George Reese: 1/29/13 6:11 PM</p>
 * @author George Reese
 * @version 2013.04
 * @since 2013.04
 */
public abstract class AbstractVMSupport implements VirtualMachineSupport {
    private CloudProvider provider;

    public AbstractVMSupport(CloudProvider provider) {
        this.provider = provider;
    }

    @Override
    public VirtualMachine alterVirtualMachine(@Nonnull String vmId, @Nonnull VMScalingOptions options) throws InternalException, CloudException {
        throw new OperationNotSupportedException("VM alternations are not currently supported for " + getProvider().getCloudName());
    }

    @Override
    public @Nonnull VirtualMachine clone(@Nonnull String vmId, @Nonnull String intoDcId, @Nonnull String name, @Nonnull String description, boolean powerOn, @Nullable String... firewallIds) throws InternalException, CloudException {
        throw new OperationNotSupportedException("VM cloning is not currently supported for " + getProvider().getCloudName());
    }

    @Override
    public @Nullable VMScalingCapabilities describeVerticalScalingCapabilities() throws CloudException, InternalException {
        return null;
    }

    @Override
    public void disableAnalytics(@Nonnull String vmId) throws InternalException, CloudException {
        // NO-OP
    }

    @Override
    public void enableAnalytics(@Nonnull String vmId) throws InternalException, CloudException {
        // NO-OP
    }

    /**
     * Provides the current provider context for the request in progress.
     * @return the current provider context
     * @throws CloudException no context was defined before making this call
     */
    protected final @Nonnull ProviderContext getContext() throws CloudException {
        ProviderContext ctx = getProvider().getContext();

        if( ctx == null ) {
            throw new CloudException("No context was defined for this request");
        }
        return ctx;
    }

    @Override
    public @Nonnull String getConsoleOutput(@Nonnull String vmId) throws InternalException, CloudException {
        return "";
    }

    @Override
    public int getCostFactor(@Nonnull VmState state) throws InternalException, CloudException {
        return 100;
    }

    @Override
    public int getMaximumVirtualMachineCount() throws CloudException, InternalException {
        return -2;
    }

    @Override
    public @Nullable VirtualMachineProduct getProduct(@Nonnull String productId) throws InternalException, CloudException {
        for( Architecture architecture : Architecture.values() ) {
            for( VirtualMachineProduct prd : listProducts(architecture) ) {
                if( productId.equals(prd.getProviderProductId()) ) {
                    return prd;
                }
            }
        }
        return null;
    }

    /**
     * @return the current provider governing any operations against this cloud in this support instance
     */
    protected final @Nonnull CloudProvider getProvider() {
        return provider;
    }

    @Override
    public @Nullable VirtualMachine getVirtualMachine(@Nonnull String vmId) throws InternalException, CloudException {
        for( VirtualMachine vm : listVirtualMachines(null) ) {
            if( vm.getProviderVirtualMachineId().equals(vmId) ) {
                return vm;
            }
        }
        return null;
    }

    @Override
    public @Nonnull VmStatistics getVMStatistics(@Nonnull String vmId, @Nonnegative long from, @Nonnegative long to) throws InternalException, CloudException {
        return new VmStatistics();
    }

    @Override
    public @Nonnull Iterable<VmStatistics> getVMStatisticsForPeriod(@Nonnull String vmId, @Nonnegative long from, @Nonnegative long to) throws InternalException, CloudException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Requirement identifyImageRequirement(@Nonnull ImageClass cls) throws CloudException, InternalException {
        return (cls.equals(ImageClass.MACHINE) ? Requirement.REQUIRED : Requirement.NONE);
    }

    @Override
    @Deprecated
    public @Nonnull Requirement identifyPasswordRequirement() throws CloudException, InternalException {
        return identifyPasswordRequirement(Platform.UNKNOWN);
    }

    @Override
    public @Nonnull Requirement identifyPasswordRequirement(Platform platform) throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public @Nonnull Requirement identifyRootVolumeRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    @Deprecated
    public @Nonnull Requirement identifyShellKeyRequirement() throws CloudException, InternalException {
        return identifyShellKeyRequirement(Platform.UNKNOWN);
    }

    @Override
    public @Nonnull Requirement identifyShellKeyRequirement(Platform platform) throws CloudException, InternalException {
        IdentityServices services = getProvider().getIdentityServices();

        if( services == null ) {
            return Requirement.NONE;
        }
        if( services.hasShellKeySupport() ) {
            return Requirement.OPTIONAL;
        }
        return Requirement.NONE;
    }

    @Override
    public @Nonnull Requirement identifyStaticIPRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public @Nonnull Requirement identifyVlanRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public boolean isAPITerminationPreventable() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isBasicAnalyticsSupported() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isExtendedAnalyticsSupported() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isUserDataSupported() throws CloudException, InternalException {
        return false;
    }

    @Override
    @Deprecated
    public @Nonnull VirtualMachine launch(@Nonnull String fromMachineImageId, @Nonnull VirtualMachineProduct product, @Nonnull String dataCenterId, @Nonnull String name, @Nonnull String description, @Nullable String withKeypairId, @Nullable String inVlanId, boolean withAnalytics, boolean asSandbox, @Nullable String... firewallIds) throws InternalException, CloudException {
        VMLaunchOptions options = VMLaunchOptions.getInstance(product.getProviderProductId(), fromMachineImageId, name, name, description);

        options.inDataCenter(dataCenterId);
        if( withKeypairId != null ) {
            options.withBoostrapKey(withKeypairId);
        }
        if( inVlanId != null ) {
            options.inVlan(null, dataCenterId, inVlanId);
        }
        if( withAnalytics ) {
            options.withExtendedAnalytics();
        }
        if( firewallIds != null ) {
            options.behindFirewalls(firewallIds);
        }
        return launch(options);
    }

    @Override
    @Deprecated
    public @Nonnull VirtualMachine launch(@Nonnull String fromMachineImageId, @Nonnull VirtualMachineProduct product, @Nonnull String dataCenterId, @Nonnull String name, @Nonnull String description, @Nullable String withKeypairId, @Nullable String inVlanId, boolean withAnalytics, boolean asSandbox, @Nullable String[] firewallIds, @Nullable Tag... tags) throws InternalException, CloudException {
        VMLaunchOptions options = VMLaunchOptions.getInstance(product.getProviderProductId(), fromMachineImageId, name, name, description);

        options.inDataCenter(dataCenterId);
        if( withKeypairId != null ) {
            options.withBoostrapKey(withKeypairId);
        }
        if( inVlanId != null ) {
            options.inVlan(null, dataCenterId, inVlanId);
        }
        if( withAnalytics ) {
            options.withExtendedAnalytics();
        }
        if( firewallIds != null ) {
            options.behindFirewalls(firewallIds);
        }
        if( tags != null ) {
            HashMap<String,Object> metaData = new HashMap<String, Object>();

            for( Tag tag : tags ) {
                metaData.put(tag.getKey(), tag.getValue());
            }
            options.withMetaData(metaData);
        }
        return launch(options);
    }

    @Override
    public @Nonnull Iterable<String> listFirewalls(@Nonnull String vmId) throws InternalException, CloudException {
        return Collections.emptyList();
    }

    /**
     * <p>
     *     Identifies a resource file that potentially contains VM product definitions for one or more clouds and/or
     *     a default set of products. This helps this abstract base class implement some default configuration file
     *     based behaviors for clouds that do not provide mechanisms for looking up VM products such as AWS or clouds
     *     that don't have a concept of products like vCloud. If your cloud provides product lookups (like OpenStack),
     *     then you can happily ignore this method and override {@link #listProducts(Architecture)} to do the proper
     *     lookup.
     * </p>
     * <p>
     *     The best way to take advantage of this feature is simply to create a resource file, place it in the application
     *     CLASSPATH, and then set the {@link ProviderContext#getCustomProperties()} so it has a &quot;vmproducts&quot;
     *     property that points to the resource file to use for that specific cloud connection.
     * </p>
     * <p>
     *     The format of the resource file is as follows:
     *
     * </p>
     * <pre>
     * [
     *     {
     *         "provider":"default",
     *         "cloud":"default",
     *         "products":[
     *         {
     *             "architectures":["I32", "I64"],
     *             "id":"t1.micro",
     *             "name":"Micro Instance (t1.micro)",
     *             "description":"Micro Instance (t1.micro)",
     *             "cpuCount":1,
     *             "rootVolumeSizeInGb":1,
     *             "ramSizeInMb":512
     *         },
     *     }
     * ]
     * </pre>
     * <p>
     *     The core element is a list of cloud/product definitions. Each cloud has a &quot;provider&quot;, &quot;cloud&quot;,
     *     and &quot;products&quot; attribute. The provider is either &quot;default&quot; or a match to the value for
     *     {@link ProviderContext#getProviderName()}. The cloud is similarly either &quot;default&quot; or a match to the
     *     value for {@link ProviderContext#getCloudName()}. The implementation of {@link #listProducts(Architecture)}
     *     in this base class will attempt to match the cloud name and provider name or look for a default.
     * </p>
     * <p>
     *     Here's what happens in practice:
     * </p>
     * <p>
     *     If your implementation has over-ridden {@link #listProducts(Architecture)}, then that logic prevails and
     *     all of this is ignored (for that cloud implementation).
     * </p>
     * <p>
     *     If your implementation is under the package something.whatever.<b>cloudname</b> (the important part is the last part
     *     of the package name) and you have not specified ANY kind of properties, the default {@link #listProducts(Architecture)}
     *     will look for a vmproducts.json file as the resource org.dasein.cloud.<b>cloudname</b>.vmproducts.json. If it exists,
     *     it will be used.
     * </p>
     * <p>
     *     If you specify a custom property with your connection called &quot;vmproducts&quot;, then the
     *     default {@link #listProducts(Architecture)} method will look for a resource matching the value specified
     *     in that property.
     * </p>
     * <p>
     *     If no custom property is set, but there is a system property (@{@link System#getProperties()}) called
     *     &quot;dasein.vmproducts.<b>cloudname</b>&quot; and look for a resource matching the value specified in that
     *     property.
     * </p>
     * <p>
     *     If you do absolutely nothing (or if the property file specified above doesn't actually exist),
     *     Dasein Cloud will load the default vmproducts.json packages with dasein-cloud-core under
     *     the resource org.dasein.cloud.std.vmproducts.json.
     * </p>
     * @return a resource file location with a vmproducts JSON definition
     * @throws CloudException no context has been set for loading the products
     */
    protected @Nonnull String getVMProductsResource() throws CloudException {
        Properties p = getContext().getCustomProperties();
        String value = null;

        if( p != null ) {
            value = p.getProperty("vmproducts");
        }
        if( value == null ) {
            String[] parts = getProvider().getClass().getPackage().getName().split("\\.");
            String impl;

            if( parts.length < 1 ) {
                impl = getProvider().getClass().getPackage().getName();
            }
            else {
                impl = parts[parts.length-1];
            }
            value = System.getProperty("dasein.vmproducts." + impl);
            if( value == null ) {
                value = "/org/dasein/cloud/" + impl + "/vmproducts.json";
            }
        }
        return value;
    }

    @Override
    public @Nonnull Iterable<VirtualMachineProduct> listProducts(@Nonnull Architecture architecture) throws InternalException, CloudException {
        APITrace.begin(getProvider(), "VM.listProducts");
        try {
            Cache<VirtualMachineProduct> cache = Cache.getInstance(getProvider(), "products" + architecture.name(), VirtualMachineProduct.class, CacheLevel.REGION_ACCOUNT, new TimePeriod<Day>(1, TimePeriod.DAY));
            Iterable<VirtualMachineProduct> products = cache.get(getContext());

            if( products != null ) {
                return products;
            }
            ArrayList<VirtualMachineProduct> list = new ArrayList<VirtualMachineProduct>();

            try {
                String resource = getVMProductsResource();
                InputStream input = AbstractVMSupport.class.getResourceAsStream(resource);

                if( input == null ) {
                    input = AbstractVMSupport.class.getResourceAsStream("/org/dasein/cloud/std/vmproducts.json");
                }
                if( input == null ) {
                    return Collections.emptyList();
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                StringBuilder json = new StringBuilder();
                String line;

                while( (line = reader.readLine()) != null ) {
                    json.append(line);
                    json.append("\n");
                }
                JSONArray arr = new JSONArray(json.toString());
                JSONObject toCache = null;

                for( int i=0; i<arr.length(); i++ ) {
                    JSONObject productSet = arr.getJSONObject(i);
                    String cloud, provider;

                    if( productSet.has("cloud") ) {
                        cloud = productSet.getString("cloud");
                    }
                    else {
                        continue;
                    }
                    if( productSet.has("provider") ) {
                        provider = productSet.getString("provider");
                    }
                    else {
                        continue;
                    }
                    if( !productSet.has("products") ) {
                        continue;
                    }
                    if( toCache == null || (provider.equals("default") && cloud.equals("default")) ) {
                        toCache = productSet;
                    }
                    if( provider.equalsIgnoreCase(getProvider().getProviderName()) && cloud.equalsIgnoreCase(getProvider().getCloudName()) ) {
                        toCache = productSet;
                        break;
                    }
                }
                if( toCache == null ) {
                    return Collections.emptyList();
                }
                JSONArray plist = toCache.getJSONArray("products");

                for( int i=0; i<plist.length(); i++ ) {
                    JSONObject product = plist.getJSONObject(i);
                    boolean supported = false;

                    if( product.has("architectures") ) {
                        JSONArray architectures = product.getJSONArray("architectures");

                        for( int j=0; j<architectures.length(); j++ ) {
                            String a = architectures.getString(j);

                            if( architecture.name().equals(a) ) {
                                supported = true;
                                break;
                            }
                        }
                    }
                    if( !supported ) {
                        continue;
                    }
                    if( product.has("excludesRegions") ) {
                        JSONArray regions = product.getJSONArray("excludesRegions");

                        for( int j=0; j<regions.length(); j++ ) {
                            String r = regions.getString(j);

                            if( r.equals(getContext().getRegionId()) ) {
                                supported = false;
                                break;
                            }
                        }
                    }
                    if( !supported ) {
                        continue;
                    }
                    VirtualMachineProduct prd = toProduct(product);

                    if( prd != null ) {
                        list.add(prd);
                    }
                }
                cache.put(getContext(), list);
            }
            catch( IOException e ) {
                throw new InternalException(e);
            }
            catch( JSONException e ) {
                throw new InternalException(e);
            }
            return list;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public Iterable<Architecture> listSupportedArchitectures() throws InternalException, CloudException {
        Cache<Architecture> cache = Cache.getInstance(getProvider(), "architectures", Architecture.class, CacheLevel.REGION_ACCOUNT, new TimePeriod<Week>(1, TimePeriod.WEEK));
        Iterable<Architecture> architectures = cache.get(getContext());

        if( architectures == null ) {
            ArrayList<Architecture> list = new ArrayList<Architecture>();

            Collections.addAll(list, Architecture.values());
            architectures = list;
            cache.put(getContext(), architectures);
        }
        return architectures;
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listVirtualMachineStatus() throws InternalException, CloudException {
        ArrayList<ResourceStatus> status = new ArrayList<ResourceStatus>();

        for( VirtualMachine vm : listVirtualMachines() ) {
            status.add(new ResourceStatus(vm.getProviderVirtualMachineId(), vm.getCurrentState()));
        }
        return status;
    }

    @Override
    public @Nonnull Iterable<VirtualMachine> listVirtualMachines() throws InternalException, CloudException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<VirtualMachine> listVirtualMachines(@Nullable VMFilterOptions options) throws InternalException, CloudException {
        if( options == null ) {
            return listVirtualMachines();
        }
        ArrayList<VirtualMachine> vms = new ArrayList<VirtualMachine>();

        for( VirtualMachine vm : listVirtualMachines() ) {
            if( options.matches(vm) ) {
                vms.add(vm);
            }
        }
        return vms;
    }

    @Override
    public void pause(@Nonnull String vmId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Pause/unpause is not currently implemented for " + getProvider().getCloudName());
    }

    @Override
    public void reboot(@Nonnull String vmId) throws CloudException, InternalException {
        VirtualMachine vm = getVirtualMachine(vmId);

        if( vm == null ) {
            throw new CloudException("No such virtual machine: " + vmId);
        }
        stop(vmId);
        long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 5L);

        while( timeout > System.currentTimeMillis() ) {
            try { vm = getVirtualMachine(vmId); }
            catch( Throwable ignore ) { }
            if( vm == null ) {
                return;
            }
            if( vm.getCurrentState().equals(VmState.STOPPED) ) {
                start(vmId);
                return;
            }
        }
    }

    @Override
    public void resume(@Nonnull String vmId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Resume/suspend is not currently implemented for " + getProvider().getCloudName());
    }

    @Override
    public void start(@Nonnull String vmId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Start/stop is not currently implemented for " + getProvider().getCloudName());
    }

    @Override
    public final void stop(@Nonnull String vmId) throws InternalException, CloudException {
        stop(vmId, false);

        long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 5L);

        while( timeout > System.currentTimeMillis() ) {
            try { Thread.sleep(10000L); }
            catch( InterruptedException ignore ) { }
            try {
                VirtualMachine vm = getVirtualMachine(vmId);

                if( vm == null || VmState.TERMINATED.equals(vm.getCurrentState()) || VmState.STOPPED.equals(vm.getCurrentState()) ) {
                    return;
                }
            }
            catch( Throwable ignore ) {
                // ignore
            }
        }

        stop(vmId, true);
    }

    @Override
    public void stop(@Nonnull String vmId, boolean force) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Start/stop is not currently implemented for " + getProvider().getCloudName());
    }

    @Override
    public final boolean supportsAnalytics() throws CloudException, InternalException {
        return (isBasicAnalyticsSupported() || isExtendedAnalyticsSupported());
    }

    @Override
    public boolean supportsPauseUnpause(@Nonnull VirtualMachine vm) {
        return false;
    }

    @Override
    public boolean supportsStartStop(@Nonnull VirtualMachine vm) {
        return false;
    }

    @Override
    public boolean supportsSuspendResume(@Nonnull VirtualMachine vm) {
        return false;
    }

    @Override
    public void suspend(@Nonnull String vmId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Resume/suspend is not currently implemented for " + getProvider().getCloudName());

    }

    @Override
    public void unpause(@Nonnull String vmId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Pause/unpause is not currently implemented for " + getProvider().getCloudName());
    }

    @Override
    public void updateTags(@Nonnull String vmId, @Nonnull Tag... tags) throws CloudException, InternalException {
        // NO-OP
    }

    @Override
    public void updateTags(@Nonnull String[] vmIds, @Nonnull Tag... tags) throws CloudException, InternalException {
        for( String id : vmIds ) {
            updateTags(id, tags);
        }
    }

    @Override
    public void removeTags(@Nonnull String vmId, @Nonnull Tag... tags) throws CloudException, InternalException {
        // NO-OP
    }

    @Override
    public void removeTags(@Nonnull String[] vmIds, @Nonnull Tag... tags) throws CloudException, InternalException {
        for( String id : vmIds ) {
            removeTags(id, tags);
        }
    }

    @Override
    public @Nonnull String[] mapServiceAction(@Nonnull ServiceAction action) {
        return new String[0];
    }

    private @Nullable VirtualMachineProduct toProduct(@Nonnull JSONObject json) throws InternalException {
        VirtualMachineProduct prd = new VirtualMachineProduct();

        try {
            if( json.has("id") ) {
                prd.setProviderProductId(json.getString("id"));
            }
            else {
                return null;
            }
            if( json.has("name") ) {
                prd.setName(json.getString("name"));
            }
            else {
                prd.setName(prd.getProviderProductId());
            }
            if( json.has("description") ) {
                prd.setDescription(json.getString("description"));
            }
            else {
                prd.setDescription(prd.getName());
            }
            if( json.has("cpuCount") ) {
                prd.setCpuCount(json.getInt("cpuCount"));
            }
            else {
                prd.setCpuCount(1);
            }
            if( json.has("rootVolumeSizeInGb") ) {
                prd.setRootVolumeSize(new Storage<Gigabyte>(json.getInt("rootVolumeSizeInGb"), Storage.GIGABYTE));
            }
            else {
                prd.setRootVolumeSize(new Storage<Gigabyte>(1, Storage.GIGABYTE));
            }
            if( json.has("ramSizeInMb") ) {
                prd.setRamSize(new Storage<Megabyte>(json.getInt("ramSizeInMb"), Storage.MEGABYTE));
            }
            else {
                prd.setRamSize(new Storage<Megabyte>(512, Storage.MEGABYTE));
            }
            if( json.has("standardHourlyRates") ) {
                JSONArray rates = json.getJSONArray("standardHourlyRates");

                for( int i=0; i<rates.length(); i++ ) {
                    JSONObject rate = rates.getJSONObject(i);

                    if( rate.has("rate") ) {
                        prd.setStandardHourlyRate((float)rate.getDouble("rate"));
                    }
                }
            }
        }
        catch( JSONException e ) {
            throw new InternalException(e);
        }
        return prd;
    }

}
