package org.apache.cloudstack.api.command.user.loadbalancer;

import com.cloud.network.lb.LoadBalancingRulesService;

import com.cloud.network.rules.LoadBalancer;

import com.cloud.utils.db.EntityManager;
import junit.framework.TestCase;
import org.apache.cloudstack.api.ResponseGenerator;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.LoadBalancerResponse;
import org.apache.log4j.Logger;

import org.junit.Test;

import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;

public class CreateLoadBalancerRuleCmdTest extends TestCase{

    private static final Logger s_logger = Logger.getLogger(CreateLoadBalancerRuleCmdTest.class);

    @Test
    public void testExecute()  throws Exception{

        CreateLoadBalancerRuleCmd cmd = new CreateLoadBalancerRuleCmd() {
            public Boolean getOpenFirewall() { return false; }
            public Long getEntityId() { return 1l; }
        };

        //mocks
        LoadBalancer lbMock = getLBInstance(1l);

        EntityManager em = Mockito.mock(EntityManager.class);
        Mockito.when(em.findById(LoadBalancer.class, 1l)).thenReturn(lbMock);
        cmd._entityMgr = em;

        LoadBalancerResponse lbResponse = new LoadBalancerResponse();
        lbResponse.setId("1");

        ResponseGenerator responseGenerator = Mockito.mock(ResponseGenerator.class);
        Mockito.when(responseGenerator.createLoadBalancerResponse(lbMock)).thenReturn(lbResponse);

        cmd._responseGenerator = responseGenerator;


        //action
        cmd.execute();


        //check result
        LoadBalancerResponse lbResponseCmd = (LoadBalancerResponse) cmd.getResponseObject();
        assertEquals("createloadbalancerruleresponse", lbResponseCmd.getResponseName());

        assertEquals("1", (String)getPrivateFieldValue(lbResponseCmd, "id"));

        Mockito.verify(em, Mockito.times(1)).findById(LoadBalancer.class, 1l);
        Mockito.verify(responseGenerator, Mockito.times(1)).createLoadBalancerResponse(lbMock);

    }

    @Test
    public void testExecute_rule_not_created()  throws Exception{
        //mocks
        CreateLoadBalancerRuleCmd cmd = new CreateLoadBalancerRuleCmd() {
            public Boolean getOpenFirewall() { return false; }
            public Long getEntityId() { return 23l; }
        };

        EntityManager em = Mockito.mock(EntityManager.class);
        Mockito.when(em.findById(LoadBalancer.class, 23l)).thenReturn(null);
        cmd._entityMgr = em;

        LoadBalancingRulesService lbRService = Mockito.mock(LoadBalancingRulesService.class);
        Mockito.when(lbRService.deleteLoadBalancerRule(23l, false, false)).thenReturn(true);
        cmd._lbService = lbRService;

        try {
            //action
            cmd.execute();

        } catch (ServerApiException e ) {
            LoadBalancerResponse lbResponseCmd = (LoadBalancerResponse) cmd.getResponseObject();
            assertNull(lbResponseCmd);

            Mockito.verify(em, Mockito.times(1)).findById(LoadBalancer.class, 23l);
            Mockito.verify(lbRService, Mockito.times(1)).deleteLoadBalancerRule(23l, false, false);
        } catch (Exception e ) {
            e.printStackTrace();
            fail("shoud be ServerApiException");
        }
    }


    public Object getPrivateFieldValue(Object instance, String value) {
        try {
            for (Field field : instance.getClass().getDeclaredFields()) {
                if (value.equals(field.getName())) {
                    field.setAccessible(true);
                    return field.get(instance);
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return null;
    }

    public LoadBalancer getLBInstance(final Long id ) {
        LoadBalancer x = new LoadBalancer() {

            @Override
            public long getId() {
                return id;
            }

            @Override
            public int getDefaultPortStart() {
                return 0;
            }

            @Override
            public int getDefaultPortEnd() {
                return 0;
            }

            @Override
            public String getXid() {
                return null;
            }

            @Override
            public Integer getSourcePortStart() {
                return null;
            }

            @Override
            public Integer getSourcePortEnd() {
                return null;
            }

            @Override
            public String getProtocol() {
                return null;
            }

            @Override
            public Purpose getPurpose() {
                return null;
            }

            @Override
            public State getState() {
                return null;
            }

            @Override
            public long getNetworkId() {
                return 0;
            }

            @Override
            public Long getSourceIpAddressId() {
                return null;
            }

            @Override
            public Integer getIcmpCode() {
                return null;
            }

            @Override
            public Integer getIcmpType() {
                return null;
            }

            @Override
            public List<String> getSourceCidrList() {
                return null;
            }

            @Override
            public List<String> getDestinationCidrList() {
                return null;
            }

            @Override
            public Long getRelated() {
                return null;
            }

            @Override
            public FirewallRuleType getType() {
                return null;
            }

            @Override
            public TrafficType getTrafficType() {
                return null;
            }

            @Override
            public boolean isDisplay() {
                return false;
            }

            @Override
            public Class<?> getEntityType() {
                return null;
            }

            @Override
            public String getUuid() {
                return null;
            }

            @Override
            public String getName() {
                return null;
            }

            @Override
            public String getDescription() {
                return null;
            }

            @Override
            public String getAlgorithm() {
                return null;
            }

            @Override
            public String getLbProtocol() {
                return null;
            }

            @Override
            public Scheme getScheme() {
                return null;
            }

            @Override
            public long getAccountId() {
                return 0;
            }

            @Override
            public long getDomainId() {
                return 0;
            }
        };


        return x;
    }
}
