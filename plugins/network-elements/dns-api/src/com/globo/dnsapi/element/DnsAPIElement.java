// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.globo.dnsapi.element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.Local;
import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.StartupCommand;
import com.cloud.configuration.Config;
import com.cloud.dc.DataCenter;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.Network;
import com.cloud.network.Network.Capability;
import com.cloud.network.Network.Provider;
import com.cloud.network.Network.Service;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.element.NetworkElement;
import com.cloud.offering.NetworkOffering;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ResourceStateAdapter;
import com.cloud.resource.ServerResource;
import com.cloud.resource.UnableDeleteHostException;
import com.cloud.utils.component.AdapterBase;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionCallbackNoReturn;
import com.cloud.utils.db.TransactionCallbackWithException;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.globo.dnsapi.DnsAPINetworkVO;
import com.globo.dnsapi.DnsAPIVirtualMachineVO;
import com.globo.dnsapi.api.AddDnsApiHostCmd;
import com.globo.dnsapi.commands.CreateDomainCommand;
import com.globo.dnsapi.commands.CreateRecordCommand;
import com.globo.dnsapi.commands.CreateReverseDomainCommand;
import com.globo.dnsapi.commands.GetDomainInfoCommand;
import com.globo.dnsapi.commands.GetRecordInfoCommand;
import com.globo.dnsapi.commands.GetReverseDomainInfoCommand;
import com.globo.dnsapi.commands.ListDomainCommand;
import com.globo.dnsapi.commands.ListRecordCommand;
import com.globo.dnsapi.commands.ListReverseDomainCommand;
import com.globo.dnsapi.commands.RemoveDomainCommand;
import com.globo.dnsapi.commands.RemoveRecordCommand;
import com.globo.dnsapi.commands.RemoveReverseDomainCommand;
import com.globo.dnsapi.commands.ScheduleExportCommand;
import com.globo.dnsapi.commands.SignInCommand;
import com.globo.dnsapi.commands.UpdateRecordCommand;
import com.globo.dnsapi.dao.DnsAPINetworkDao;
import com.globo.dnsapi.dao.DnsAPIVirtualMachineDao;
import com.globo.dnsapi.model.Domain;
import com.globo.dnsapi.model.Export;
import com.globo.dnsapi.model.Record;
import com.globo.dnsapi.resource.DnsAPIResource;
import com.globo.dnsapi.response.DnsAPIDomainListResponse;
import com.globo.dnsapi.response.DnsAPIDomainResponse;
import com.globo.dnsapi.response.DnsAPIExportResponse;
import com.globo.dnsapi.response.DnsAPIRecordListResponse;
import com.globo.dnsapi.response.DnsAPIRecordResponse;

@Component
@Local(NetworkElement.class)
public class DnsAPIElement extends AdapterBase implements ResourceStateAdapter, NetworkElement, DnsAPIElementService {

	private static final Logger s_logger = Logger.getLogger(DnsAPIElement.class);
	
	private static final Map<Service, Map<Capability, String>> capabilities = setCapabilities();
	
	private static final String AUTHORITY_TYPE = "M";
	
	private static final String RECORD_TYPE = "A";
	private static final String REVERSE_RECORD_TYPE = "PTR";
	private static final String REVERSE_DOMAIN_SUFFIX = "in-addr.arpa";
	
	// DAOs
	@Inject
	DataCenterDao _dcDao;
	@Inject
	DnsAPINetworkDao _dnsapiNetworkDao;
	@Inject
	DnsAPIVirtualMachineDao _dnsapiVmDao;
	@Inject
	HostDao _hostDao;
	@Inject
	NetworkDao _networkDao;
	@Inject
	PhysicalNetworkDao _physicalNetworkDao;
	@Inject
	ConfigurationDao _configDao;
	
	// Managers
	@Inject
	AgentManager _agentMgr;
	@Inject
	ResourceManager _resourceMgr;
	
    public DnsAPIElement() {

    }

    @Override
    public Map<Service, Map<Capability, String>> getCapabilities() {
        return capabilities;
    }
    
    private static Map<Service, Map<Capability, String>> setCapabilities() {
    	Map<Service, Map<Capability, String>> caps = new HashMap<Service, Map<Capability, String>>();
    	Map<Capability, String> dnsCapabilities = new HashMap<Capability, String>();
    	dnsCapabilities.put(Capability.AllowDnsSuffixModification, "true");
        caps.put(Service.Dns, dnsCapabilities);
        return caps;
    }

    @Override
    public Provider getProvider() {
        return Provider.DnsAPI;
    }
    
    protected boolean isTypeSupported(VirtualMachine.Type type) {
    	return type == VirtualMachine.Type.User || type == VirtualMachine.Type.ConsoleProxy || type == VirtualMachine.Type.DomainRouter;
    }
    
    protected Domain setupDomainAndReverseDomain(Network network) {
		Long zoneId = network.getDataCenterId();
    	DataCenter zone = _dcDao.findById(zoneId);
		if (zone == null) {
			throw new CloudRuntimeException(
					"Could not find zone associated to this network");
		}
		
    	s_logger.debug("Creating domain " + network.getNetworkDomain() + " for network " + network);
    	Domain domain = this.getOrCreateDomain(zone.getId(), network.getNetworkDomain(), false);

    	/* Create new reverse domain in DNS API */
    	String[] octets = network.getCidr().split("\\/")[0].split("\\.");
    	String reverseDomainName = octets[2] + "." + octets[1] + "." + octets[0] + "." + REVERSE_DOMAIN_SUFFIX;
    	s_logger.debug("Creating reverse domain " + reverseDomainName);
    	Domain reverseDomain = this.getOrCreateDomain(zone.getId(), reverseDomainName, true);
    	
    	/* Save in the database */
    	this.saveDomainDB(network, domain, reverseDomain);
    	
    	return domain;
    }

    @Override
    @DB
    public boolean implement(final Network network, NetworkOffering offering, DeployDestination dest, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException,
    InsufficientCapacityException {
    	Transaction.execute(new TransactionCallbackNoReturn() {

			@Override
			public void doInTransactionWithoutResult(TransactionStatus status) {
				// Configure network domain
				setupDomainAndReverseDomain(network);

		    	/* Export changes to Bind in DNS API */
		    	scheduleBindExport(network.getDataCenterId());
			}
    	});
        return true;
    }

    @Override
    @DB
    public boolean prepare(final Network network, final NicProfile nic, final VirtualMachineProfile vm, DeployDestination dest, ReservationContext context) throws ConcurrentOperationException,
    ResourceUnavailableException, InsufficientCapacityException {
    	
    	if (!isTypeSupported(vm.getType())) {
    		s_logger.info("DNSAPI only manage records for VMs of type User, ConsoleProxy and DomainRouter. VM " + vm + " is " + vm.getType());
    		return false;
    	}
    	
    	Long zoneId = network.getDataCenterId();
    	final DataCenter zone = _dcDao.findById(zoneId);
		if (zone == null) {
			throw new CloudRuntimeException(
					"Could not find zone associated to this network");
		}
		
		Transaction.execute(new TransactionCallbackNoReturn() {
			
			@Override
			public void doInTransactionWithoutResult(TransactionStatus status) {
				// VirtualRouter is created before implement method was called.
//				setupDomainAndReverseDomain(network);
				
				DnsAPINetworkVO dnsapiNetworkVO = getDnsAPINetworkVO(network);
				if (dnsapiNetworkVO == null) {
					throw new CloudRuntimeException("Could not obtain DNS mapping for this network");
				}

				long domainId = dnsapiNetworkVO.getDnsapiDomainId();
				long reverseDomainId = dnsapiNetworkVO.getDnsapiReverseDomainId();
				
				/* Create new A record in DNS API */
				// We allow only lower case names in DNS, so force lower case names for VMs
				String vmName = vm.getHostName();
				String vmNameLowerCase = vmName.toLowerCase();
				if (!vmName.equals(vmNameLowerCase) && vm.getType() == VirtualMachine.Type.User) {
					throw new InvalidParameterValueException("VM name should contain only lower case letters and digits: " + vmName + " - " + vm);
				}
				
				String recordName = vmNameLowerCase;
		    	Record createdRecord = createOrUpdateRecord(zone.getId(), domainId, recordName, nic.getIp4Address(), false);
		    	
				/* Create new PTR record in DNS API */
				// Need domain name for full reverse record content
		    	GetDomainInfoCommand cmdInfo = new GetDomainInfoCommand(domainId);
		    	Answer answerInfo = callCommand(cmdInfo, zone.getId());
		    	Domain domain = ((DnsAPIDomainResponse) answerInfo).getDomain();
		    	if (domain == null) {
		    		// Domain doesn't exist in DNS API
		    		throw new CloudRuntimeException("Could not get Domain info from DNS API");
		    	}
		    	
		    	String[] octets = nic.getIp4Address().split("\\.");
				String reverseRecordName = octets[3];
				String reverseRecordContent = recordName + "." + domain.getName();
		    	
				Record createdReverseRecord = createOrUpdateRecord(zone.getId(), reverseDomainId, reverseRecordName, reverseRecordContent, true);
				
		    	/* Save in the database */
		    	// Save domain record
		    	saveRecordDB(vm, domainId, createdRecord);
		    	// Save reverse domain record
		    	saveRecordDB(vm, reverseDomainId, createdReverseRecord);

		    	/* Export changes to Bind in DNS API */
		    	scheduleBindExport(zone.getId());
			}
		});
    	return true;
    }

    @Override
    @DB
    public boolean release(final Network network, NicProfile nic, final VirtualMachineProfile vm, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException {
    	s_logger.debug("Entering release method for DnsAPI");
    	
    	if (!isTypeSupported(vm.getType())) {
    		s_logger.info("DNSAPI only manage records for VMs of type User, ConsoleProxy and DomainRouter. VM " + vm + " is " + vm.getType());
    		return false;
    	}
    	
    	Long zoneId = network.getDataCenterId();
    	final DataCenter zone = _dcDao.findById(zoneId);
		if (zone == null) {
			throw new CloudRuntimeException(
					"Could not find zone associated to this network");
		}
		
		boolean result = Transaction.execute(new TransactionCallback<Boolean>() {

			@Override
			public Boolean doInTransaction(TransactionStatus status) {
				DnsAPINetworkVO dnsapiNetworkVO = getDnsAPINetworkVO(network);
				if (dnsapiNetworkVO == null) {
					// Don't have mapping for domain anymore, should let Cloudstack clean everything up
					return true;
				}

				long domainId = dnsapiNetworkVO.getDnsapiDomainId();
				long reverseDomainId = dnsapiNetworkVO.getDnsapiReverseDomainId();

				DnsAPIVirtualMachineVO dnsapiVirtualMachineVODomain = getDnsAPIVirtualMachineVO(vm.getId(), domainId);
				DnsAPIVirtualMachineVO dnsapiVirtualMachineVOReverseDomain = getDnsAPIVirtualMachineVO(vm.getId(), reverseDomainId);
				if (dnsapiVirtualMachineVODomain == null || dnsapiVirtualMachineVOReverseDomain == null) {
					// Don't have mapping for VMs anymore, should let Cloudstack clean everything up
					return true;
				}

				long recordId = dnsapiVirtualMachineVODomain.getDnsapiRecordId();
				long reverseRecordId = dnsapiVirtualMachineVOReverseDomain.getDnsapiRecordId();
				
				/* Remove record from DNS API */
				removeRecord(zone.getId(), recordId);
		    	
		    	/* Remove reverse record from DNS API */
				removeRecord(zone.getId(), reverseRecordId);
				
		    	/* Export changes to Bind in DNS API */
		    	scheduleBindExport(zone.getId());
		    	
		    	/* Remove entries from mapping table */
		    	_dnsapiVmDao.remove(dnsapiVirtualMachineVODomain.getId());
		    	_dnsapiVmDao.remove(dnsapiVirtualMachineVOReverseDomain.getId());
		    	
		    	return true;
			}
		});
    	
        return result;
    }

    @Override
    public boolean shutdown(Network network, ReservationContext context, boolean cleanup) throws ConcurrentOperationException, ResourceUnavailableException {
    	s_logger.debug("Entering shutdown method for DnsAPI");
        return true;
    }

    @Override
    @DB
    public boolean destroy(final Network network, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException {
    	s_logger.debug("Entering destroy method for DnsAPI");
    	Long zoneId = network.getDataCenterId();
    	final DataCenter zone = _dcDao.findById(zoneId);
		if (zone == null) {
			throw new CloudRuntimeException(
					"Could not find zone associated to this network");
		}
		
		boolean result = Transaction.execute(new TransactionCallback<Boolean>() {
			
			@Override
			public Boolean doInTransaction(TransactionStatus status) {
				DnsAPINetworkVO dnsapiNetworkVO = getDnsAPINetworkVO(network);
				if (dnsapiNetworkVO == null) {
					// Don't have mapping for domain anymore, should let Cloudstack clean everything up
					return true;
				}

				long domainId = dnsapiNetworkVO.getDnsapiDomainId();
				long reverseDomainId = dnsapiNetworkVO.getDnsapiReverseDomainId();
				    	
				/* Remove domain from DNS API */
				removeDomain(zone.getId(), domainId, false);
		    	
		    	/* Remove reverse domain from DNS API */
				removeDomain(zone.getId(), reverseDomainId, true);
				
		    	/* Export changes to Bind in DNS API */
		    	scheduleBindExport(zone.getId());
		    	
		    	/* Remove entry from mapping table */
		    	_dnsapiNetworkDao.remove(dnsapiNetworkVO.getId());
		    	
		    	return true;
			}
		});
    	
        return result;
    }

    @Override
    public boolean isReady(PhysicalNetworkServiceProvider provider) {
        return true;
    }

    @Override
    public boolean shutdownProviderInstances(PhysicalNetworkServiceProvider provider, ReservationContext context) throws ConcurrentOperationException, ResourceUnavailableException {
    	PhysicalNetwork pNtwk = _physicalNetworkDao.findById(provider.getPhysicalNetworkId());
    	Host host = getDnsAPIHost(pNtwk.getDataCenterId());
    	if (host != null) {
    		_resourceMgr.deleteHost(host.getId(), true, false);
    	}
    	return true;
    }

    @Override
    public boolean canEnableIndividualServices() {
        return true;
    }

    @Override
    public boolean verifyServicesCombination(Set<Service> services) {
        return true;
    }

	@Override
	public List<Class<?>> getCommands() {
		List<Class<?>> cmdList = new ArrayList<Class<?>>();
		cmdList.add(AddDnsApiHostCmd.class);
		return cmdList;
	}
	
	@Override
	public HostVO createHostVOForConnectedAgent(HostVO host,
			StartupCommand[] cmd) {
		return null;
	}

	@Override
	public HostVO createHostVOForDirectConnectAgent(HostVO host,
			StartupCommand[] startup, ServerResource resource,
			Map<String, String> details, List<String> hostTags) {
        if (!(startup[0] instanceof StartupCommand && resource instanceof DnsAPIResource)) {
            return null;
        }
        host.setType(Host.Type.L2Networking);
        return host;
	}

	@Override
	public DeleteHostAnswer deleteHost(HostVO host, boolean isForced,
			boolean isForceDeleteStorage) throws UnableDeleteHostException {
        if (!(host.getType() == Host.Type.L2Networking)) {
            return null;
        }
        return new DeleteHostAnswer(true);
	}
	
    @Override
    public boolean configure(String name, Map<String, Object> params)
            throws ConfigurationException {
        super.configure(name, params);
        _resourceMgr.registerResourceStateAdapter(name, this);
        return true;
    }
    
	protected Answer callCommand(Command cmd, Long zoneId) {
		
		HostVO dnsApiHost = getDnsAPIHost(zoneId);
		if (dnsApiHost == null) {
			throw new CloudRuntimeException("Could not find the DNS API resource");
		}
		
		Answer answer = _agentMgr.easySend(dnsApiHost.getId(), cmd);
		if (answer == null || !answer.getResult()) {
			String msg = "Error executing command " + cmd;
			msg = answer == null ? msg : answer.getDetails();
			throw new CloudRuntimeException(msg);
		}
		
		return answer;
	}
	
	private HostVO getDnsAPIHost(Long zoneId) {
		return _hostDao.findByTypeNameAndZoneId(zoneId, Provider.DnsAPI.getName(), Type.L2Networking);
	}
	
	@Override
	@DB
	public Host addDNSAPIHost(Long physicalNetworkId, final String username, final String password, String url) {
		
		if (username == null || username.trim().isEmpty()) {
			throw new InvalidParameterValueException("Invalid username: " + username);
		}
		
		if (password == null || password.trim().isEmpty()) {
			throw new InvalidParameterValueException("Invalid password: " + password);
		}
		
		if (url == null || url.trim().isEmpty()) {
			throw new InvalidParameterValueException("Invalid url: " + url);
		}
		
		// validate physical network and zone
		// Check if physical network exists
		PhysicalNetwork pNtwk = null;
		if (physicalNetworkId != null) {
			pNtwk = _physicalNetworkDao.findById(physicalNetworkId);
			if (pNtwk == null) {
				throw new InvalidParameterValueException(
						"Unable to find a physical network having the specified physical network id");
			}
		} else {
			throw new InvalidParameterValueException("Invalid physicalNetworkId: " + physicalNetworkId);
		}

		final Long zoneId = pNtwk.getDataCenterId();

		final Map<String, String> params = new HashMap<String, String>();
		params.put("guid", "dnsapi-" + String.valueOf(zoneId));
		params.put("zoneId", String.valueOf(zoneId));
		params.put("name", Provider.DnsAPI.getName());
		
		params.put("url", url);
		params.put("username", username);
		params.put("password", password);
		
		final Map<String, Object> hostDetails = new HashMap<String, Object>();
		hostDetails.putAll(params);

		Host host = Transaction.execute(new TransactionCallbackWithException<Host, CloudRuntimeException>() {

			@Override
			public Host doInTransaction(TransactionStatus status) throws CloudRuntimeException {
				try {
					DnsAPIResource resource = new DnsAPIResource();
					resource.configure(Provider.DnsAPI.getName(), hostDetails);
					
					Host host = _resourceMgr.addHost(zoneId, resource, resource.getType(), params);
					
					if (host == null) {
						throw new CloudRuntimeException("Failed to add DNS API host");
					}
					
					// Validate username and password by logging in
					SignInCommand cmd = new SignInCommand(username, password);
					Answer answer = callCommand(cmd, zoneId);
					if (answer == null || !answer.getResult()) {
						// Could not sign in on DNS API
						throw new ConfigurationException("Could not sign in on DNS API. Please verify URL, username and password.");
					}
					
					return host;
				} catch (ConfigurationException e) {
					throw new CloudRuntimeException(e);
				}
			}
		});
		
		return host;
	}
	
	private DnsAPINetworkVO getDnsAPINetworkVO(Network network) {
		return _dnsapiNetworkDao.findByNetworkId(network.getId());
	}
	
	private DnsAPIVirtualMachineVO getDnsAPIVirtualMachineVO(long vmId, Long domainId) {
		return _dnsapiVmDao.findByVirtualMachineIdAndDomainId(vmId, domainId);
	}
	
	private Domain getOrCreateDomain(Long zoneId, String domainName, boolean reverse) {
		Long templateId = Long.valueOf(_configDao.getValue(Config.DNSAPITemplateId.key()));
		if (templateId == null) {
			throw new CloudRuntimeException("TemplateId for domain is not set up in the global configs");
		}

    	// Check if domain already exists
    	Command cmdList;
    	if (reverse) {
    		cmdList = new ListReverseDomainCommand(domainName);
    	} else {
    		cmdList = new ListDomainCommand(domainName);
    	}
    	Answer answerList = callCommand(cmdList, zoneId);
    	List<Domain> domainList = ((DnsAPIDomainListResponse) answerList).getDomainList();
    	if (domainList.size() == 0) {
    		// Doesn't exist yet, create it
    		Command cmdCreate;
    		if (reverse) {
    			cmdCreate = new CreateReverseDomainCommand(domainName, templateId, AUTHORITY_TYPE);
    		} else {
    			cmdCreate = new CreateDomainCommand(domainName, templateId, AUTHORITY_TYPE);
    		}
        	Answer answerCreateDomain = callCommand(cmdCreate, zoneId);
        	return ((DnsAPIDomainResponse) answerCreateDomain).getDomain();
    	} else if (domainList.size() == 1) {
    		return domainList.get(0);
    	} else {
    		throw new CloudRuntimeException("Multiple domains already exist in DNS API");
    	}
	}
		
	private void removeDomain(Long zoneId, Long domainId, boolean reverse) {
    	// Check if domain exists
		Command cmdInfo;
		if (reverse) {
			cmdInfo = new GetReverseDomainInfoCommand(domainId);
		} else {
			cmdInfo = new GetDomainInfoCommand(domainId);
		}
    	Answer answerInfo = callCommand(cmdInfo, zoneId);
    	Domain domain = ((DnsAPIDomainResponse) answerInfo).getDomain();
    	if (domain == null) {
    		// Domain doesn't exist in DNS API
    		// Do nothing, continue
    	} else {
    		// Remove domain
    		Command cmdRemove;
    		if (reverse) {
    			cmdRemove = new RemoveReverseDomainCommand(domainId);
    		} else {
    			cmdRemove = new RemoveDomainCommand(domainId);
    		}
    		callCommand(cmdRemove, zoneId);
    	}
	}
		
	private void saveDomainDB(Network network, Domain createdDomain, Domain reverseDomain) {
		
    	DnsAPINetworkVO dnsapiNetworkVO = _dnsapiNetworkDao.findByNetworkId(network.getId());
    	if (dnsapiNetworkVO == null) {
    		// Entry in mapping table doesn't exist yet, create it
    		dnsapiNetworkVO = new DnsAPINetworkVO(network.getId(), createdDomain.getId(), reverseDomain.getId());
    		_dnsapiNetworkDao.persist(dnsapiNetworkVO);
    	} else {
    		// An entry already exists
    		if (dnsapiNetworkVO.getNetworkId() == network.getId() && dnsapiNetworkVO.getDnsapiDomainId() == createdDomain.getId() && dnsapiNetworkVO.getDnsapiReverseDomainId() == reverseDomain.getId()) {
    			// All the same, entry already exists, nothing to do
    			return;
    		} else {
    			// Outdated info, update it
    			DnsAPINetworkVO newDnsapiNetworkVO = new DnsAPINetworkVO(network.getId(), createdDomain.getId(), reverseDomain.getId());
    			_dnsapiNetworkDao.update(dnsapiNetworkVO.getId(), newDnsapiNetworkVO);
    		}
    	}
	}
	
	private Record createOrUpdateRecord(Long zoneId, long domainId, String name, String content, boolean reverse) {
		// Check if record already exists
		ListRecordCommand cmdList;
		if (reverse) {
			cmdList = new ListRecordCommand(domainId, content);
		} else {
			cmdList = new ListRecordCommand(domainId, name);
		}
		Answer answerList = callCommand(cmdList, zoneId);
		List<Record> recordList = ((DnsAPIRecordListResponse) answerList).getRecordList();
		Command cmd;
		if (recordList.size() == 0) {
    		// Doesn't exist yet, create it
			if (reverse) {
	        	cmd = new CreateRecordCommand(domainId, name, content, REVERSE_RECORD_TYPE); // Reverse record
			} else {
	        	cmd = new CreateRecordCommand(domainId, name, content, RECORD_TYPE); // Regular record
	    	}
		} else if (recordList.size() == 1) {
			// Record already exists, we should update it with newer info
			if (reverse) {
				cmd = new UpdateRecordCommand(domainId, recordList.get(0).getId(), name, content, REVERSE_RECORD_TYPE);
			} else {
				cmd = new UpdateRecordCommand(domainId, recordList.get(0).getId(), name, content, RECORD_TYPE);
			}
		} else {
			throw new CloudRuntimeException("Multiple records in DNS API");
		}
    	Answer answerCreateRecord = callCommand(cmd, zoneId);
    	return ((DnsAPIRecordResponse) answerCreateRecord).getRecord();
	}
	
	private void removeRecord(Long zoneId, Long recordId) {
    	// Check if record exists
    	GetRecordInfoCommand cmdInfo = new GetRecordInfoCommand(recordId);
    	Answer answerInfo = callCommand(cmdInfo, zoneId);
    	Record record = ((DnsAPIRecordResponse) answerInfo).getRecord();
    	if (record == null) {
    		// Record doesn't exist in DNS API
    		// Do nothing, continue
    	} else {
    		// Remove record
    		RemoveRecordCommand cmdRemove = new RemoveRecordCommand(recordId);
    		callCommand(cmdRemove, zoneId);
    	}
	}
	
	protected void saveRecordDB(VirtualMachineProfile vm, Long domainId, Record record) {
		
		long vmId = vm.getId();
    	DnsAPIVirtualMachineVO dnsapiVMVO = this.getDnsAPIVirtualMachineVO(vmId, domainId);
    	if (dnsapiVMVO == null) {
    		// Entry in mapping table doesn't exist yet, create it
    		dnsapiVMVO = new DnsAPIVirtualMachineVO(vmId, domainId, record.getId());
    		_dnsapiVmDao.persist(dnsapiVMVO);
    	} else {
    		// An entry already exists
    		// Check if it needs to be updated
    		if (dnsapiVMVO.getVirtualMachineId() == vmId && dnsapiVMVO.getDnsapiRecordId() == record.getId()) {
    			// All the same, entry already exists, nothing to do
    			return;
    		} else {
    			// Outdated info, update it
    			dnsapiVMVO.setVirtualMachineId(vmId);
    			dnsapiVMVO.setDnsapiRecordId(record.getId());
    			_dnsapiVmDao.update(dnsapiVMVO.getId(), dnsapiVMVO);
    		}
    	}
	}
	
	private void scheduleBindExport(Long zoneId) {
		try {
			ScheduleExportCommand cmdExport = new ScheduleExportCommand();
	    	Answer answerExport = callCommand(cmdExport, zoneId);
	    	Export export = ((DnsAPIExportResponse) answerExport).getExport();
	    	if (export != null) {
	    		s_logger.debug(export.getResult());
	    	}
		} catch (CloudRuntimeException e) {
			// fail on export never rollback transaction
			s_logger.warn("Error schedule DNSAPI to export. Hosts will delay to appear in DNS", e);
		}
	}

}
