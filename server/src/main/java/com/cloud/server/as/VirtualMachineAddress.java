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
package com.cloud.server.as;

public class VirtualMachineAddress {

    private String ipAddress;
    private String hostName;

    public VirtualMachineAddress(String ipAddress, String hostName) {
        this.ipAddress = ipAddress;
        this.hostName = hostName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getHostName() {
        return hostName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VirtualMachineAddress)) return false;

        VirtualMachineAddress vmAddress = (VirtualMachineAddress) o;

        if (hostName != null ? !hostName.equals(vmAddress.hostName) : vmAddress.hostName != null) return false;
        if (ipAddress != null ? !ipAddress.equals(vmAddress.ipAddress) : vmAddress.ipAddress != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = ipAddress != null ? ipAddress.hashCode() : 0;
        result = 31 * result + (hostName != null ? hostName.hashCode() : 0);
        return result;
    }
}
