package com.globo.globonetwork.cloudstack.api.loadbalancer;

import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.LBHealthCheckPolicyVO;
import com.cloud.network.dao.LBStickinessPolicyVO;
import com.cloud.network.lb.LoadBalancingRulesService;
import com.cloud.utils.exception.CloudRuntimeException;

import java.util.HashMap;
import org.apache.cloudstack.api.command.user.loadbalancer.CreateLBHealthCheckPolicyCmd;
import org.apache.cloudstack.api.command.user.loadbalancer.CreateLBStickinessPolicyCmd;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

/**
 * Created by lucas.castro on 11/3/16.
 */
public class CreateGloboLoadBalancerCmdTest {

    CreateGloboLoadBalancerCmd cmd = new CreateGloboLoadBalancerCmd();



    @Test
    public void testCreateHealthcheck() throws ResourceUnavailableException {
        //when
        cmd.setPingPath("/index.html");
        cmd.setEntityId(123l);
        cmd.setEntityUuid("54321");

        CreateLBHealthCheckPolicyCmd  cmd1 = new CreateLBHealthCheckPolicyCmd();
        cmd1.setPingPath("/index.html");
        cmd1.setLbRuleId(123l);

        //mock create
        LoadBalancingRulesService lbService = mock(LoadBalancingRulesService.class);
        LBHealthCheckPolicyVO healthcheckPolicity = new LBHealthCheckPolicyVO(123l, "/index.html",null, 0, 0, 0, 0);
        healthcheckPolicity.setUuid("66");

        when(lbService.createLBHealthCheckPolicy(cmd1)).thenReturn(healthcheckPolicity);
        //mock execute
        when(lbService.applyLBHealthCheckPolicy(cmd1)).thenReturn(true);
        cmd._lbService = lbService;

        //do
        cmd.createHealthcheck();

        //check
        verify(lbService, times(1)).createLBHealthCheckPolicy(cmd1);
        verify(lbService, times(1)).applyLBHealthCheckPolicy(cmd1);
    }

    @Test(expected = RuntimeException.class)
    public void testCreateHealthcheckWhenCreateFail() throws ResourceUnavailableException {
        //when
        cmd.setPingPath("/index.html");
        cmd.setEntityId(123l);
        cmd.setEntityUuid("54321");

        CreateLBHealthCheckPolicyCmd  cmd1 = new CreateLBHealthCheckPolicyCmd();
        cmd1.setPingPath("/index.html");
        cmd1.setLbRuleId(123l);

        //mock create
        LoadBalancingRulesService lbService = mock(LoadBalancingRulesService.class);
        when(lbService.createLBHealthCheckPolicy(cmd1)).thenThrow(new RuntimeException());

        cmd._lbService = lbService;

        try {
            //do
            cmd.createHealthcheck();

        }finally {
            //check
            verify(lbService, times(1)).createLBHealthCheckPolicy(cmd1);
        }
    }


    @Test(expected = CloudRuntimeException.class)
    public void testCreateHealthcheckWhenApplyFail() throws ResourceUnavailableException {
        //when
        cmd.setPingPath("/index.html");
        cmd.setEntityId(123l);
        cmd.setEntityUuid("54321");

        CreateLBHealthCheckPolicyCmd  cmd1 = new CreateLBHealthCheckPolicyCmd();
        cmd1.setPingPath("/index.html");
        cmd1.setLbRuleId(123l);

        //mock create
        LoadBalancingRulesService lbService = mock(LoadBalancingRulesService.class);
        LBHealthCheckPolicyVO healthcheckPolicity = new LBHealthCheckPolicyVO(123l, "/index.html",null, 0, 0, 0, 0);
        healthcheckPolicity.setUuid("66");


        when(lbService.createLBHealthCheckPolicy(cmd1)).thenReturn(healthcheckPolicity);
        //mock execute

        when(lbService.applyLBHealthCheckPolicy(cmd1)).thenThrow(new CloudRuntimeException("error 1"));
        cmd._lbService = lbService;

        try {
            //do
            cmd.createHealthcheck();

        }finally {
            //check
            verify(lbService, times(1)).createLBHealthCheckPolicy(cmd1);
            verify(lbService, times(1)).applyLBHealthCheckPolicy(cmd1);
        }
    }



    @Test
    public void testCreateStickiness() throws ResourceUnavailableException, NetworkRuleConflictException {
        //when
        cmd.setStickinessMethodName("Cookie");
        cmd.setEntityId(123l);
        cmd.setEntityUuid("54321");

        CreateLBStickinessPolicyCmd  cmd1 = new CreateLBStickinessPolicyCmd();
        cmd1.setStickinessMethodName("Cookie");
        cmd1.setLbStickinessPolicyName("Cookie");
        cmd1.setLbRuleId(123l);

        //mock create
        LoadBalancingRulesService lbService = mock(LoadBalancingRulesService.class);
        LBStickinessPolicyVO stickiness = new LBStickinessPolicyVO(123l, "Cookie", "Cookie", new HashMap(),"");
        stickiness.setUuid("66");

        when(lbService.createLBStickinessPolicy(cmd1)).thenReturn(stickiness);
        //mock execute
        when(lbService.applyLBStickinessPolicy(cmd1)).thenReturn(true);
        cmd._lbService = lbService;

        //do
        cmd.createStickiness();

        //check
        verify(lbService, times(1)).createLBStickinessPolicy(cmd1);
        verify(lbService, times(1)).applyLBStickinessPolicy(cmd1);
    }


    @Test(expected = CloudRuntimeException.class)
    public void testCreateStickinessWhenCreateFail() throws ResourceUnavailableException, NetworkRuleConflictException {
        //when
        cmd.setStickinessMethodName("Cookie");
        cmd.setEntityId(123l);
        cmd.setEntityUuid("54321");

        CreateLBStickinessPolicyCmd  cmd1 = new CreateLBStickinessPolicyCmd();
        cmd1.setStickinessMethodName("Cookie");
        cmd1.setLbStickinessPolicyName("Cookie");
        cmd1.setLbRuleId(123l);

        //mock create
        LoadBalancingRulesService lbService = mock(LoadBalancingRulesService.class);
        LBStickinessPolicyVO stickiness = new LBStickinessPolicyVO(123l, "Cookie", "Cookie", new HashMap(),"");
        stickiness.setUuid("66");

        when(lbService.createLBStickinessPolicy(cmd1)).thenThrow(new CloudRuntimeException("error stickiness"));
        cmd._lbService = lbService;

        try {
            //do
            cmd.createStickiness();
        } finally {
            //check
            verify(lbService, times(1)).createLBStickinessPolicy(cmd1);
        }
    }

    @Test
    public void testIsToCreateHealthcheck() {
        boolean tocreate = cmd.isToCreateHealthcheck();
        assertFalse(tocreate);


        cmd.setPingPath("");
        tocreate = cmd.isToCreateHealthcheck();
        assertFalse(tocreate);


        cmd.setPingPath("/index.html");
        tocreate = cmd.isToCreateHealthcheck();
        assertTrue(tocreate);
    }

    @Test
    public void testIsToCreateStickiness() {
        boolean tocreate = cmd.isToCreateStickiness();
        assertFalse(tocreate);


        cmd.setStickinessMethodName("");
        tocreate = cmd.isToCreateStickiness();
        assertFalse(tocreate);


        cmd.setStickinessMethodName("Cookie");
        tocreate = cmd.isToCreateStickiness();
        assertTrue(tocreate);
    }
}