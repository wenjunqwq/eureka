package com.netflix.eureka2.eureka1x.rest;

import java.util.Set;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.datacenter.AwsDataCenterInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.selector.ServiceSelector;
import com.netflix.eureka2.testkit.data.builder.SampleAwsDataCenterInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.subjects.ReplaySubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class Eureka1xDomainObjectModelMapperTest {

    public static final int APPLICATION_CLUSTER_SIZE = 3;

    private final Eureka1xDomainObjectModelMapper mapper = new Eureka1xDomainObjectModelMapper();

    private final SourcedEurekaRegistry<InstanceInfo> registry = mock(SourcedEurekaRegistry.class);
    private final ReplaySubject<ChangeNotification<InstanceInfo>> notificationSubject = ReplaySubject.create();

    @Before
    public void setUp() throws Exception {
        when(registry.forInterest(any(Interest.class))).thenReturn(notificationSubject);
    }

    @Test
    public void testConversionToEureka1xDataCenterInfo() throws Exception {
        AwsDataCenterInfo v2DataCenterInfo = SampleAwsDataCenterInfo.UsEast1a.build();
        AmazonInfo v1DataCenterInfo = mapper.toEureka1xDataCenterInfo(v2DataCenterInfo);
        verifyDataCenterInfoMapping(v1DataCenterInfo, v2DataCenterInfo);
    }

    @Test
    public void testConversionToEureka1xInstanceInfo() {
        InstanceInfo v2InstanceInfo = SampleInstanceInfo.WebServer.build();
        com.netflix.appinfo.InstanceInfo v1InstanceInfo = mapper.toEureka1xInstanceInfo(v2InstanceInfo);

        assertThat(v1InstanceInfo.getAppGroupName(), is(equalToIgnoringCase(v2InstanceInfo.getAppGroup())));
        assertThat(v1InstanceInfo.getAppName(), is(equalToIgnoringCase(v2InstanceInfo.getApp())));
        assertThat(v1InstanceInfo.getASGName(), is(equalToIgnoringCase(v2InstanceInfo.getAsg())));
        assertThat(v1InstanceInfo.getVIPAddress(), is(equalToIgnoringCase(v2InstanceInfo.getVipAddress())));
        assertThat(v1InstanceInfo.getSecureVipAddress(), is(equalToIgnoringCase(v2InstanceInfo.getSecureVipAddress())));
        InstanceStatus mappedStatus = mapper.toEureka1xStatus(v2InstanceInfo.getStatus());
        assertThat(v1InstanceInfo.getStatus(), is(equalTo(mappedStatus)));

        // Data center info
        AwsDataCenterInfo dataCenterInfo = (AwsDataCenterInfo) v2InstanceInfo.getDataCenterInfo();
        verifyDataCenterInfoMapping((AmazonInfo) v1InstanceInfo.getDataCenterInfo(), (AwsDataCenterInfo) v2InstanceInfo.getDataCenterInfo());
        assertThat(v1InstanceInfo.getHostName(), is(equalToIgnoringCase(dataCenterInfo.getPublicAddress().getHostName())));

        // Network addresses
        assertThat(v1InstanceInfo.getHostName(), is(equalTo(dataCenterInfo.getPublicAddress().getHostName())));
        assertThat(v1InstanceInfo.getIPAddr(), is(equalTo(dataCenterInfo.getPublicAddress().getIpAddress())));

        // Port mapping
        int port = ServiceSelector.selectBy().secure(false).returnServiceEndpoint(v2InstanceInfo).getServicePort().getPort();
        int securePort = ServiceSelector.selectBy().secure(true).returnServiceEndpoint(v2InstanceInfo).getServicePort().getPort();
        assertThat(v1InstanceInfo.getPort(), is(equalTo(port)));
        assertThat(v1InstanceInfo.getSecurePort(), is(equalTo(securePort)));

        // Home/status/health check URLs
        assertThat(v1InstanceInfo.getHomePageUrl(), is(equalTo(v2InstanceInfo.getHomePageUrl())));
        assertThat(v1InstanceInfo.getStatusPageUrl(), is(equalTo(v2InstanceInfo.getStatusPageUrl())));
        assertThat(v1InstanceInfo.getHealthCheckUrls(), is(equalTo((Set<String>) v2InstanceInfo.getHealthCheckUrls())));

        // Lease info
        assertThat(v1InstanceInfo.getLeaseInfo(), is(notNullValue()));

        // Meta data
        assertThat(v1InstanceInfo.getMetadata(), is(equalTo(v2InstanceInfo.getMetaData())));
    }

    private static void verifyDataCenterInfoMapping(AmazonInfo v1DataCenterInfo, AwsDataCenterInfo v2DataCenterInfo) {
        assertThat(v1DataCenterInfo.getName(), is(equalTo(Name.Amazon)));
        assertThat(v1DataCenterInfo.getId(), is(equalTo(v2DataCenterInfo.getInstanceId())));
        assertThat(v1DataCenterInfo.get(MetaDataKey.amiId), is(equalTo(v2DataCenterInfo.getAmiId())));
        assertThat(v1DataCenterInfo.get(MetaDataKey.availabilityZone), is(equalTo(v2DataCenterInfo.getZone())));
        assertThat(v1DataCenterInfo.get(MetaDataKey.instanceId), is(equalTo(v2DataCenterInfo.getInstanceId())));
        assertThat(v1DataCenterInfo.get(MetaDataKey.instanceType), is(equalTo(v2DataCenterInfo.getInstanceType())));
        assertThat(v1DataCenterInfo.get(MetaDataKey.localIpv4), is(equalTo(v2DataCenterInfo.getPrivateAddress().getIpAddress())));
        assertThat(v1DataCenterInfo.get(MetaDataKey.publicHostname), is(equalTo(v2DataCenterInfo.getPublicAddress().getHostName())));
        assertThat(v1DataCenterInfo.get(MetaDataKey.publicIpv4), is(equalTo(v2DataCenterInfo.getPublicAddress().getIpAddress())));
    }
}