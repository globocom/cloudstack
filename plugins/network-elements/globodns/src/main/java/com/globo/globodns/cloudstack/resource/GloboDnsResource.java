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
package com.globo.globodns.cloudstack.resource;

import com.globo.globodns.client.exception.GloboDnsIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.naming.ConfigurationException;

import com.globo.globodns.client.model.Authentication;
import com.globo.globodns.client.model.Domain;
import com.globo.globodns.client.model.Export;
import com.globo.globodns.client.model.Record;
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
import com.cloud.utils.StringUtils;
import com.cloud.utils.component.ManagerBase;
import com.globo.globodns.client.GloboDns;
import com.globo.globodns.client.GloboDnsException;
import com.globo.globodns.cloudstack.commands.CreateLbRecordAndReverseCommand;
import com.globo.globodns.cloudstack.commands.CreateOrUpdateDomainCommand;
import com.globo.globodns.cloudstack.commands.CreateOrUpdateRecordAndReverseCommand;
import com.globo.globodns.cloudstack.commands.RemoveDomainCommand;
import com.globo.globodns.cloudstack.commands.RemoveRecordCommand;
import com.globo.globodns.cloudstack.commands.SignInCommand;
import com.globo.globodns.cloudstack.commands.ValidateLbRecordCommand;
import com.googlecode.ipv6.IPv6Address;

public class GloboDnsResource extends ManagerBase implements ServerResource {
    private String _zoneId;

    private String _guid;

    private String _name;

    private String _username;

    private String _url;

    private String _password;

    private Integer numberOfRetries = 3;
    private Integer connectTimeout = 20000;
    private Integer readTimeout = 20000;

    protected GloboDns _globoDns;

    private static final String IPV4_RECORD_TYPE = "A";
    private static final String IPV6_RECORD_TYPE = "AAAA";
    private static final String REVERSE_RECORD_TYPE = "PTR";
    private static final String REVERSE_IPV4_DOMAIN_SUFFIX = "in-addr.arpa";
    private static final String REVERSE_IPV6_DOMAIN_SUFFIX = "ip6.arpa";
    private static final String DEFAULT_AUTHORITY_TYPE = "M";

    private static final Logger s_logger = Logger.getLogger(GloboDnsResource.class);

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {

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

        _globoDns = GloboDns.buildHttpApi(_url, _username, _password);

        if (params.containsKey("readTimeout")) {
            readTimeout = Integer.valueOf((String)params.get("readTimeout"));
        }

        if (params.containsKey("connectTimeout")) {
            connectTimeout = Integer.valueOf((String)params.get("connectTimeout"));
        }

        if (params.containsKey("numberOfRetries")) {
            numberOfRetries = Integer.valueOf((String)params.get("numberOfRetries"));
        }

        _globoDns.setReadTimeout(readTimeout);
        _globoDns.setConnectTimeout(connectTimeout);
        _globoDns.setNumberOfRetries(numberOfRetries);

        return true;
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
        s_logger.trace("initialize called");
        StartupCommand cmd = new StartupCommand(getType());
        cmd.setName(_name);
        cmd.setGuid(_guid);
        cmd.setDataCenter(_zoneId);
        cmd.setPod("");
        cmd.setPrivateIpAddress("");
        cmd.setStorageIpAddress("");
        cmd.setVersion(GloboDnsResource.class.getPackage().getImplementationVersion());
        return new StartupCommand[] {cmd};
    }

    @Override
    public PingCommand getCurrentStatus(long id) {
        return new PingCommand(getType(), id);
    }

    @Override
    public void disconnected() {
        return;
    }

    @Override
    public IAgentControl getAgentControl() {
        return null;
    }

    @Override
    public void setAgentControl(IAgentControl agentControl) {
        return;
    }

    @Override
    public Answer executeRequest(Command cmd) {
        if (cmd instanceof ReadyCommand) {
            return new ReadyAnswer((ReadyCommand)cmd);
        } else if (cmd instanceof MaintainCommand) {
            return new MaintainAnswer((MaintainCommand)cmd);
        } else if (cmd instanceof SignInCommand) {
            return execute((SignInCommand)cmd);
        } else if (cmd instanceof RemoveDomainCommand) {
            return execute((RemoveDomainCommand)cmd);
        } else if (cmd instanceof RemoveRecordCommand) {
            return execute((RemoveRecordCommand)cmd);
        } else if (cmd instanceof CreateOrUpdateDomainCommand) {
            return execute((CreateOrUpdateDomainCommand)cmd);
        } else if (cmd instanceof CreateOrUpdateRecordAndReverseCommand) {
            return execute((CreateOrUpdateRecordAndReverseCommand)cmd);
        } else if (cmd instanceof ValidateLbRecordCommand) {
            return execute((ValidateLbRecordCommand) cmd);
        } else if (cmd instanceof CreateLbRecordAndReverseCommand) {
            return execute((CreateLbRecordAndReverseCommand) cmd);
        }
        return Answer.createUnsupportedCommandAnswer(cmd);
    }

    public Answer execute(SignInCommand cmd) {
        try {
            Authentication auth = _globoDns.getAuthAPI().signIn(cmd.getEmail(), cmd.getPassword());
            if (auth != null) {
                return new Answer(cmd, true, "Signed in successfully");
            } else {
                return new Answer(cmd, false, "Unable to sign in on GloboDNS");
            }
        } catch (GloboDnsException e) {
            return new Answer(cmd, false, e.getMessage());
        }
    }

    public Answer execute(RemoveDomainCommand cmd) {
        try {
            Domain domain = searchDomain(cmd.getNetworkDomain(), false);
            if (domain != null) {
                if (!cmd.isOverride()) {
                    for (Record record : _globoDns.getRecordAPI().listAll(domain.getId())) {
                        if (record.getTypeNSRecordAttributes().getId() == null) {
                            s_logger.warn("There are records in domain " + cmd.getNetworkDomain() + " and override is not enable. I will not delete this domain.");
                            return new Answer(cmd, true, "Domain keeped");
                        }
                    }
                }
                _globoDns.getDomainAPI().removeDomain(domain.getId());
                scheduleExportChangesToBind();
            } else {
                s_logger.warn("Domain " + cmd.getNetworkDomain() + " already been deleted.");
            }

            return new Answer(cmd, true, "Domain removed");
        } catch (GloboDnsException e) {
            return new Answer(cmd, false, e.getMessage());
        }
    }

    public Answer execute(RemoveRecordCommand cmd) {
        boolean needsExport = false;
        try {
            if (removeRecord(cmd.getRecordName(), cmd.getRecordIp(), cmd.getNetworkDomain(), false)) {
                needsExport = true;
            }

            // remove reverse
            String reverseGloboDnsName = generateReverseDomainNameFromNetworkIp(cmd.getRecordIp(), cmd.isIpv6());
            String reverseRecordName = generateReverseRecordNameFromNetworkIp(cmd.getRecordIp(), cmd.isIpv6());
            String reverseRecordContent = cmd.getRecordName() + '.' + cmd.getNetworkDomain() + '.';

            if (removeRecord(reverseRecordName, reverseRecordContent, reverseGloboDnsName, true)) {
                needsExport = true;
            }

            return new Answer(cmd, true, "Record removed");
        } catch (GloboDnsException e) {
            return new Answer(cmd, false, e.getMessage());
        } finally {
            if (needsExport) {
                scheduleExportChangesToBind();
            }
        }
    }

    public Answer execute(CreateOrUpdateRecordAndReverseCommand cmd) {
        boolean needsExport = false;
        try {
            Domain domain = searchDomain(cmd.getNetworkDomain(), false);
            if (domain == null) {
                domain = _globoDns.getDomainAPI().createDomain(cmd.getNetworkDomain(), cmd.getReverseTemplateId(), DEFAULT_AUTHORITY_TYPE);
                s_logger.warn("Domain " + cmd.getNetworkDomain() + " doesn't exist, maybe someone removed it. It was automatically created with template "
                        + cmd.getReverseTemplateId());
            }

            String recordType = cmd.isIpv6() ? IPV6_RECORD_TYPE : IPV4_RECORD_TYPE;
            boolean created = createOrUpdateRecord(domain.getId(), cmd.getRecordName(), cmd.getRecordIp(), recordType, cmd.isOverride());
            if (!created) {
                String msg = "Unable to create record " + cmd.getRecordName() + " at " + cmd.getNetworkDomain();
                if (!cmd.isOverride()) {
                    msg += ". Override record option is false, maybe record already exist.";
                }
                return new Answer(cmd, false, msg);
            } else {
                needsExport = true;
            }

            String reverseRecordContent = cmd.getRecordName() + '.' + cmd.getNetworkDomain() + '.';
            if (createOrUpdateReverse(cmd.getRecordIp(), reverseRecordContent, cmd.getReverseTemplateId(), cmd.isOverride(), cmd.isIpv6())) {
                needsExport = true;
            } else {
                if (!cmd.isOverride()) {
                    String msg = "Unable to create reverse record " + cmd.getRecordName() + " for ip " + cmd.getRecordIp();
                    msg += ". Override record option is false, maybe record already exist.";
                    return new Answer(cmd, false, msg);
                }
            }

            return new Answer(cmd);
        } catch (GloboDnsIOException ex) {
            return new Answer(cmd, false, "DNS IO ERROR: "+ ex.getMessage(), Answer.AnswerTypeError.DNS_IO_ERROR);
        } catch (GloboDnsException e) {
            return new Answer(cmd, false, e.getMessage());
        } finally {
            if (needsExport) {
                scheduleExportChangesToBind();
            }
        }
    }

    public Answer execute(ValidateLbRecordCommand cmd) {
        String lbName = cmd.getLbRecordName() +  "." + cmd.getLbDomain();
        s_logger.debug("[LoadBalancer " + lbName +"] init loadbalancer validating");
        try {
            Domain domain = searchDomain(cmd.getLbDomain(), false);
            if (domain == null) {
                String msg = "Domain " + cmd.getLbDomain() + " doesn't exist.";
                s_logger.error("[LoadBalancer " + lbName +"]" + msg);
                return new Answer(cmd, false, msg);
            }

            Record record = this.searchRecordByName(cmd.getLbRecordName(), domain.getId());
            if (record == null) {
                // Record does not exist, return that it is valid
                return new Answer(cmd);
            }

            if (cmd.isOverride()) {
                // If record exists and override is true, then also returns true
                return new Answer(cmd);
            }

            if (record.getContent().equals(cmd.getLbRecordContent())) {
                // If record exists and override is false, then content must be equal, i.e. no changes will be made, only validating it
                return new Answer(cmd);
            } else {
                s_logger.warn("[LoadBalancer " + lbName +"] Record " + cmd.getLbRecordName() + " is invalid or override option is false");
                String msg = "The given load balancer name record is not valid or it is already in use. Override is not possible.";
                return new Answer(cmd, false, msg);
            }

       } catch (GloboDnsIOException ex ){
            if (cmd.isSkipDnsError()) {
                s_logger.warn("[LoadBalancer " + lbName +"] IOException: ignoring loadbalancer " + lbName + " validation because skipdnserror is " + cmd.isSkipDnsError() + ", maybe globoDNSAPI is off or with some problem. Error: " + ex.getMessage(), ex);
                return new Answer(cmd, true, ex.getMessage());
            } else {
                s_logger.error("[LoadBalancer " + lbName +"] IOException: force loadbalancer " + lbName + "validation because skipdnserror is "+ cmd.isSkipDnsError() + " failed: " +  ex.getMessage(), ex);
                return new Answer(cmd, false, "Integration problem with DNSAPI, please contact your system administrator.", Answer.AnswerTypeError.DNS_IO_ERROR);
            }

       } catch (GloboDnsException e) {
            s_logger.error("[LoadBalancer " + lbName +"] GloboDnsException error:" + e.getMessage(), e);
           return new Answer(cmd, false, e.getLocalizedMessage());
       }
   }

   public Answer execute(CreateLbRecordAndReverseCommand cmd) {
        boolean needsExport = false;
       String lbname = cmd.getLbRecordName() + cmd.getLbDomain();
        try {
            Domain domain = searchDomain(cmd.getLbDomain(), false);
            if (domain == null) {
                String msg = "Domain " + cmd.getLbDomain() + " doesn't exist.";
                s_logger.debug("[LoadBalancer " + lbname + "] " + msg);
                return new Answer(cmd, false, msg);
            }

            boolean created = createOrUpdateRecord(domain.getId(), cmd.getLbRecordName(), cmd.getLbRecordIp(), IPV4_RECORD_TYPE, cmd.isOverride());
            if (!created) {
                String msg = "Unable to create LB record " + cmd.getLbRecordName() + " at " + cmd.getLbDomain();
                if (!cmd.isOverride()) {
                    msg += ". Override LB record option is false, maybe record already exists.";
                }
                return new Answer(cmd, false, msg);
            } else {
                needsExport = true;
            }

            String reverseRecordContent = cmd.getLbRecordName() + '.' + cmd.getLbDomain() + '.';
            if (createOrUpdateReverse(cmd.getLbRecordIp(), reverseRecordContent, cmd.getReverseTemplateId(), cmd.isOverride(), false)) { //IPv6 LB not implemented yet
                needsExport = true;
            } else if (!cmd.isOverride()) {
                String msg = "Unable to create LB reverse record " + cmd.getLbRecordName() + " for ip " + cmd.getLbRecordIp();
                msg += ". Override record option is false, maybe record already exists.";
                return new Answer(cmd, false, msg);
            }


            return new Answer(cmd);
        }catch (GloboDnsIOException ex) {
            s_logger.error("[LoadBalancer " + lbname + "] DNS IO error " + ex.getMessage(), ex);
            return new Answer(cmd, false, ex.getMessage(), Answer.AnswerTypeError.DNS_IO_ERROR);
        } catch (GloboDnsException e) {
            s_logger.error("[LoadBalancer " + lbname + "] unable to create lb: " + e);
            return new Answer(cmd, false, e.getMessage());
        } finally {
           if (needsExport) {
               scheduleExportChangesToBind();
           }
       }
   }

    protected boolean createOrUpdateReverse(String networkIp, String reverseRecordContent, Long templateId, boolean override, boolean isIpv6) {
        String reverseDomainName = generateReverseDomainNameFromNetworkIp(networkIp, isIpv6);
        Domain reverseDomain = searchDomain(reverseDomainName, true);
        if (reverseDomain == null) {
            reverseDomain = _globoDns.getDomainAPI().createReverseDomain(reverseDomainName, templateId, DEFAULT_AUTHORITY_TYPE);
            s_logger.info("Created reverse domain " + reverseDomainName + " with template " + templateId);
        }

        // create reverse
        String reverseRecordName = generateReverseRecordNameFromNetworkIp(networkIp, isIpv6);
        return createOrUpdateRecord(reverseDomain.getId(), reverseRecordName, reverseRecordContent, REVERSE_RECORD_TYPE, override);
    }

    public Answer execute(CreateOrUpdateDomainCommand cmd) {

        boolean needsExport = false;
        try {
            Domain domain = searchDomain(cmd.getDomainName(), false);
            if (domain == null) {
                // create
                domain = _globoDns.getDomainAPI().createDomain(cmd.getDomainName(), cmd.getTemplateId(), DEFAULT_AUTHORITY_TYPE);
                s_logger.info("Created domain " + cmd.getDomainName() + " with template " + cmd.getTemplateId());
                if (domain == null) {
                    return new Answer(cmd, false, "Unable to create domain " + cmd.getDomainName());
                } else {
                    needsExport = true;
                }
            } else {
                s_logger.warn("Domain " + cmd.getDomainName() + " already exist.");
            }
            return new Answer(cmd);
        } catch (GloboDnsException e) {
            return new Answer(cmd, false, e.getMessage());
        } finally {
            if (needsExport) {
                scheduleExportChangesToBind();
            }
        }
    }

    /**
     * Try to remove a record from bindZoneName. If record was removed returns true.
     * @param recordName
     * @param bindZoneName
     * @return true if record exists and was removed.
     */
    protected boolean removeRecord(String recordName, String recordValue, String bindZoneName, boolean reverse) {

        Domain domain = searchDomain(bindZoneName, reverse);
        if (domain == null) {
            s_logger.warn("Domain " + bindZoneName + " doesn't exists in GloboDNS. Record " + recordName + " has already been removed.");
            return false;
        }
        Record record = searchRecordByNameAndContent(recordName, recordValue, domain.getId());
        if (record == null) {
            s_logger.warn("Record " + recordName + " in domain " + bindZoneName + " has already been removed.");
            return false;
        } else {
            if (!record.getContent().equals(recordValue)) {
                s_logger.warn("Record " + recordName + " in domain " + bindZoneName + " has different value from " + recordValue
                        + ". Will not delete it.");
                return false;
            }

            _globoDns.getRecordAPI().removeRecord(record.getId());
        }

        return true;
    }

    /**
     * Create a new record in Zone, or update it if record has been exists.
     * @param domainId
     * @param name
     * @param ip
     * @param type
     * @return if record was created or updated.
     */
    private boolean createOrUpdateRecord(Long domainId, String name, String ip, String type, boolean override) {
        Record record = this.searchRecordByName(name, domainId);
        if (record == null) {
            // Create new record
            record = _globoDns.getRecordAPI().createRecord(domainId, name, ip, type);
            s_logger.info("Created record " + name + " in domain " + domainId);
        } else {
            if (!ip.equals(record.getContent())) {
                if (Boolean.TRUE.equals(override)) {
                    // ip is incorrect. Fix.
                    _globoDns.getRecordAPI().updateRecord(record.getId(), domainId, name, ip);
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * GloboDns export all changes to Bind server.
     */
    public void scheduleExportChangesToBind() {
        try {
            Export export = _globoDns.getExportAPI().scheduleExport();
            if (export != null) {
                s_logger.info("GloboDns Export: " + export.getResult());
            }
        } catch (GloboDnsException e) {
            s_logger.warn("Error on scheduling export. Although everything was persist, someone need to manually force export in GloboDns", e);
        }
    }

    /**
     * Try to find bindZoneName in GloboDns.
     * @param name
     * @return Domain object or null if domain not exists.
     */
    private Domain searchDomain(String name, boolean reverse) {
        if (name == null) {
            return null;
        }
        List<Domain> candidates;
        if (reverse) {
            candidates = _globoDns.getDomainAPI().listReverseByQuery(name);
        } else {
            candidates = _globoDns.getDomainAPI().listByQuery(name);
        }
        for (Domain candidate : candidates) {
            if (name.equals(candidate.getName())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Find recordName in domain.
     * @param recordName
     * @param domainId Id of BindZoneName. Maybe you need use searchDomain before to use BindZoneName.
     * @return Record or null if not exists.
     */
    private Record searchRecordByName(String recordName, Long domainId) {
        List<Record> candidates = this.listRecords(recordName, domainId);
        // GloboDns search name in name and content. We need to iterate to check if recordName exists only in name
        for (Record candidate : candidates) {
            if (recordName.equalsIgnoreCase(candidate.getName())) {
                s_logger.debug("Record " + recordName + " in domain id " + domainId + " found in GloboDNS");
                return candidate;
            }
        }
        s_logger.debug("Record " + recordName + " in domain id " + domainId + " not found in GloboDNS");
        return null;
    }

    private Record searchRecordByNameAndContent(String recordName, String recordContent, Long domainId) {
        List<Record> candidates = this.listRecords(recordName, domainId);
        // GloboDns search name in name and content. We need to iterate to check if recordName exists only in name
        for (Record candidate : candidates) {
            if (recordName.equalsIgnoreCase(candidate.getName()) && recordContent.equalsIgnoreCase(candidate.getContent())) {
                s_logger.debug("Record " + recordName + ":" + recordContent + " in domain id " + domainId + " found in GloboDNS");
                return candidate;
            }
        }
        s_logger.debug("Record " + recordName + ":" + recordContent + " in domain id " + domainId + " not found in GloboDNS");
        return null;
    }

    private List<Record> listRecords(String recordName, Long domainId) {
        if (recordName == null || domainId == null) {
            return null;
        }
        List<Record> candidates = _globoDns.getRecordAPI().listByQuery(domainId, recordName);
        return candidates;
    }

    /**
     * Generate reverseBindZoneName of network. We ALWAYS use /24.
     * @param networkIp
     * @return Bind Zone Name reverse of network specified by networkIp
     */
    private String generateReverseDomainNameFromNetworkIp(String networkIp, boolean isIpv6) {
        if(isIpv6){
            String ipv6 = IPv6Address.fromString(networkIp).toLongString(); // returns the long representation of ipv6 address
            ArrayList<String> blocks = new ArrayList<String>(Arrays.asList(Arrays.copyOfRange(ipv6.split(":"), 0, 4)));
            String joinedIpv6Blocks = StringUtils.join(blocks, "");

            StringBuilder reverseDomainName = new StringBuilder();
            for(char character : joinedIpv6Blocks.toCharArray()){
                reverseDomainName.append(character + ".");
            }
            return reverseDomainName.reverse().substring(1).toString() + '.' + REVERSE_IPV6_DOMAIN_SUFFIX;
        }else{
            String[] octets = networkIp.split("\\."); //Considering only /24 networks
            String reverseDomainName = octets[2] + '.' + octets[1] + '.' + octets[0] + '.' + REVERSE_IPV4_DOMAIN_SUFFIX;
            return reverseDomainName;
        }
    }

    private String generateReverseRecordNameFromNetworkIp(String networkIp, boolean isIpv6) {
        if(isIpv6){
            String ipv6 = IPv6Address.fromString(networkIp).toLongString(); // returns the long representation of ipv6 address
            ArrayList<String> blocks = new ArrayList<String>(Arrays.asList(Arrays.copyOfRange(ipv6.split(":"), 4, 8)));
            String joinedIpv6Blocks = StringUtils.join(blocks, "");

            StringBuilder reverseDomainName = new StringBuilder();
            for(char character : joinedIpv6Blocks.toCharArray()){
                reverseDomainName.append(character + ".");
            }
            return reverseDomainName.reverse().substring(1).toString();
        }else{
            String[] octets = networkIp.split("\\.");
            String reverseRecordName = octets[3];
            return reverseRecordName;
        }
    }

}
