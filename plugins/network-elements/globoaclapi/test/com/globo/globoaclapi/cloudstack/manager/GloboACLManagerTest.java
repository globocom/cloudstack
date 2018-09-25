package com.globo.globoaclapi.cloudstack.manager;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.network.Network;
import com.cloud.network.Networks;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.rules.FirewallRule;
import com.cloud.network.rules.FirewallRuleVO;
import com.cloud.resource.ResourceManager;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.globoaclapi.cloudstack.commands.CreateACLRuleCommand;
import com.globo.globoaclapi.cloudstack.commands.ListACLRulesCommand;
import com.globo.globoaclapi.cloudstack.commands.RemoveACLRuleCommand;
import com.globo.globoaclapi.cloudstack.resource.GloboAclApiResource;
import com.globo.globoaclapi.cloudstack.response.GloboACLRulesResponse;
import com.globo.globonetwork.client.model.Vlan;
import com.globo.globonetwork.cloudstack.GloboNetworkNetworkVO;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkNetworkDao;
import com.globo.globonetwork.cloudstack.manager.GloboNetworkManager;
import org.apache.cloudstack.acl.ControlledEntity;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GloboACLManagerTest {

    private GloboACLManager manager;

    @Before
    public void setUp() throws Exception {
        manager = spy(new GloboACLManager());
        doReturn("user").when(manager).getCallingUser();
    }

    @Test
    public void testListACLsGivenNoACLsFound(){
        mockGetGloboNetworkVlanNumber(1L);
        mockGetGloboNetworkEnvironmentID(1L);
        mockGetACLapiHost();

        AgentManager agentManager = mockAgentManagerEasySend(new GloboACLRulesResponse(new ArrayList<GloboACLRulesResponse.ACLRule>()));

        List<FirewallRuleVO> rules = manager.listACLRules(new NetworkVO());

        assertTrue(rules.isEmpty());
        verify(agentManager, times(1)).easySend(anyLong(), any(ListACLRulesCommand.class));
    }

    @Test
    public void testListACLsGivenError(){
        mockGetGloboNetworkVlanNumber(1L);
        mockGetGloboNetworkEnvironmentID(1L);
        mockGetACLapiHost();

        AgentManager agentManager = mockAgentManagerEasySend(new Answer(null, false, "Error"));

        try{
            manager.listACLRules(new NetworkVO());
            fail();
        }catch(CloudRuntimeException e){
            assertEquals("Error", e.getMessage());
            verify(agentManager, times(1)).easySend(anyLong(), any(ListACLRulesCommand.class));
        }
    }

    @Test
    public void testListACLsGivenVlanNotActivated(){
        mockGetGloboNetworkVlanNumber(1L);
        mockGetGloboNetworkEnvironmentID(1L);
        mockGetACLapiHost();

        AgentManager agentManager = mockAgentManagerEasySend(new Answer(null, false, GloboACLManager.VLAN_NOT_ACTIVATED_MESSAGE));

        List<FirewallRuleVO> rules = manager.listACLRules(new NetworkVO());

        assertEquals(0, rules.size());
        verify(agentManager, times(1)).easySend(anyLong(), any(ListACLRulesCommand.class));
    }

    @Test
    public void testListACLsGivenOneACLFound(){
        mockGetGloboNetworkVlanNumber(1L);
        mockGetGloboNetworkEnvironmentID(1L);
        mockGetACLapiHost();

        GloboACLRulesResponse.ACLRule rule = createACLRuleResponse("10.0.0.5/24", 80, null);
        AgentManager agentManager = mockAgentManagerEasySend(new GloboACLRulesResponse(Arrays.asList(rule)));

        List<FirewallRuleVO> rules = manager.listACLRules(new NetworkVO());

        assertEquals(1, rules.size());
        verify(agentManager, times(1)).easySend(anyLong(), any(ListACLRulesCommand.class));
    }

    @Test
    public void testCreateACLGivenRuleWithoutDestinationCIDR() throws Exception {
        try{
            manager.createACLRule(createNetwork(), createTCPFirewallRuleVO(" ", 80, 81));
            fail();
        }catch(InvalidParameterValueException e){
            assertEquals("Invalid source CIDR, value should not be empty.", e.getMessage());
        }
    }

    @Test
    public void testCreateACLGivenNotPortsSupplied() throws Exception {
        try{
            manager.createACLRule(createNetwork(), createTCPFirewallRuleVO("10.0.0.5/24", null, null));
            fail();
        }catch(InvalidParameterValueException e){
            assertEquals("Start port should not be empty.", e.getMessage());
        }
    }

    @Test
    public void testCreateACLGivenEndPortGreaterThanStartPortSupplied() throws Exception {
        try{
            manager.createACLRule(createNetwork(), createTCPFirewallRuleVO("10.0.0.5/24", 80, 70));
            fail();
        }catch(InvalidParameterValueException e){
            assertEquals("End port should not be greater than start port.", e.getMessage());
        }
    }

    @Test
    public void testCreateACLGivenNullIcmpCode() throws Exception {
        try{
            manager.createACLRule(createNetwork(), createICMPFirewallRuleVO("10.0.0.5/24", null, 1));
            fail();
        }catch(InvalidParameterValueException e){
            assertEquals("ICMP type and code should not be empty", e.getMessage());
        }
    }

    @Test
    public void testCreateACLGivenNullIcmpType() throws Exception {
        try{
            manager.createACLRule(createNetwork(), createICMPFirewallRuleVO("10.0.0.5/24", 1, null));
            fail();
        }catch(InvalidParameterValueException e){
            assertEquals("ICMP type and code should not be empty", e.getMessage());
        }
    }

    @Test
    public void testCreateACLGivenInvalidCommandResponse() throws Exception {
        mockGetGloboNetworkVlanNumber(1L);
        mockGetGloboNetworkEnvironmentID(1L);
        mockGetACLapiHost();
        AgentManager agentManager = mockAgentManagerEasySend(new Answer(null, false, "Invalid ACL rule"));

        try {
            manager.createACLRule(createNetwork(), createTCPFirewallRuleVO("10.0.0.5/24", 80, null));
            fail();
        }catch(CloudRuntimeException e){
            assertEquals("Invalid ACL rule", e.getMessage());
            verify(agentManager, times(1)).easySend(anyLong(), any(CreateACLRuleCommand.class));
        }
    }

    @Test
    public void tesRemoveACLGivenInvalidCommandResponse() throws Exception {
        mockGetGloboNetworkVlanNumber(1L);
        mockGetGloboNetworkEnvironmentID(1L);
        mockGetACLapiHost();

        AgentManager agentManager = mockAgentManagerEasySend(new Answer(null, false, "Invalid ACL rule"));

        try {
            manager.removeACLRule(createNetwork(), 1L);
            fail();
        }catch(CloudRuntimeException e){
            assertEquals("Invalid ACL rule", e.getMessage());
            verify(agentManager, times(1)).easySend(anyLong(), any(RemoveACLRuleCommand.class));
        }
    }

    @Test
    public void testCreateACLGivenSuccessCommandResponse() throws Exception {
        mockGetGloboNetworkVlanNumber(1L);
        mockGetGloboNetworkEnvironmentID(1L);
        mockGetACLapiHost();
        AgentManager agentManager = mockAgentManagerEasySend(new Answer(null, true, "ACL rule created"));

        manager.createACLRule(createNetwork(), createTCPFirewallRuleVO("10.0.0.5/24", 80, null));
        verify(agentManager, times(1)).easySend(anyLong(), any(CreateACLRuleCommand.class));
    }

    @Test
    public void testRemoveACLGivenSuccessCommandResponse() throws Exception {
        mockGetGloboNetworkVlanNumber(1L);
        mockGetGloboNetworkEnvironmentID(1L);
        mockGetACLapiHost();
        AgentManager agentManager = mockAgentManagerEasySend(new Answer(null, true, "ACL rule created"));

        manager.removeACLRule(createNetwork(), 1L);
        verify(agentManager, times(1)).easySend(anyLong(), any(RemoveACLRuleCommand.class));
    }

    @Test
    public void testCreateACLRuleCommandGivenTCPRule() throws Exception {
        mockGetGloboNetworkVlanNumber(1L);
        mockGetGloboNetworkEnvironmentID(1L);

        CreateACLRuleCommand cmd = manager.createACLRuleCommand(createNetwork(), createTCPFirewallRuleVO("10.0.0.5/24", 80, 85));

        assertEquals("tcp", cmd.getProtocol());
        assertEquals("10.0.0.5/24", cmd.getDestinationCidr());
        assertEquals(new Integer(80), cmd.getStartPort());
        assertEquals(new Integer(85), cmd.getEndPort());
        assertEquals(new Long(1), cmd.getVlanNumber());
        assertEquals(new Long(1), cmd.getEnvironmentId());
    }

    @Test
    public void testCreateACLRuleCommandGivenICMPRule() throws Exception {
        mockGetGloboNetworkVlanNumber(1L);
        mockGetGloboNetworkEnvironmentID(1L);

        CreateACLRuleCommand cmd = manager.createACLRuleCommand(createNetwork(), createICMPFirewallRuleVO("10.0.0.5/24", 1, 1));

        assertEquals("icmp", cmd.getProtocol());
        assertEquals("10.0.0.5/24", cmd.getDestinationCidr());
        assertEquals(new Integer(1), cmd.getIcmpCode());
        assertEquals(new Integer(1), cmd.getIcmpType());
        assertEquals(new Long(1), cmd.getVlanNumber());
        assertEquals(new Long(1), cmd.getEnvironmentId());
    }

    @Test
    public void testCreateACLApiHostGivenInvalidURL(){
        try {
            manager.addGloboAclApiHost(1L, null, null, null);
            fail();
        }catch (InvalidParameterValueException e){
            assertEquals("Invalid ACL API URL", e.getMessage());
        }
    }

    @Test
    public void testCreateACLApiHostGivenInvalidUsername(){
        try {
            manager.addGloboAclApiHost(1L, "https://aclapiurl.com", null, null);
            fail();
        }catch (InvalidParameterValueException e){
            assertEquals("Invalid ACL API username", e.getMessage());
        }
    }

    @Test
    public void testCreateACLApiHostGivenInvalidPassword(){
        try {
            manager.addGloboAclApiHost(1L, "https://aclapiurl.com", "username", null);
            fail();
        }catch (InvalidParameterValueException e){
            assertEquals("Invalid ACL API password", e.getMessage());
        }
    }

    @Test
    public void testCreateACLApiHostGivenNullPhysicalNetworkId(){
        try {
            manager.addGloboAclApiHost(null, "https://aclapiurl.com", "username", "password");
            fail();
        }catch (InvalidParameterValueException e){
            assertEquals("Invalid physicalNetworkId", e.getMessage());
        }
    }

    @Test
    public void testCreateACLApiHostGivenInvalidPhysicalNetwork(){
        mockFindPhyisicalNetwork(null);
        try {
            manager.addGloboAclApiHost(1L, "https://aclapiurl.com", "username", "password");
            fail();
        }catch (InvalidParameterValueException e){
            assertEquals("Unable to find a physical network having the specified physical network id", e.getMessage());
        }
    }

    @Test
    public void testCreateACLApiHostGivenFailOnAddingHost(){
        mockFindPhyisicalNetwork(new PhysicalNetworkVO(1L, 1L, null, null, null, null, null));
        ResourceManager resourceManagerMock = mockAddHost(null);
        try {
            manager.addGloboAclApiHost(1L, "https://aclapiurl.com", "username", "password");
            fail();
        }catch (CloudRuntimeException e){
            assertEquals("Failed to add Globo ACL API host", e.getMessage());
            verify(resourceManagerMock, times(1)).addHost(eq(1L), any(GloboAclApiResource.class), eq(Host.Type.L2Networking), any(HashMap.class));
        }
    }

    private GloboACLRulesResponse.ACLRule createACLRuleResponse(String destination, Integer portStart, Integer portEnd) {
        GloboACLRulesResponse.ACLRule rule = new GloboACLRulesResponse.ACLRule();
        rule.setId("1");
        rule.setProtocol("TCP");
        rule.setDestination(destination);
        rule.setPortStart(portStart);
        rule.setPortEnd(portEnd);
        return rule;
    }

    private FirewallRuleVO createTCPFirewallRuleVO(String destination, Integer portStart, Integer portEnd) {
        FirewallRuleVO rule = new FirewallRuleVO(
            "1", 1L, portStart, portEnd, "tcp", 1, 1, 1,
            FirewallRule.Purpose.Firewall, null, null, null, null,
            1l, FirewallRule.TrafficType.Egress, FirewallRule.FirewallRuleType.User
        );
        rule.setSourceCidrList(Arrays.asList(destination));
        return rule;
    }

    private FirewallRuleVO createICMPFirewallRuleVO(String destination, Integer icmpCode, Integer icmpType) {
        FirewallRuleVO rule = new FirewallRuleVO(
                "1", 1L, null, null, "icmp", 1, 1, 1,
                FirewallRule.Purpose.Firewall, null, Arrays.asList(destination), icmpCode, icmpType, null,
                FirewallRule.TrafficType.Egress, FirewallRule.FirewallRuleType.User
        );
        rule.setSourceCidrList(Arrays.asList(destination));
        return rule;
    }

    private NetworkVO createNetwork() {
        NetworkVO network = new NetworkVO(
            1L, Networks.TrafficType.Guest, Networks.Mode.None,
            Networks.BroadcastDomainType.Vlan, 1L, 1L, 1L, 1L, "bla", "fake", "eet.net",
            Network.GuestType.Shared, 1L, 1L,
            ControlledEntity.ACLType.Account, false, 1L, false
        );
        network.setNetworkCidr("10.10.10.0/24");
        return network;
    }

    private HostVO createMockHost() {
        return new HostVO(10L, "Host-1", Host.Type.Routing, null,
                "10.0.0.0", null, null, null, null, null, null, null, null,
                Status.Up, null, null, null, 10L, 10L, 30L, 10233, null, null,
                null, 0, null);
    }

    private void mockGetGloboNetworkVlanNumber(long vlanNum) {
        GloboNetworkManager globoNetworkService = mock(GloboNetworkManager.class);
        Vlan vlan = new Vlan();
        vlan.setVlanNum(vlanNum);
        when(globoNetworkService.getVlanInfoFromGloboNetwork(any(Network.class))).thenReturn(vlan);
        manager._globoNetworkService = globoNetworkService;
    }

    private void mockGetGloboNetworkEnvironmentID(long environmentId) {
        GloboNetworkNetworkDao globoNetworkDao = mock(GloboNetworkNetworkDao.class);
        GloboNetworkNetworkVO globonetwork = new GloboNetworkNetworkVO(1L, 1L, environmentId);
        when(globoNetworkDao.findByNetworkId(any(Long.class))).thenReturn(globonetwork);
        manager._globoNetworkDao = globoNetworkDao;
    }

    private AgentManager mockAgentManagerEasySend(Answer answer) {
        AgentManager agentManager = mock(AgentManager.class);
        when(agentManager.easySend(anyLong(), any(Command.class))).thenReturn(answer);
        manager._agentMgr = agentManager;
        return agentManager;
    }

    private void mockGetACLapiHost() {
        HostDao hostDao = mock(HostDao.class);
        when(hostDao.findByTypeNameAndZoneId(anyLong(), eq(Network.Provider.GloboAclApi.getName()), eq(Host.Type.L2Networking))).thenReturn(createMockHost());
        manager._hostDao = hostDao;
    }

    private void mockFindPhyisicalNetwork(PhysicalNetworkVO physicalNetwork) {
        PhysicalNetworkDao physicalNetworkDaoMock = mock(PhysicalNetworkDao.class);
        when(physicalNetworkDaoMock.findById(anyLong())).thenReturn(physicalNetwork);
        manager._physicalNetworkDao = physicalNetworkDaoMock;
    }

    private ResourceManager mockAddHost(Host host) {
        ResourceManager resourceManagerMock = mock(ResourceManager.class);
        when(resourceManagerMock.addHost(eq(1L), any(GloboAclApiResource.class), eq(Host.Type.L2Networking), any(HashMap.class))).thenReturn(host);
        manager._resourceMgr = resourceManagerMock;
        return resourceManagerMock;
    }
}