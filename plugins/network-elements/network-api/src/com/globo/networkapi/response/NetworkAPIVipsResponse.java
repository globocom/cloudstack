package com.globo.networkapi.response;

import java.util.ArrayList;
import java.util.List;

import org.apache.cloudstack.api.BaseResponse;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;

public class NetworkAPIVipsResponse extends Answer {

	private List<Vip> vipsList;
	
    public NetworkAPIVipsResponse(Command command, List<Vip> vipsList) {
    	super(command, true, null);
    	this.vipsList = vipsList;
    }

	public List<Vip> getVipsList() {
		return vipsList;
	}
	
	public static class Vip {
		private Long id;
		private String name;
		private String ip;
		private String network;
		private String cache;
		private String method;
		private String persistence;
		private String healthcheckType;
		private String healthcheck;
		private Integer maxConn;
		private List<String> ports;
		private List<Real> reals;

		public Long getId() {
			return id;
		}
		public void setId(Long id) {
			this.id = id;
		}
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public String getIp() {
			return ip;
		}
		public void setIp(String ip) {
			this.ip = ip;
		}
		public String getNetwork() {
			return network;
		}
		public void setNetwork(String network) {
			this.network = network;
		}
		public String getCache() {
			return cache;
		}
		public void setCache(String cache) {
			this.cache = cache;
		}
		public String getMethod() {
			return method;
		}
		public void setMethod(String method) {
			this.method = method;
		}
		public String getPersistence() {
			return persistence;
		}
		public void setPersistence(String persistence) {
			this.persistence = persistence;
		}
		public String getHealthcheckType() {
			return healthcheckType;
		}
		public void setHealthcheckType(String healthcheckType) {
			this.healthcheckType = healthcheckType;
		}
		public String getHealthcheck() {
			return healthcheck;
		}
		public void setHealthcheck(String healthcheck) {
			this.healthcheck = healthcheck;
		}
		public Integer getMaxConn() {
			return maxConn;
		}
		public void setMaxConn(Integer maxConn) {
			this.maxConn = maxConn;
		}
		public List<String> getPorts() {
			return ports;
		}
		public void setPorts(List<String> ports) {
			this.ports = ports;
		}
		public List<Real> getReals() {
			return reals;
		}
		public void setReals(List<Real> reals) {
			this.reals = reals;
		}
	}

	public static class Real extends BaseResponse {
		private String vmName;
		
		private String ip;
		
		private List<String> ports;
		
		private Boolean state;

		public String getVmName() {
			return vmName;
		}

		public void setVmName(String vmName) {
			this.vmName = vmName;
		}

		public String getIp() {
			return ip;
		}

		public void setIp(String ip) {
			this.ip = ip;
		}

		public List<String> getPorts() {
			if (ports == null) {
				ports = new ArrayList<String>();
			}
			return ports;
		}

		public void setPorts(List<String> ports) {
			this.ports = ports;
		}

		public Boolean getState() {
			return state;
		}

		public void setState(Boolean state) {
			this.state = state;
		}
	}
}
