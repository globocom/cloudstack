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

(function(cloudStack, $) {

    cloudStack.sections.loadbalancer.listView.detailView.tabs["details"] = {
        title: 'label.details',
        fields: [{
            id: {
                label: 'label.id'
            }
            },{
            linkedlb: {
                label: 'Linked with:',
                isHidden: function(args) {
                    if (typeof(args.context.loadbalancers[0].isLinked) == 'undefined') {
                        return false;
                    } else {
                        return true;
                    }
                }
            },
            dns_registry:{
                label:'DNS Registered'
            },
            name: {
                label: 'label.fqdn'
            },
            publicip: {
                label: 'label.ip'
            },
            ports: {
                label: 'label.port'
            },
            algorithm: {
                label: 'label.algorithm'
            },
            cache: {
                label: 'Cache'
            },
            dsr: {
                label: 'DSR'
            },
            stickiness: {
                label: 'label.stickiness'
            },
        }],
        tags: cloudStack.api.tags({
            resourceType: 'LoadBalancer',
            contextId: 'loadbalancers'
        }),
        dataProvider: function(args) {
            if (!args.jsonObj) {
                args.jsonObj = args.context.loadbalancers[0];
            }
            $.ajax({
                url: createURL("listLBStickinessPolicies"),
                data: {
                    lbruleid: args.jsonObj.id
                },
                dataType: "json",
                async: false,
                    success: function(json) {
                        var response = json.listlbstickinesspoliciesresponse.stickinesspolicies[0];
                        var stickiness = "";
                        if (!response || !response.stickinesspolicy ||
                            !response.stickinesspolicy[0] || !response.stickinesspolicy[0].name) {
                            stickiness = "None";
                        } else {
                            stickiness = response.stickinesspolicy[0].name;
                        }
                        args.jsonObj.stickiness = stickiness;
                    },
                error: function (errorMessage) {
                    args.response.error(errorMessage);
                }
            });

            $.ajax({
                url: createURL("getGloboResourceConfiguration"),
                data: {
                    resourceid: args.jsonObj.id,
                    resourcetype: 'LOAD_BALANCER',
                    resourcekey: 'isDNSRegistered'
                },
                dataType: "json",
                async: false,
                    success: function(json) {
                        if(json.getgloboresourceconfigurationresponse.globoresourceconfiguration.configurationvalue == null){
                            args.jsonObj["dns_registry"] = "Yes"
                        } else {
                            args.jsonObj["dns_registry"] = json.getgloboresourceconfigurationresponse.globoresourceconfiguration.configurationvalue == "true" ? "Yes" : "No";
                        }
                },
                error: function (errorMessage) {
                    args.jsonObj["dns_registry"] = "Yes";
                }
            });

            $.ajax({
                url: createURL("getGloboResourceConfiguration"),
                data: {
                    resourceid: args.jsonObj.id,
                    resourcetype: 'LOAD_BALANCER',
                    resourcekey: 'dsr'
                },
                dataType: "json",
                async: false,
                    success: function(json) {
                        if(json.getgloboresourceconfigurationresponse.globoresourceconfiguration.configurationvalue == null){
                            args.jsonObj["dsr"] = "No"
                        } else {
                            args.jsonObj["dsr"] = json.getgloboresourceconfigurationresponse.globoresourceconfiguration.configurationvalue == "true" ? "Yes" : "No";
                        }
                }
            });

            args.response.success({
                data: args.jsonObj
            });
        },
    }



}(cloudStack, jQuery));