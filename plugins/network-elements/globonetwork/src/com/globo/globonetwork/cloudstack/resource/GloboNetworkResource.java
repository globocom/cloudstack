/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.globo.globonetwork.cloudstack.resource;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.utils.Pair;
import com.cloud.utils.StringUtils;
import com.globo.globonetwork.client.exception.GloboNetworkErrorCodeException;
import com.globo.globonetwork.client.model.OptionVipV3;
import com.globo.globonetwork.client.model.VipV3;
import com.globo.globonetwork.cloudstack.commands.CreatePoolCommand;
import com.globo.globonetwork.cloudstack.commands.DeletePoolCommand;
import com.globo.globonetwork.cloudstack.commands.GloboNetworkResourceCommand;
import com.globo.globonetwork.cloudstack.commands.ValidateVipUpdateCommand;
import com.globo.globonetwork.cloudstack.response.CheckDSREnabledResponse;
import com.globo.globonetwork.client.api.ExpectHealthcheckAPI;
import com.globo.globonetwork.client.api.GloboNetworkAPI;
import com.globo.globonetwork.client.api.PoolAPI;
import com.globo.globonetwork.client.model.healthcheck.ExpectHealthcheck;
import com.globo.globonetwork.client.model.pool.PoolV3;
import com.globo.globonetwork.cloudstack.commands.CheckDSREnabled;
import com.globo.globonetwork.cloudstack.commands.GetPoolLBByIdCommand;
import com.globo.globonetwork.cloudstack.commands.ListExpectedHealthchecksCommand;
import com.globo.globonetwork.cloudstack.commands.ListPoolLBCommand;
import com.globo.globonetwork.cloudstack.manager.HealthCheckHelper;
import com.globo.globonetwork.cloudstack.response.GloboNetworkExpectHealthcheckResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.ConfigurationException;

import com.globo.globonetwork.client.model.OptionVip;
import com.globo.globonetwork.client.model.PoolOption;
import com.globo.globonetwork.client.model.VipPoolMap;
import com.globo.globonetwork.cloudstack.commands.ListGloboNetworkLBCacheGroupsCommand;
import com.globo.globonetwork.cloudstack.commands.ListPoolOptionsCommand;
import com.globo.globonetwork.cloudstack.response.GloboNetworkCacheGroupsResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkPoolOptionResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.log4j.Logger;

import com.cloud.agent.IAgentControl;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.MaintainAnswer;
import com.cloud.agent.api.MaintainCommand;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.ReadyAnswer;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.resource.ServerResource;
import com.cloud.utils.component.ManagerBase;
import com.globo.globonetwork.client.exception.GloboNetworkException;
import com.globo.globonetwork.client.model.Environment;
import com.globo.globonetwork.client.model.Equipment;
import com.globo.globonetwork.client.model.Ip;
import com.globo.globonetwork.client.model.Network;
import com.globo.globonetwork.client.model.VipEnvironment;
import com.globo.globonetwork.client.model.Vlan;
import com.globo.globonetwork.cloudstack.commands.AcquireNewIpForLbCommand;
import com.globo.globonetwork.cloudstack.commands.ActivateNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.ApplyVipInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.CreateNewVlanInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.DeallocateVlanFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GetNetworkFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GetVipInfoFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GetVlanInfoFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GloboNetworkErrorAnswer;
import com.globo.globonetwork.cloudstack.commands.ListAllEnvironmentsFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RegisterEquipmentAndIpInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.ReleaseIpFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RemoveNetworkInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RemoveVipFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.UnregisterEquipmentAndIpInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.response.GloboNetworkAllEnvironmentResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkAndIPResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse.Real;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVlanResponse;
import sun.net.util.IPAddressUtil;

public class GloboNetworkResource extends ManagerBase implements ServerResource {
    private String _zoneId;

    private String _guid;

    private String _name;

    private String _username;
    private String _url;
    private String _password;

    private Integer readTimeout = 2*60000;
    private Integer connectTimeout = 1*60000;
    private Integer numberOfRetries = 0;

    private static final Logger s_logger = Logger.getLogger(GloboNetworkResource.class);

    private static final long NETWORK_TYPE = 6; // Rede invalida de equipamentos

    private static final Long EQUIPMENT_TYPE = 10L;

    private static final Integer DEFAULT_REALS_PRIORITY = 0;

    private static final Integer DEFAULT_REAL_WEIGHT = 0;

    private static final Integer DEFAULT_MAX_CONN = 0;
    private static final int DEFAULT_REAL_STATUS = 7;

    protected enum LbAlgorithm {
        RoundRobin("round-robin"), LeastConn("least-conn");

        String globoNetworkBalMethod;

        LbAlgorithm(String globoNetworkBalMethod) {
            this.globoNetworkBalMethod = globoNetworkBalMethod;
        }

        public String getGloboNetworkBalMethod() {
            return globoNetworkBalMethod;
        }
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {

        try {
            _zoneId = (String)params.get("zoneId");
            if (_zoneId == null) {
                throw new ConfigurationException("Unable to find zone");
            }

            _guid = (String)params.get("guid");
            if (_guid == null) {
                throw new ConfigurationException("Unable to find guid");
            }

            _name = (String)params.get("name");
            if (_name == null) {
                throw new ConfigurationException("Unable to find name");
            }

            _url = (String)params.get("url");
            if (_url == null) {
                throw new ConfigurationException("Unable to find url");
            }

            _username = (String)params.get("username");
            if (_username == null) {
                throw new ConfigurationException("Unable to find username");
            }

            _password = (String)params.get("password");
            if (_password == null) {
                throw new ConfigurationException("Unable to find password");
            }


            if (params.containsKey("readTimeout")) {
                readTimeout = Integer.valueOf((String)params.get("readTimeout"));
            }

            if (params.containsKey("connectTimeout")) {
                connectTimeout = Integer.valueOf((String)params.get("connectTimeout"));
            }

            if (params.containsKey("numberOfRetries")) {
                numberOfRetries = Integer.valueOf((String)params.get("numberOfRetries"));
            }

            return true;
        } catch (NumberFormatException e) {
            s_logger.error("Invalid number in configuration parameters", e);
            throw new ConfigurationException("Invalid number in configuration parameters: " + e);
        }
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public Type getType() {
        return Host.Type.L2Networking;
    }

    @Override
    public StartupCommand[] initialize() {
        StartupCommand cmd = new StartupCommand(getType());
        cmd.setName(_name);
        cmd.setGuid(_guid);
        cmd.setDataCenter(_zoneId);
        cmd.setPod("");
        cmd.setPrivateIpAddress("");
        cmd.setStorageIpAddress("");
        cmd.setVersion(GloboNetworkResource.class.getPackage().getImplementationVersion());

        return new StartupCommand[] {cmd};
    }

    @Override
    public PingCommand getCurrentStatus(long id) {
        return new PingCommand(getType(), id);
    }

    @Override
    public void disconnected() {
    }

    @Override
    public IAgentControl getAgentControl() {
        return null;
    }

    @Override
    public void setAgentControl(IAgentControl agentControl) {
    }

    @Override
    public Answer executeRequest(Command cmd) {
        if (cmd instanceof GloboNetworkResourceCommand) {
            GloboNetworkResourceCommand globoNetworkResourceCommand = (GloboNetworkResourceCommand)cmd;
            GloboNetworkAPI api = getNewGloboNetworkAPI();
            return globoNetworkResourceCommand.execute(api);
        }

        if (cmd instanceof ReadyCommand) {
            return new ReadyAnswer((ReadyCommand)cmd);
        } else if (cmd instanceof MaintainCommand) {
            return new MaintainAnswer((MaintainCommand)cmd);
        } else if (cmd instanceof GetVlanInfoFromGloboNetworkCommand) {
            return execute((GetVlanInfoFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof CreateNewVlanInGloboNetworkCommand) {
            return execute((CreateNewVlanInGloboNetworkCommand)cmd);
        } else if (cmd instanceof ActivateNetworkCommand) {
            return execute((ActivateNetworkCommand)cmd);
        } else if (cmd instanceof ListAllEnvironmentsFromGloboNetworkCommand) {
            return execute((ListAllEnvironmentsFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof RemoveNetworkInGloboNetworkCommand) {
            return execute((RemoveNetworkInGloboNetworkCommand)cmd);
        } else if (cmd instanceof DeallocateVlanFromGloboNetworkCommand) {
            return execute((DeallocateVlanFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof RegisterEquipmentAndIpInGloboNetworkCommand) {
            return execute((RegisterEquipmentAndIpInGloboNetworkCommand)cmd);
        } else if (cmd instanceof UnregisterEquipmentAndIpInGloboNetworkCommand) {
            return execute((UnregisterEquipmentAndIpInGloboNetworkCommand)cmd);
        } else if (cmd instanceof GetVipInfoFromGloboNetworkCommand) {
            return execute((GetVipInfoFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof ValidateVipUpdateCommand) {
            return execute((ValidateVipUpdateCommand)cmd);
        } else if (cmd instanceof RemoveVipFromGloboNetworkCommand) {
            return execute((RemoveVipFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof AcquireNewIpForLbCommand) {
            return execute((AcquireNewIpForLbCommand)cmd);
        } else if (cmd instanceof ReleaseIpFromGloboNetworkCommand) {
            return execute((ReleaseIpFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof ApplyVipInGloboNetworkCommand) {
            return execute((ApplyVipInGloboNetworkCommand)cmd);
        } else if (cmd instanceof GetNetworkFromGloboNetworkCommand) {
            return execute((GetNetworkFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof ListGloboNetworkLBCacheGroupsCommand) {
            return execute((ListGloboNetworkLBCacheGroupsCommand) cmd);
        }else if (cmd instanceof ListPoolOptionsCommand){
            return execute((ListPoolOptionsCommand) cmd);
        }else if (cmd instanceof ListPoolLBCommand) {
            return execute((ListPoolLBCommand) cmd);
        }else if (cmd instanceof GetPoolLBByIdCommand) {
            return execute((GetPoolLBByIdCommand) cmd);
        }else if (cmd instanceof ListExpectedHealthchecksCommand) {
            return execute((ListExpectedHealthchecksCommand) cmd);
        }else if (cmd instanceof CreatePoolCommand) {
            return execute((CreatePoolCommand) cmd);
        }else if (cmd instanceof DeletePoolCommand) {
            return execute((DeletePoolCommand) cmd);
        }else if (cmd instanceof CheckDSREnabled) {
            return execute((CheckDSREnabled) cmd);
        }
        return Answer.createUnsupportedCommandAnswer(cmd);
    }

    private Answer execute(ListExpectedHealthchecksCommand cmd) {
        try{
            ExpectHealthcheckAPI api = getNewGloboNetworkAPI().getExpectHealthcheckAPI();

            List<ExpectHealthcheck> expectHealthchecks = api.listHealthcheck();

            List<GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck> result = new ArrayList<GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck>();

            for (ExpectHealthcheck expectHealthcheck : expectHealthchecks){
                result.add(new GloboNetworkExpectHealthcheckResponse.ExpectedHealthcheck(expectHealthcheck.getId(), expectHealthcheck.getExpect()));
            }

            GloboNetworkExpectHealthcheckResponse response = new GloboNetworkExpectHealthcheckResponse(result);
            return response;
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        } catch (Exception e) {
            s_logger.error("Generic error accessing GloboNetwork while update pool", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    private Answer execute(CreatePoolCommand cmd) {
        PoolV3 pool = null;
        GloboNetworkAPI gnAPI = getNewGloboNetworkAPI();

        try{
            VipAPIFacade vipAPIFacade = createVipAPIFacade(cmd.getVipId(), gnAPI);

            if(vipAPIFacade.hasVip()) {
                VipInfoHelper vipInfo = getVipInfos(gnAPI, cmd.getVipEnvironment(), cmd.getVipIp());

                pool = createPool(
                    cmd.getPublicPort(), cmd.getPrivatePort(), vipInfo.getEnvironment(), cmd.getVipName(),
                    cmd.getBalacingAlgorithm(), cmd.getL4protocol().name(), null, null, null,
                    DEFAULT_MAX_CONN, cmd.getServiceDownAction(), buildPoolMembers(gnAPI, cmd.getReals()), cmd.getRegion()
                );

                pool = gnAPI.getPoolAPI().save(pool);

                VipEnvironment vipEnv = gnAPI.getVipEnvironmentAPI().search(cmd.getVipEnvironment(), null, null, null);
                vipAPIFacade.addPool(vipEnv, cmd.getPublicPort(), cmd.getL4protocol().getNetworkApiOptionValue(), cmd.getL7protocol().getNetworkApiOptionValue(), pool);
                return new GloboNetworkPoolResponse(poolV3FromNetworkApi(pool));
            }
            return new GloboNetworkPoolResponse(cmd, true, "", new GloboNetworkPoolResponse.Pool());
        } catch (GloboNetworkException e) {
            rollbackPoolCreation(pool, gnAPI);
            return handleGloboNetworkException(cmd, e);
        } catch (Exception e) {
            rollbackPoolCreation(pool, gnAPI);
            s_logger.error("Generic error accessing GloboNetwork while creating new pool", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    private void rollbackPoolCreation(PoolV3 pool, GloboNetworkAPI gnAPI){
        if(pool != null && pool.getId() != null){
            try {
                gnAPI.getPoolAPI().deleteV3(pool.getId());
            } catch (GloboNetworkException e) {
                s_logger.error("Error rollbacking pool creation", e);
            }
        }
    }

    private Answer execute(DeletePoolCommand cmd) {
        GloboNetworkAPI gnAPI = getNewGloboNetworkAPI();
        VipAPIFacade vipAPIFacade = null;

        try{
            vipAPIFacade = createVipAPIFacade(cmd.getVipId(), gnAPI);
            vipAPIFacade.removePool(cmd.getPoolId());

            PoolAPI poolAPI = gnAPI.getPoolAPI();
            PoolV3 pool = poolAPI.getById(cmd.getPoolId());
            if(pool.getPoolCreated()){
                poolAPI.undeployV3(pool.getId());
            }

            poolAPI.deleteV3(cmd.getPoolId());

            return new Answer(cmd, true, "Pool successfuly removed");
        } catch (GloboNetworkException e) {
            rollbackPoolRemoval(cmd.getPoolId(), cmd.getVipPort(), vipAPIFacade, gnAPI);
            return handleGloboNetworkException(cmd, e);
        } catch (Exception e) {
            rollbackPoolRemoval(cmd.getPoolId(), cmd.getVipPort(), vipAPIFacade, gnAPI);
            s_logger.error("Generic error accessing GloboNetwork while creating new pool", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    private void rollbackPoolRemoval(Long poolId, Integer vipPort, VipAPIFacade vipAPIFacade, GloboNetworkAPI gnAPI){
        try {
            PoolV3 pool = gnAPI.getPoolAPI().getById(poolId);
            if(pool != null) {
                VipEnvironment vipEnv = gnAPI.getVipEnvironmentAPI().search(vipAPIFacade.getVip().getEnvironmentVipId(), null, null, null);
                String l4ProtocolString = HealthCheckHelper.getL4Protocol(pool.getHealthcheck().getHealthcheckType(), vipPort);
                String l7ProtocolString = HealthCheckHelper.getL7Protocol(pool.getHealthcheck().getHealthcheckType(), vipPort);
                vipAPIFacade.addPool(vipEnv, vipPort, l4ProtocolString, l7ProtocolString,  pool);
            }
        } catch (GloboNetworkException e) {
            s_logger.error("Error rollbacking pool removal", e);
        }
    }


    private Answer execute(GetPoolLBByIdCommand cmd) {
        try {
            PoolV3 pool = getNewGloboNetworkAPI().getPoolAPI().getById(cmd.getPoolId());

            GloboNetworkPoolResponse.Pool poolCS = poolFromNetworkApi(pool, null);
            GloboNetworkPoolResponse answer = new GloboNetworkPoolResponse(cmd, true, "", poolCS);

            return answer;
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        } catch (Exception e) {
            s_logger.error("Generic error accessing GloboNetwork while getPoolById", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    private Answer execute(ListPoolLBCommand cmd) {
        try {
            GloboNetworkAPI globoNetworkAPI = getNewGloboNetworkAPI();
            PoolAPI poolAPI = globoNetworkAPI.getPoolAPI();

            VipAPIFacade apiFacade = this.createVipAPIFacade(cmd.getVipId(), globoNetworkAPI);
            VipV3 vip = apiFacade.getVip();

            List<OptionVipV3> optionVipV3s = globoNetworkAPI.getOptionVipV3API().listOptions(vip.getEnvironmentVipId());

            List<GloboNetworkPoolResponse.Pool> pools = new ArrayList<>();
            for(VipV3.Port port : vip.getPorts()){
                Integer vipPort = port.getPort();
                VipV3.PortOptions options = port.getOptions();
                String optionL4 = findProtocol(optionVipV3s, options.getL4ProtocolId());
                String optionL7 = findProtocol(optionVipV3s, options.getL7ProtocolId());

                for(VipV3.Pool p : port.getPools()){
                    PoolV3 pool = poolAPI.getById(p.getPoolId());
                    GloboNetworkPoolResponse.Pool poolCS = poolFromNetworkApi(pool, vipPort);
                    poolCS.setL4protocol(optionL4);
                    poolCS.setL7protocol(optionL7);
                    pools.add(poolCS);

                }
            }

            GloboNetworkPoolResponse answer = new GloboNetworkPoolResponse(cmd, true, "", pools);
            return answer;
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        } catch (Exception e) {
            s_logger.error("Generic error accessing GloboNetwork", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    private String findProtocol(List<OptionVipV3> optionVipV3s, Long optionId) {

        for (OptionVipV3 option : optionVipV3s) {
            if (option.getId().equals(optionId)) {
                return option.getName();
            }
        }

        return null;
    }

    private static GloboNetworkPoolResponse.Pool poolFromNetworkApi(PoolV3 poolNetworkApi, Integer vipPort) throws GloboNetworkException {
        GloboNetworkPoolResponse.Pool pool = new GloboNetworkPoolResponse.Pool();

        pool.setId(poolNetworkApi.getId());
        pool.setIdentifier(poolNetworkApi.getIdentifier());
        pool.setPort(poolNetworkApi.getDefaultPort());
        pool.setLbMethod(poolNetworkApi.getLbMethod());
        pool.setMaxconn(poolNetworkApi.getMaxconn());
        pool.setVipPort(vipPort);


        PoolV3.Healthcheck healthcheck = poolNetworkApi.getHealthcheck();

        if (healthcheck != null) {
            pool.setHealthcheck(healthcheck.getHealthcheckRequest());
            pool.setHealthcheckType(healthcheck.getHealthcheckType());
            pool.setExpectedHealthcheck(healthcheck.getExpectedHealthcheck());
        }

        return pool;
    }
    public static GloboNetworkPoolResponse.Pool poolV3FromNetworkApi(PoolV3 poolNetworkApi) throws GloboNetworkException {
        GloboNetworkPoolResponse.Pool pool = new GloboNetworkPoolResponse.Pool();

        pool.setId(poolNetworkApi.getId());
        pool.setIdentifier(poolNetworkApi.getIdentifier());
        pool.setPort(poolNetworkApi.getDefaultPort());
        pool.setLbMethod(poolNetworkApi.getLbMethod());
        pool.setMaxconn(poolNetworkApi.getMaxconn());

        PoolV3.Healthcheck healthcheck = poolNetworkApi.getHealthcheck();

        if ( healthcheck != null ) {
            pool.setHealthcheck(healthcheck.getHealthcheckRequest());
            pool.setHealthcheckType(healthcheck.getHealthcheckType());
            pool.setExpectedHealthcheck(healthcheck.getExpectedHealthcheck());
        }

        return pool;
    }

    private Answer execute(ListPoolOptionsCommand cmd) {
        try {
            GloboNetworkAPI gnAPI = getNewGloboNetworkAPI();
            Network network = gnAPI.getNetworkJsonAPI().listVipNetworks(cmd.getGloboNetworkLBEnvironmentId(), false).get(0);
            Vlan vlan = gnAPI.getVlanAPI().getById(network.getVlanId());
            List<PoolOption> poolOptions = gnAPI.getPoolAPI().listPoolOptionsV3(vlan.getEnvironment(), cmd.getType());

            List<GloboNetworkPoolOptionResponse.PoolOption> options = new ArrayList<>();
            for(PoolOption option : poolOptions){
                options.add(new GloboNetworkPoolOptionResponse.PoolOption(option.getId(), option.getName()));
            }
            return new GloboNetworkPoolOptionResponse(cmd, options);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        } catch (IOException e) {
            s_logger.error("Generic error accessing GloboNetwork", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    private Answer execute(ListGloboNetworkLBCacheGroupsCommand cmd) {
        try {
            List<OptionVip> optionVips = getNewGloboNetworkAPI().getOptionVipAPI().listCacheGroups(cmd.getLBEnvironmentId());
            List<String> cacheGroups = new ArrayList<String>();
            if (optionVips != null) {
                for(OptionVip optionVip : optionVips) {
                    cacheGroups.add(optionVip.getCacheGroup());
                }
            }
            // if optionVips is null, then an empty list will be returned
            return new GloboNetworkCacheGroupsResponse(cmd, cacheGroups);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    private Answer execute(GetNetworkFromGloboNetworkCommand cmd) {
        GloboNetworkAPI gnAPI = getNewGloboNetworkAPI();
        try {
            if (cmd.getNetworkId() == null) {
                return new Answer(cmd, false, "Invalid network ID");
            }

            Network network = gnAPI.getNetworkAPI().getNetwork(cmd.getNetworkId(), cmd.isv6());
            if (network == null) {
                return new Answer(cmd, false, "Network with ID " + cmd.getNetworkId() + " not found in GloboNetwork");
            }

            return this.createNetworkResponse(network, cmd, gnAPI);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    private Answer handleGloboNetworkException(Command cmd, GloboNetworkException e) {
        return GloboNetworkResourceCommand.handleGloboNetworkException(cmd, e);
    }

    public Answer execute(RemoveVipFromGloboNetworkCommand cmd) {
        GloboNetworkAPI gnAPI = getNewGloboNetworkAPI();

        if (cmd.getVipId() == null) {
            return new Answer(cmd, true, "Vip request was previously removed from GloboNetwork");
        }
        try {
            VipAPIFacade vipAPIFacade = this.createVipAPIFacade(cmd.getVipId(), gnAPI);
            if (!vipAPIFacade.hasVip()) {
                return new Answer(cmd, true, "Vip request " + cmd.getVipId() + " was previously removed from GloboNetwork");
            }

            vipAPIFacade.undeploy();

            s_logger.info("Requesting GloboNetwork to delete vip vip_id=" + cmd.getVipId() + " keepIp=" + cmd.isKeepIp());
            vipAPIFacade.delete(cmd.isKeepIp());

            return new Answer(cmd);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(GetVlanInfoFromGloboNetworkCommand cmd) {
        try {
            Vlan vlan = getNewGloboNetworkAPI().getVlanAPI().getById(cmd.getVlanId());
            return createResponse(vlan, cmd);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(CreateNewVlanInGloboNetworkCommand cmd) {
        GloboNetworkAPI gnAPI = getNewGloboNetworkAPI();

        Vlan vlan = null;
        try {
            vlan = gnAPI.getVlanAPI().allocateWithoutNetwork(cmd.getGloboNetworkEnvironmentId(), cmd.getVlanName(), cmd.getVlanDescription());

            /*Network network = */gnAPI.getNetworkAPI().addNetwork(vlan.getId(), Long.valueOf(NETWORK_TYPE), null, cmd.isIpv6(), cmd.getSubnet());

            // Bug in GloboNetworkApi: I need to have a second call to get networkid
            vlan = gnAPI.getVlanAPI().getById(vlan.getId());
            return createResponse(vlan, cmd);
        } catch (GloboNetworkException e) {
            if (vlan != null) {
                try {
                    gnAPI.getVlanAPI().deallocate(vlan.getId());
                } catch (GloboNetworkException ex) {
                    s_logger.error("Error deallocating vlan " + vlan.getId() + "from GloboNetwork.");
                }
            }
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(ActivateNetworkCommand cmd) {
        try {
            getNewGloboNetworkAPI().getNetworkJsonAPI().createNetworks(cmd.getNetworkId(), cmd.isv6());
            return new Answer(cmd, true, "Network created");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(ListAllEnvironmentsFromGloboNetworkCommand cmd) {
        try {
            List<Environment> apiEnvironmentList = getNewGloboNetworkAPI().getEnvironmentAPI().listAll();

            List<GloboNetworkAllEnvironmentResponse.Environment> environmentList = new ArrayList<GloboNetworkAllEnvironmentResponse.Environment>(apiEnvironmentList.size());
            for (Environment apiEnvironment : apiEnvironmentList) {
                GloboNetworkAllEnvironmentResponse.Environment environment = new GloboNetworkAllEnvironmentResponse.Environment();
                environment.setId(apiEnvironment.getId());
                environment.setDcDivisionName(apiEnvironment.getDcDivisionName());
                environment.setL3GroupName(apiEnvironment.getL3GroupName());
                environment.setLogicalEnvironmentName(apiEnvironment.getLogicalEnvironmentName());
                environmentList.add(environment);
            }

            return new GloboNetworkAllEnvironmentResponse(cmd, environmentList);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(RemoveNetworkInGloboNetworkCommand cmd) {
        try {
            getNewGloboNetworkAPI().getNetworkJsonAPI().removeNetwork(cmd.getNetworkId(), cmd.isIpv6());
            return new Answer(cmd, true, "Network removed");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(DeallocateVlanFromGloboNetworkCommand cmd) {
        try {
            getNewGloboNetworkAPI().getVlanAPI().deallocate(cmd.getVlanId());
            return new Answer(cmd, true, "Vlan deallocated");
        } catch (GloboNetworkException e) {
            if (e instanceof GloboNetworkErrorCodeException) {
                GloboNetworkErrorCodeException ex = (GloboNetworkErrorCodeException)e;
                if (ex.getCode() == GloboNetworkErrorCodeException.VLAN_NOT_REGISTERED) {
                    return new Answer(cmd, true, "Vlan already has been deallocated");
                }
            }
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(RegisterEquipmentAndIpInGloboNetworkCommand cmd) {
        GloboNetworkAPI gnAPI = getNewGloboNetworkAPI();
        try {
            Equipment equipment = gnAPI.getEquipmentAPI().listByName(cmd.getVmName());
            if (equipment == null) {
                s_logger.info("Registering virtualmachine " + cmd.getVmName() + " in GloboNetwork");
                // Equipment (VM) does not exist, create it
                equipment = gnAPI.getEquipmentAPI().insert(cmd.getVmName(), EQUIPMENT_TYPE, cmd.getEquipmentModelId(), cmd.getEquipmentGroupId());
            }

            Vlan vlan = gnAPI.getVlanAPI().getById(cmd.getVlanId());

            // Make sure this vlan has only one IPv4/IPv6 network associated to it
            if (vlan.getNetworks().size() == 0) {
                return new Answer(cmd, false, "No IPv4/IPv6 networks in this vlan");
            } else if (vlan.getNetworks().size() > 1) {
                return new Answer(cmd, false, "Multiple IPv4/IPv6 networks in this vlan");
            }
            Network network = vlan.getNetworks().get(0);

            Ip ip = gnAPI.getIpAPI().findByIpAndEnvironment(cmd.getNicIp(), cmd.getEnvironmentId(), network.isv6());
            if (ip == null) {
                // Doesn't exist, create it
                ip = gnAPI.getIpAPI().saveIp(cmd.getNicIp(), equipment.getId(), cmd.getNicDescription(), network.getId(), network.isv6());
            } else {
                ip = gnAPI.getIpAPI().getIp(ip.getId(), false);
                if (!ip.getEquipments().contains(cmd.getVmName())) {
                    gnAPI.getIpAPI().assocIp(ip.getId(), equipment.getId(), network.getId(), network.isv6(), cmd.getNicDescription());
                }
            }

            if (ip == null) {
                return new Answer(cmd, false, "Could not register NIC in GloboNetwork");
            }

            return new Answer(cmd, true, "NIC " + cmd.getNicIp() + " registered successfully in GloboNetwork");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(UnregisterEquipmentAndIpInGloboNetworkCommand cmd) {
        GloboNetworkAPI gnAPI = getNewGloboNetworkAPI();
        try {
            Equipment equipment = gnAPI.getEquipmentAPI().listByName(cmd.getVmName());
            if (equipment == null) {
                s_logger.warn("VM was removed from GloboNetwork before being destroyed in Cloudstack. This is not critical, logging inconsistency: VM UUID " + cmd.getVmName());
                return new Answer(cmd);
            }

            if (cmd.getEnvironmentId() != null && cmd.getNicIp() != null) {
                Ip ip = gnAPI.getIpAPI().findByIpAndEnvironment(cmd.getNicIp(), cmd.getEnvironmentId(), cmd.isv6());
                if (ip == null) {
                    // Doesn't exist, ignore
                    s_logger.warn("IP was removed from GloboNetwork before being destroyed in Cloudstack. This is not critical, logging inconsistency: IP " + cmd.getNicIp());
                } else {
                    gnAPI.getEquipmentAPI().removeIP(equipment.getId(), ip.getId(), cmd.isv6());
                }
            }

            // if there are no more IPs in equipment, remove it.
            List<Ip> ipList = gnAPI.getIpAPI().findIpsByEquipment(equipment.getId());
            if (ipList.size() == 0) {
                gnAPI.getEquipmentAPI().delete(equipment.getId());
            }

            return new Answer(cmd, true, "NIC " + cmd.getNicIp() + " deregistered successfully in GloboNetwork");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(ReleaseIpFromGloboNetworkCommand cmd) {
        GloboNetworkAPI gnAPI = getNewGloboNetworkAPI();
        try {
            Ip ip = gnAPI.getIpAPI().checkVipIp(cmd.getIp(), cmd.getVipEnvironmentId(), cmd.isv6());
            if (ip == null) {
                // Doesn't exist, ignore
                s_logger.warn("IP was removed from GloboNetwork before being destroyed in Cloudstack. This is not critical.");
            } else {
                gnAPI.getIpAPI().deleteIp(ip.getId(), cmd.isv6());
            }
            return new Answer(cmd, true, "IP deleted successfully from GloboNetwork");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(GetVipInfoFromGloboNetworkCommand cmd) {
        GloboNetworkAPI gnAPI = getNewGloboNetworkAPI();
        try {
            VipAPIFacade apiFacade = createVipAPIFacade(cmd.getVipId(), gnAPI);
            return createVipResponse(apiFacade.getVip(), cmd, gnAPI);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(ValidateVipUpdateCommand cmd){
        GloboNetworkAPI gnAPI = getNewGloboNetworkAPI();
        try {
            VipAPIFacade apiFacade = createVipAPIFacade(cmd.getVipId(), gnAPI);
            VipV3 vip = apiFacade.getVip();
            Ip ip = gnAPI.getIpAPI().getIp(vip.getIpv4Id(), false);

            if (!cmd.getIp().equals(ip.getIpString())) {
                return new Answer(cmd, false, "You can create only 1 lb rule per IP.");
            }

            if (!vip.getName().equals(cmd.getName())) {
                return new Answer(cmd, false, "It is not allowed to change load balancer name in GloboNetwork");
            }
            return new Answer(cmd, true, null);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(AcquireNewIpForLbCommand cmd) {
        final GloboNetworkAPI gnAPI = getNewGloboNetworkAPI();
        try {
            long globoNetworkLBEnvironmentId = cmd.getGloboNetworkLBEnvironmentId();
            Ip globoIp = gnAPI.getIpAPI().getAvailableIpForVip(globoNetworkLBEnvironmentId, cmd.getDescription(), cmd.isv6());
            if (globoIp == null) {
                return new Answer(cmd, false, "No available ip address for load balancer environment network " + globoNetworkLBEnvironmentId);
            }

            // get network information
            Long networkId = globoIp.getNetworkId();
            Network network = gnAPI.getNetworkAPI().getNetwork(networkId, cmd.isv6());
            if (network == null) {
                return new Answer(cmd, false, "Network with id " + networkId + " not found");
            }

            GloboNetworkAndIPResponse answer = (GloboNetworkAndIPResponse)this.createNetworkResponse(network, cmd, gnAPI);

            // ip information
            answer.setIp(globoIp.getIpString());
            answer.setIpId(globoIp.getId());

            return answer;
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(CheckDSREnabled cmd) {
        try {
            GloboNetworkAPI gnApi = getNewGloboNetworkAPI();
            VipAPIFacade vipAPIFacade = this.createVipAPIFacade(null, gnApi);
            if(vipAPIFacade.isDSRVipEnabled(cmd.getVipEnvironmentId())){
                return new CheckDSREnabledResponse(cmd, true, true, "");
            }else{
                return new CheckDSREnabledResponse(cmd, true, false, "This VIP environment does not support DSR");
            }
        } catch (GloboNetworkException e) {
            return new CheckDSREnabledResponse(cmd, false, false, "Error checking VIP environment configuration");
        }
    }

    public Answer execute(ApplyVipInGloboNetworkCommand cmd) {
        List<VipPoolMap> vipPoolMapping = new ArrayList<>();
        VipAPIFacade vipAPIFacade = null;
        GloboNetworkAPI gnApi = getNewGloboNetworkAPI();

        try {
            s_logger.debug("[ApplyVip_" + cmd.getHost() + "] Vip_id: " + cmd.getVipId() + " ip: " + cmd.getIpv4() + " envId: " + cmd.getVipEnvironmentId());
            Long start = new Date().getTime();

            vipAPIFacade = this.createVipAPIFacade(cmd.getVipId(), gnApi);
            VipInfoHelper vipInfo = getVipInfos(gnApi, cmd.getVipEnvironmentId(), cmd.getIpv4());
            List<PoolV3.PoolMember> poolMembers = buildPoolMembers(gnApi,cmd.getRealList());
            List<Long> poolIds = vipAPIFacade.getPoolIds();

            if(poolIds.isEmpty()){
                vipPoolMapping = this.createPools(gnApi, poolMembers, vipInfo.getEnvironment(), cmd);
            }else{
                this.updatePools(gnApi, poolIds, poolMembers, cmd.getPortPairs());
            }

            if (!vipAPIFacade.hasVip()) {
                vipAPIFacade.save(cmd, cmd.getHost(), vipInfo.vipEnvironment, vipInfo.vipIp, vipPoolMapping);
            } else {
                vipAPIFacade.update(cmd, vipInfo.vipIp, vipPoolMapping);
            }

            vipAPIFacade.deploy();

            Answer vipResponse = vipAPIFacade.createVipResponse(cmd);
            Long time = new Date().getTime() - start;

            s_logger.debug("[ApplyVip END] Vip: " + cmd.getHost() + ", ip: " + cmd.getIpv4() +", Operation time: " + time + " ms");
            return vipResponse;
        }catch (GloboNetworkException e) {
            rollbackVipCreation(gnApi, cmd, vipAPIFacade, vipPoolMapping);
            return handleGloboNetworkException(cmd, e);
        } catch (InvalidParameterValueException e){
            s_logger.error("Error", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    private void rollbackVipCreation(GloboNetworkAPI gnApi, ApplyVipInGloboNetworkCommand cmd, VipAPIFacade vipAPIFacade, List<VipPoolMap> poolMappings){
        if(vipAPIFacade != null && !vipAPIFacade.hasVip() && cmd.getVipId() == null && poolMappings != null){
            List<Long> ids = new ArrayList<>();
            for(VipPoolMap vipPoolMap : poolMappings){
                ids.add(vipPoolMap.getPoolId());
            }
            try {
                gnApi.getPoolAPI().deleteV3(ids);
            } catch (GloboNetworkException e) {
                s_logger.error("It was not possible to cleanup pools after failed vip creation", e);
            }
        }
    }

    public static String buildPoolName(String region, String host, Integer vipPort, Integer realport) {
        return "ACS_POOL_" + region + "_" + host + "_" + vipPort +  "_" + realport;
    }

    protected LbAlgorithm getBalancingAlgorithm(String methodBal) {
        LbAlgorithm lbAlgorithm;
        if ("roundrobin".equals(methodBal)) {
            lbAlgorithm = LbAlgorithm.RoundRobin;
        } else if ("leastconn".equals(methodBal)) {
            lbAlgorithm = LbAlgorithm.LeastConn;
        } else {
            throw new InvalidParameterValueException("Invalid balancing method provided.");
        }
        return lbAlgorithm;
    }

    protected List<PoolV3> findPoolsByPort(Integer port, List<PoolV3> pools) {
        List<PoolV3> poolsFound = new ArrayList<>();
        if(pools != null) {
            for (PoolV3 pool : pools) {
                if (port.equals(pool.getDefaultPort())) {
                    poolsFound.add(pool);
                }
            }
        }
        return poolsFound;
    }

    protected Answer createVipResponse(VipV3 vip, GetVipInfoFromGloboNetworkCommand cmd, GloboNetworkAPI gnAPI) throws GloboNetworkException {
        Set<String> servicePorts = new LinkedHashSet<>();
        OptionVipV3 cacheOption = gnAPI.getOptionVipV3API().findOptionsById(vip.getEnvironmentVipId(), vip.getOptions().getCacheGroupId());
        String cache = cacheOption != null ? cacheOption.getName() : null;
        OptionVipV3 persistenceOption = gnAPI.getOptionVipV3API().findOptionsById(vip.getEnvironmentVipId(), vip.getOptions().getPersistenceId());
        String persistence = persistenceOption != null ? persistenceOption.getName() : null;
        String balancingMethod = null;
        String healthcheckType = null;
        String healthcheckString = null;
        Integer maxConn = null;
        List<PoolV3.PoolMember> members = new ArrayList<>();

        for(VipV3.Port port : vip.getPorts()){
            for(VipV3.Pool p : port.getPools()){
                PoolV3 pool = gnAPI.getPoolAPI().getById(p.getPoolId());
                servicePorts.add(port.getPort() + ":" + pool.getDefaultPort());
                members.addAll(pool.getPoolMembers());

                balancingMethod = pool.getLbMethod();
                healthcheckType = pool.getHealthcheck().getHealthcheckType();
                healthcheckString = pool.getHealthcheck().getHealthcheckRequest();
                maxConn = pool.getMaxconn();
            }
        }

        Map<Long, Real> reals = new HashMap<>();
        for (PoolV3.PoolMember real : members) {
            Real realResponse = reals.get(real.getIpId());
            if (realResponse == null) {
                realResponse = new Real();
                realResponse.setIp(real.getIpFormated());
                realResponse.setVmName(real.getEquipmentName());
                realResponse.setPorts(new ArrayList<>(servicePorts));
                reals.put(real.getIpId(), realResponse);
            }
        }

        Ip ip = gnAPI.getIpAPI().getIp(vip.getIpv4Id(), false);

        return new GloboNetworkVipResponse(
            cmd, vip.getId(), vip.getName(), ip.getIpString(), vip.getIpv4Id(), vip.getEnvironmentVipId(), null,
            cache, balancingMethod, persistence, healthcheckType, healthcheckString, maxConn, servicePorts,
            reals.values(), vip.getCreated()
        );
    }

    private Answer createResponse(Vlan vlan, Command cmd) {

        if (vlan.getIpv4Networks().isEmpty() && vlan.getIpv6Networks().isEmpty()) {
            // Error code 116 from GloboNetwork: 116 : VlanNaoExisteError,
            return new GloboNetworkErrorAnswer(cmd, 116, "No networks in this VLAN");
        }

        String vlanName = vlan.getName();
        String vlanDescription = vlan.getDescription();
        Long vlanId = vlan.getId();
        Long vlanNum = vlan.getVlanNum();
        Network network = vlan.getNetworks().get(0);

        return new GloboNetworkVlanResponse(cmd, vlanId, vlanName, vlanDescription, vlanNum, network.getNetworkAddressAsString(),
                network.getMaskAsString(), network.getId(), network.getActive(), network.getBlock(), network.isv6());
    }

    private Answer createNetworkResponse(Network network, Command cmd, GloboNetworkAPI gnAPI) throws GloboNetworkException {
        GloboNetworkAndIPResponse answer = new GloboNetworkAndIPResponse(cmd);
        answer.setNetworkId(network.getId());
        answer.setVipEnvironmentId(network.getVipEnvironmentId());
        answer.setNetworkAddress(network.getNetworkAddressAsString());
        answer.setNetworkMask(network.getMaskAsString());
        answer.setActive(Boolean.TRUE.equals(network.getActive()));
        answer.setNetworkCidr(network.getNetworkAddressAsString() + "/" + network.getBlock());
        answer.setIsv6(network.isv6());

        // get vlan information
        Long vlanId = network.getVlanId();
        Vlan vlan = gnAPI.getVlanAPI().getById(vlanId);
        if (vlan == null) {
            return new Answer(cmd, false, "Vlan with id " + vlanId + " not found");
        }
        answer.setVlanId(vlanId);
        answer.setVlanName(vlan.getName());
        answer.setVlanDescription(vlan.getDescription());
        answer.setVlanNum(vlan.getVlanNum().intValue());
        return answer;
    }

    public enum PersistenceMethod {
        NONE("(nenhum)", "None"),
        COOKIE("cookie", "Cookie"),
        SOURCE_IP("source-ip", "Source-ip"),
        SOURCE_IP_PERSISTENCE_PORTS("source-ip com persist. entre portas", "Source-ip with persistence between ports"),
        PRIORITY_FAILOVER("Priority_Failover", "Priority Failover");


        private String networkAPICode;
        private String description;
        private  PersistenceMethod(String networkAPICode, String description){
            this.networkAPICode = networkAPICode;
            this.description = description;
        }
        public static String fromPersistencePolicy(LoadBalancingRule.LbStickinessPolicy policy) {
            if (policy == null) {
                return PersistenceMethod.NONE.networkAPICode;
            }

            for (PersistenceMethod persistenceMethod : PersistenceMethod.values()) {
                if (persistenceMethod.description.equals(policy.getMethodName())) {
                    return persistenceMethod.networkAPICode;
                }
            }

            throw new InvalidParameterValueException("Invalid persistence policy provided. value: " + policy.getMethodName());
        }

        public static PersistenceMethod validateValue(String value) {

            List<String> values = new ArrayList<>();
            for (PersistenceMethod persistenceMethod : PersistenceMethod.values()) {
                if (persistenceMethod.description.equals(value)) {
                    return persistenceMethod;
                }
                values.add(persistenceMethod.description);
            }
            values.remove(PersistenceMethod.NONE.description);

            throw new InvalidParameterValueException("Invalid persistence policy provided. value: " + value + ", possible values: " + StringUtils.join(values, ", ")+ ".");
        }
    }

    protected ArrayList<VipPoolMap> createPools(GloboNetworkAPI gnApi, List<PoolV3.PoolMember> poolMembers, Long vipEnvironment, ApplyVipInGloboNetworkCommand cmd) throws GloboNetworkException {
        Map<String, VipPoolMap> vipPoolMaps = new LinkedHashMap<>();
        PoolAPI poolAPI = gnApi.getPoolAPI();

        for (Pair<Integer, Integer> portPair : cmd.getPortPairs()) {
            Integer vipPort = portPair.first();
            Integer realPort = portPair.second();

            PoolV3 pool = createPool(
                vipPort,
                realPort,
                vipEnvironment,
                cmd.getHost(),
                cmd.getMethodBal(),
                cmd.getHealthcheckType(),
                cmd.getHealthcheck(),
                cmd.getExpectedHealthcheck(),
                cmd.getHealthCheckDestination(),
                DEFAULT_MAX_CONN,
                cmd.getServiceDownAction(),
                poolMembers,
                cmd.getRegion()
            );

            pool = poolAPI.save(pool);
            vipPoolMaps.put(vipPort + ":" + realPort, new VipPoolMap(pool.getId(), vipPort));
        }
        return new ArrayList<>(vipPoolMaps.values());
    }

    protected PoolV3 createPool(Integer vipPort, Integer realPort, Long environment, String lbName, String balancingAlgorithm, String healthcheckType, String healthcheckPath, String expectedHealthcheck, String healthcheckDestination, Integer maxConn, String serviceDownActionStr, List<PoolV3.PoolMember> poolMembers, String region){
        PoolV3 poolV3 = new PoolV3();
        poolV3.setMaxconn(maxConn != null ? maxConn : DEFAULT_MAX_CONN);
        poolV3.setIdentifier(buildPoolName(region, lbName, vipPort, realPort));
        poolV3.setDefaultPort(realPort);
        poolV3.setEnvironment(environment);

        PoolV3.Healthcheck healthcheck = poolV3.getHealthcheck();
        healthcheck.setDestination(healthcheckDestination);
        if (forceSupportOldPoolVersion(healthcheckType, realPort)) {
            healthcheck.setHealthcheck("TCP", "", "");
        } else {
            healthcheck.setHealthcheck(healthcheckType, healthcheckPath, expectedHealthcheck);
        }

        LbAlgorithm lbAlgorithm = getBalancingAlgorithm(balancingAlgorithm);
        poolV3.setLbMethod(lbAlgorithm.getGloboNetworkBalMethod());

        PoolV3.ServiceDownAction serviceDownAction = new PoolV3.ServiceDownAction();
        serviceDownAction.setName(serviceDownActionStr);
        poolV3.setServiceDownAction(serviceDownAction);

        for (PoolV3.PoolMember poolMember : poolMembers) {
            poolMember.setPortReal(realPort);
            poolV3.getPoolMembers().add(poolMember);
        }
        return poolV3;
    }

    protected void updatePools(GloboNetworkAPI gnApi, List<Long> poolIds, List<PoolV3.PoolMember> poolMembers, List<Pair<Integer, Integer>> portPairs) throws GloboNetworkException {
        PoolAPI poolAPI = gnApi.getPoolAPI();
        Set<Long> updatePoolsIdList = new HashSet<>();

        for (Pair<Integer, Integer> portPair : portPairs) {
            Integer realPort = portPair.second();

            List<PoolV3> pools = poolAPI.getByIdsV3(poolIds);
            List<PoolV3> poolsFound = findPoolsByPort(realPort, pools);

            for (PoolV3 poolV3 : poolsFound) {
                if(!updatePoolsIdList.contains(poolV3.getId())) {
                    List<PoolV3.PoolMember> poolMembersFinal = new ArrayList<>();

                    for (PoolV3.PoolMember poolMember : poolMembers) {
                        PoolV3.PoolMember poolMemberAlreadyExists = findExistsRealInPool(poolMember, poolV3.getPoolMembers());

                        if (poolMemberAlreadyExists == null) {
                            poolMember.setPortReal(realPort);
                            poolMember.setLimit(poolV3.getMaxconn());
                            poolMembersFinal.add(poolMember);
                        } else {
                            poolMemberAlreadyExists.setEquipment(poolMember.getEquipment());
                            poolMemberAlreadyExists.setIp(poolMember.getIp());
                            poolMembersFinal.add(poolMemberAlreadyExists);
                        }
                    }
                    poolV3.setPoolMembers(poolMembersFinal);

                    poolV3 = poolAPI.save(poolV3);
                    updatePoolsIdList.add(poolV3.getId());
                }
            }
        }
    }

    protected boolean forceSupportOldPoolVersion(String healthcheckType, Integer realPort) {
        return (!healthcheckType.equals(HealthCheckHelper.HealthCheckType.HTTPS.name())
                &&(realPort == 443 ||  realPort == 8443));
    }

    private PoolV3.PoolMember findExistsRealInPool(PoolV3.PoolMember poolMember, List<PoolV3.PoolMember> poolMembersFromNetworkApi) {

        for (PoolV3.PoolMember poolMemberCreated : poolMembersFromNetworkApi) {
            if (poolMemberCreated.getIpFormated().equals(poolMember.getIpFormated())) {
                return poolMemberCreated;
            }
        }
        return null;
    }

    protected List<PoolV3.PoolMember> buildPoolMembers(GloboNetworkAPI gnAPI, List<Real> realList) throws GloboNetworkException {
        List<PoolV3.PoolMember> poolMembers = new ArrayList<PoolV3.PoolMember>();

        for (Real real : realList) {
            if (real.isRevoked()) {
                continue;
            }
            Ip equipmentIp = gnAPI.getIpAPI().findByIpAndEnvironment(real.getIp(), real.getEnvironmentId(), false);
            if (equipmentIp == null) {
                throw new InvalidParameterValueException("Could not get information by real IP: " + real.getIp());
            }
            Equipment equipment = gnAPI.getEquipmentAPI().listByName(real.getVmName());

            PoolV3.PoolMember poolMember = new PoolV3.PoolMember();

            poolMember.setPriority(DEFAULT_REALS_PRIORITY);
            poolMember.setWeight(DEFAULT_REAL_WEIGHT);
            poolMember.setMemberStatus(DEFAULT_REAL_STATUS);

            poolMember.setEquipmentId(equipment.getId());
            poolMember.setEquipmentName(equipment.getName());

            PoolV3.Ip ipReal = new PoolV3.Ip();
            ipReal.setIpFormated(real.getIp());
            ipReal.setId(equipmentIp.getId());


            boolean isIPv6 = IPAddressUtil.isIPv6LiteralAddress(real.getIp());
            if (isIPv6) {
                poolMember.setIpv6(ipReal);
            } else {
                poolMember.setIp(ipReal);
            }

            poolMembers.add(poolMember);
        }

        return poolMembers;
    }

    protected VipInfoHelper getVipInfos(GloboNetworkAPI gnAPI, Long vipEnvironmentId, String ipv4) throws GloboNetworkException {
        VipEnvironment environmentVip = gnAPI.getVipEnvironmentAPI().search(vipEnvironmentId, null, null, null);
        if (environmentVip == null) {
            throw new InvalidParameterValueException("Could not find VIP environment " + vipEnvironmentId);
        }

        Ip vipIp = gnAPI.getIpAPI().checkVipIp(ipv4, vipEnvironmentId, false);
        if (vipIp == null) {
            throw new InvalidParameterValueException("IP " + ipv4 + " doesn't exist in VIP environment " + vipEnvironmentId);
        }

        Network network = gnAPI.getNetworkAPI().getNetwork(vipIp.getNetworkId(), false);
        if (network == null) {
            throw new InvalidParameterValueException("Network " + vipIp.getNetworkId() + " was not found in GloboNetwork");
        }
        Vlan vlan = gnAPI.getVlanAPI().getById(network.getVlanId());
        if (vlan == null) {
            throw new InvalidParameterValueException("Vlan " + network.getVlanId() + " was not found in GloboNetwork");
        }

        return new VipInfoHelper(vlan.getEnvironment(), environmentVip, vipIp, network);
    }

    public static class VipInfoHelper {

        private final Network vipNetwork;
        private final Ip vipIp;
        private Long environment;
        private VipEnvironment vipEnvironment;

        public VipInfoHelper(Long environment, VipEnvironment vipEnv, Ip vipIp, Network vipNetwork) {
            this.environment = environment;
            this.vipEnvironment = vipEnv;
            this.vipIp = vipIp;
            this.vipNetwork = vipNetwork;

        }

        public Long getEnvironment() {
            return environment;
        }
    }

    protected VipAPIFacade createVipAPIFacade(Long vipId, GloboNetworkAPI api) throws GloboNetworkException {
        return new VipAPIFacade(vipId, api);
    }


    public GloboNetworkAPI getNewGloboNetworkAPI(){
        GloboNetworkAPI api = new GloboNetworkAPI(_url, _username, _password);
        api.setConnectTimeout(connectTimeout);
        api.setReadTimeout(readTimeout);
        api.setNumberOfRetries(numberOfRetries);


        String ndcContext = CallContext.current().getNdcContext();

        api.setContext(ndcContext);

        return api;
    }
}
