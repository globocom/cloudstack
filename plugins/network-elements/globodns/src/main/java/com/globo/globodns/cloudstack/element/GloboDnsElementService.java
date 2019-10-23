// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.globo.globodns.cloudstack.element;

import com.cloud.host.Host;
import com.cloud.utils.component.PluggableService;

public interface GloboDnsElementService extends PluggableService {

    public Host addGloboDnsHost(Long pNtwkId, String username, String password, String url);

    public boolean validateDnsRecordForLoadBalancer(String lbDomain, String lbRecord, String lbRecordContent, Long zoneId, boolean skipDnsError);

    public boolean createDnsRecordForLoadBalancer(String lbDomain, String lbRecord, String lbIpAddress, Long zoneId);

    public boolean removeDnsRecordForLoadBalancer(String lbUuid, String lbDomain, String lbRecord, String lbIpAddress, Long zoneId);

    public boolean registerVmDomain(Long zoneId, String nicUuid, String hostName, String ipAddress, String networkDomain, boolean isIpv6, boolean skipDnsError);

    public boolean createDnsRecordForLoadBalancer(GloboDnsTO globoDns, boolean skipDnsError);
}
