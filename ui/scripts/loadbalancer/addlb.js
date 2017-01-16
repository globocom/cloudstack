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
    var healthcheckTypes = cloudStack.sections.loadbalancer.utils.healthcheck;
    var msg_validation_healthcheck_http = healthcheckTypes.validationMsg;

    var networks = [];

    cloudStack.sections.loadbalancer.listView.actions["add"] = {
       label: 'Create new Load Balancer',
       preAction: function() {
           var message;
           $.ajax({
               url: createURL('listGloboLbNetworks'),
               data: {
                   supportedservices: 'lb'
               },
               dataType: "json",
               async: false,
               success: function(json) {
                   networks = [];
                   $(json.listnetworksresponse.network).each(function() {
                       networks.push({id: this.id,
                                      description: this.name,
                                      service: this.service,
                                      physicalnetworkid: this.physicalnetworkid,
                                      networkofferingid: this.networkofferingid,
                       });
                   });
               },
               error: function(json) {
                   message = parseXMLHttpResponse(json);
               }
           });

           if (networks.length === 0) {
               cloudStack.dialog.notice({
                   message: message || 'There are no networks. Please create a network before creating a load balancer.'
               });
               return false;
           }
           return true;
       },
       createForm: {
           fields: {
               name: {
                   label: 'label.name',
                   validation: {
                       required: true,
                       noUnderscore : true
                   }
               },
               lbdomain: {
                   label: 'LB Domain',
                   validation: {
                       required: true
                   },
                   select: function(args) {
                       var lbdomains = [];
                       $.ajax({
                           url: createURL("listGloboNetworkCapabilities"),
                           dataType: "json",
                           async: false,
                           success: function(json) {
                               var response = json.listglobonetworkcapabilitiesresponse;
                               $(json.listglobonetworkcapabilitiesresponse.globoNetworkCapability.allowedLbSuffixes).each(function() {
                                   lbdomains.push({id: this.valueOf(), description: this.valueOf()});
                               });
                           },
                           error: function(json) {
                               args.response.error(parseXMLHttpResponse(json));
                           }
                       });
                       args.response.success({
                           data: lbdomains
                       });
                   }
               },
               ports: {
                   label: 'label.port',
                   docID: 'helpLbPorts',
                   validation: {
                       required: true
                   }
               },
               network: {
                   label: 'label.network',
                   validation: {
                       required: true
                   },
                   select: function(args) {

                       args.response.success({
                           data: networks
                       });
                   }
               },
               lbenvironment: {
                   label: 'LB Environment',
                   validation: {
                       required: true
                   },
                   dependsOn: ['network'],
                   select: function(args) {
                       var network = findNetwork(args.data.network);

                       $.ajax({
                           url: createURL("listGloboNetworkLBEnvironments"),
                           data: {
                               physicalnetworkid: network.physicalnetworkid,
                               networkid: network.id
                           },
                           dataType: "json",
                           async: false,
                           success: function(json) {
                               var data = [];
                               $(json.listglobonetworklbenvironmentsresponse.globonetworklbenvironments).each(function() {
                                   data.push({id: this.id, name: this.name, description: this.name});
                               });
                               args.response.success({
                                   data: data
                               });
                           },
                           error: function(json) {
                               args.response.error(parseXMLHttpResponse(json));
                           }
                       });
                   }
               },
               algorithm: {
                   label: 'label.algorithm',
                   validation: {
                       required: true
                   },
                   dependsOn: ['network'],
                   select: function(args) {
                       var network = findNetwork(args.data.network);


                       var lbService = $.grep(network.service, function(service) {
                           return service.name == 'Lb';
                       })[0];

                       var algorithmCapabilities = $.grep(
                           lbService.capability,
                           function(capability) {
                               return capability.name == 'SupportedLbAlgorithms';
                           }
                       )[0];

                       var algorithms = algorithmCapabilities.value.split(',');
                       var data = [];
                       $(algorithms).each(function() {
                           data.push({id: this.valueOf().trim(), name: this.valueOf().trim(), description: _l('label.lb.algorithm.' + this.valueOf().trim())});
                       });
                       args.response.success({
                           data: data
                       });
                   },
               },

               isLbAdvanced: {
                   label: 'label.show.advanced.settings',
                   dependsOn: ['network'],
                   isBoolean: true,
                   defaultValue: false,
                   isChecked: false,
               },
               cachegroup: {
                   label: 'Cache',
                   isHidden: function (args) {
                       var isAdvancedChecked = $('input[name=isLbAdvanced]:checked').length > 0;
                       return !isAdvancedChecked;
                   },
                   validation: {
                       required: true
                   },
                   defaultValue: "(nenhum)",
                   dependsOn: ['lbenvironment','isLbAdvanced'],
                   select: function(args) {
                       var network;
                       if ( args.data.lbenvironment !== '' ){
                           $.ajax({
                               url: createURL("listGloboNetworkLBCacheGroups"),
                               data: {
                                   lbenvironment: args.data.lbenvironment,
                                   networkid: args.data.network
                               },
                               dataType: "json",
                               async: false,
                               success: function(json) {
                                   var data = [];
                                   $(json.listglobonetworklbcachegroupsresponse.globonetworkcachegroups).each(function() {
                                       data.push({id: this.name, name: this.name, description: this.name});
                                   });
                                   args.response.success({
                                       data: data
                                   });
                               },
                               error: function(json) {
                                   args.response.error(parseXMLHttpResponse(json));
                               }
                           });
                       }
                   }
               },

               dsr: {
                   label: 'DSR',
                   dependsOn: ['isLbAdvanced'],
                   isHidden: function (args) {
                       var isAdvancedChecked = $('input[name=isLbAdvanced]:checked').length > 0;
                       return !isAdvancedChecked;
                   },
                   isBoolean: true,
                   defaultValue: false,
                   isChecked: false,
               },

               sticky: {
                   label: 'label.stickiness',
                   isHidden: function (args) {
                       var isAdvancedChecked = $('input[name=isLbAdvanced]:checked').length > 0;
                       return !isAdvancedChecked;
                   },
                   dependsOn: ['network', 'isLbAdvanced'],
                   select: function(args) {
                       var network = networks[0];

                       var lbService = $.grep(network.service, function(service) {
                           return service.name == 'Lb';
                       })[0];

                       var stickinessCapabilities = $.grep(
                           lbService.capability,
                           function(capability) {
                               return capability.name == 'SupportedStickinessMethods';
                           }
                       )[0];

                       var stickinessMethods = jQuery.parseJSON(stickinessCapabilities.value);
                       var data = [];
                       // Default None value
                       data.push({id: 'None', name: 'None', description: 'None'});
                       $(stickinessMethods).each(function() {
                           data.push({id: this.methodname, name: this.methodname, description: this.methodname});
                       });
                       args.response.success({
                           data: data
                       });
                   },
               },
               // #hlb
               healthchecktype: {
                   label: 'Health check type',
                   docID: 'helpHealthcheckType',
                   isHidden: function (args) {
                       var isAdvancedChecked = $('input[name=isLbAdvanced]:checked').length > 0;
                       return !isAdvancedChecked;
                   },
                   dependsOn: ['isLbAdvanced'],
                   select: function(args) {
                       args.response.success({
                           data: healthcheckTypes.values
                       });
                       args.$select.change(function() {
                           var type = $(this).val()
                           if (healthcheckTypes.isLayer4(type)) {
                               $("div[rel='healthcheck']").hide()
                               $("div[rel='expectedhealthcheck']").hide()
                           } else {
                               $("div[rel='healthcheck']").show()
                               $("div[rel='expectedhealthcheck']").show()
                           }
                       })
                   }
               },
               healthcheck: {
                   label: 'Health check request'
               },
               expectedhealthcheck: {
                   label: 'Expected health check',
                   select: function(args) {
                       var expectedHealthcheck = [];
                       $.ajax({
                           url: createURL("listGloboNetworkExpectedHealthchecks"),
                           data: {
                           },
                           async: false,
                           success: function(json) {
                               expectedHealthchecksR = json.listgloboNetworkexpectedhealthchecksresponse.globonetworkexpectedhealthcheck
                               $(expectedHealthchecksR).each(function() {
                                   expectedHealthcheck.push({id: this.expected, name: this.expected, description: this.expected})
                               });
                               args.response.success({
                                   data: expectedHealthcheck
                               });

                           }
                       })


                   }
               }

           },
       },
       action: function(args) {
           if (!validateLb(args)) {
                return;
           };

           data = {
              networkid: args.data.network,
              lbenvironmentid: args.data.lbenvironment
           };

           data = buildLoadBalancerData(args);

           $.ajax({
               url: createURL("createGloboLoadBalancer"),
               data: data,
               dataType: "json",
               success: function(json) {
                    var jobID = json.creategloboloadbalancerresponse.jobid;
                    args.response.success({
                        _custom:{
                            jobId: jobID,
                            getUpdatedItem: function(json) {
                                var lb = json.queryasyncjobresultresponse.jobresult.loadbalancer;
                                lb.ports = lb.publicport + ':' + lb.privateport + ', ';
                                $(lb.additionalportmap).each(function() {
                                    lb.ports += this + ', ';
                                });
                                lb.ports = lb.ports.substring(0, lb.ports.length - 2); // remove last ', '


                                notifyDnsRegistered(json.queryasyncjobresultresponse.jobresult.loadbalancer);

                                return lb;
                            }
                        }
                    });
               },
           });
       },
       messages: {
           notification: function(args) {
               return 'Create Load Balancer';
           }
       },
       notification: {
           label: 'Load Balancer created',
           poll: pollAsyncJobResult
       }
    }


    var show_error_message = function(args, msg) {
           if (typeof(msg) == 'string') {
               args.response.error(msg);
           } else if (typeof(msg) == 'object') {
               args.response.error(parseXMLHttpResponse(msg));
           }
    };

    var validateLb = function(args){
        if (args.data.name.indexOf("_") > 0) {
           args.response.error("Underscore(_) is not allowed in Load Balancer names");
           return false;
        }

        if ( healthcheckTypes.isLayer7(args.data.healthchecktype) ) {
           healthcheckPingPath = args.data.healthcheck.valueOf().trim();
           if ( healthcheckPingPath === ''){
               args.response.error(msg_validation_healthcheck_http);
               return false;
           }
        }

        var network = $.grep(networks, function(nettemp){
            return args.data.network == nettemp.id
        })[0];

        var networkoffering = findNetworkOffering(network.networkofferingid);

        var lbService = $.grep(networkoffering.service, function(service) {
            return service.name == 'Lb';
        })[0];

        var provider = lbService.provider[0].name;
        if (provider != 'GloboNetwork') {
            args.response.error("Your network is provided by " + provider + ". Please, choose only network is provided by GloboNetwork");
            return false;
        }

        if (!validatePortsMap(args, args.data.ports, args.data.dsr)) {
            return false;
        };


        return true;
    };



    var findNetworkOffering = function(id) {
        var networkoffering;
        $.ajax({
            url: createURL("listNetworkOfferings"),
            data: {
                id: id
            },
            dataType: "json",
            async: false,
            success: function(json) {
                networkoffering = json.listnetworkofferingsresponse.networkoffering[0];

            },
            error: function(json) {
                args.response.error(parseXMLHttpResponse(json));
            }
        });
        return networkoffering;
    }

    var validatePortsMap = function(args, portsInString, dsr) {
        var portList = portsInString.replace(/[^\d\:\,]/g, "").replace(/\,+$/g, "").split(",");

        var portsMap = [];
        //check format
        if (portList.length > 0) {
            $.each(portList, function() {
                if (this.indexOf(":") == -1) {
                    args.response.error("Invalid ports. It should be in the form \"80:8080,443:8443\"");
                    return false;
                }
            });
        }

        //check duplicate public port
        var publicPorts = new Set();
        $.each(portList, function() {
            publicPorts.add(this.split(":")[0])
        });

        if (publicPorts.size != portList.length) {
            args.response.error("Invalid ports. It should be in the form \"80:8080,443:8443\"");
            return false;
        }

        if(dsr == 'on'){
            var publicPorts = new Array();
            $.each(portList, function() {
                publicPorts.push(this.split(":")[0])
            });

            var privatePorts = new Array();
            $.each(portList, function() {
                privatePorts.push(this.split(":")[1])
            });

            for(var i=0 ; i < portList.length; i++){
                if(publicPorts[i] != privatePorts[i]){
                    args.response.error("In DSR load balancer the public port must always be the same as private port.");
                    return false;
                }
            }
        }

        return true;

    }
    var findNetwork = function(networkuuid) {
        for (var i = 0; i < networks.length; i++) {
            if (networks[i].id === networkuuid) {
                return networks[i];
            }
        }
        return null;
    }

    var notifyDnsRegistered = function(loadbalancer){
        var configs = loadbalancer.globoresourceconfig;
        var isLoadBalancerDNSRegistered = false;
        var hasDnsRegiteredConfig = false;
        for (var i = 0; i < configs.length; i++) {
            if ( configs[i].configurationkey == "isDNSRegistered" ) {
                hasDnsRegiteredConfig = true;
                isLoadBalancerDNSRegistered = configs[i].configurationvalue == "true";
            }
        }

        if (hasDnsRegiteredConfig && !isLoadBalancerDNSRegistered) {
            cloudStack.dialog.notice({message: "The LoadBalancer DNS was not registered. You can retry to register DNS later."});
        }
    }

    var buildLoadBalancerData = function(args) {
        var ports =args.data.ports.split(",");
        additionalportmap = ports.slice(1, ports.length);

        var firstport = ports[0].split(":");


        var healthcheckrequest = '';
        var expectedhealthcheck = '';
        if (healthcheckTypes.isLayer7(args.data.healthchecktype)) {
            healthcheckrequest = args.data.healthcheck.valueOf().trim()
            expectedhealthcheck = args.data.expectedhealthcheck
        }

        var data = {
           algorithm:            args.data.algorithm,
           name:                 args.data.name + args.data.lbdomain,
           publicport:           firstport[0],
           privateport:          firstport[1],
           openfirewall:         false,
           networkid:            args.data.network,
           cache:                args.data.cachegroup,
           dsr:                  args.data.dsr == 'on',
           healthcheckType:      args.data.healthchecktype,
           healthcheckrequest:   healthcheckrequest,
           expectedhealthcheck:  expectedhealthcheck,
           additionalportmap:    additionalportmap.join(),
           skipdnserror:         true,
           stickinessmethodname: args.data.sticky.valueOf() != 'None' ? args.data.sticky.valueOf() : "",
           lbenvironmentid:      args.data.lbenvironment
       };
       return data;

    }
    cloudStack.validatePortsMap = validatePortsMap;


}(cloudStack, jQuery));