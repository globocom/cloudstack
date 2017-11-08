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

    var healthcheckTypes = {
    	"values": [{id: 'TCP', name: 'TCP', description: 'TCP', layer: 4},
                    {id: 'UDP', name: 'UDP', description: 'UDP', layer: 4},
                    {id: 'HTTP', name: 'HTTP', description: 'HTTP', layer: 7},
                    {id: 'HTTPS', name: 'HTTPS', description: 'HTTPS', layer: 7}
        ],
    	"isLayer4": function(idProtocol) {
    	    return this.isLayer(idProtocol, 4)
    	},
    	"isLayer7": function(idProtocol) {
    	    return this.isLayer(idProtocol, 7)
    	},
    	"isLayer": function(idProtocol, layer) {
            		for (var i = 0; i < this.values.length; i++ ){
            			if ( this.values[i].id === idProtocol && this.values[i].layer == layer){ return true; }
            		};
            		return false;
        },
        "validationMsg": "<span style='font-weight: bold'>Expected Health Check</span> can not be empty when <span style='font-weight: bold'>Health Check Type</span> is <span style='font-weight: bold'>HTTP/HTTPS</span>"
    };
    var msg_validation_healthcheck_http = healthcheckTypes.validationMsg;
    var cascadeAsyncCmds = function(args) {

        var process_command = function(index, last_result) {
            var command = args.commands[index];

            var process_success = function(result, jobId) {
                command.result = result;
                if (index === args.commands.length-1) {
                    // runned last command.
                    args.success(result, jobId);
                } else {
                    // run next command
                    process_command(index+1, result);
                }
            };

            if ($.isFunction(command.data)) {
                command.data = command.data(last_result, index, args);
            }

            // sometimes, command must be skipped
            if (command.data === false) {
                process_success(false, null);
                return;
            }

            if (!command.url) {
                command.url = createURL(command.name);
            }

            $.ajax({
                url: command.url,
                data: command.data,
                dataType: "json",
                success: function(result) {
                    var jobId, timerControl;

                    // get jobid
                    for (var prop in result) {
                        if (result.hasOwnProperty(prop) && prop.match(/response$/)) {
                            jobId = result[prop].jobid;
                            break;
                        }
                    }

                    if (!jobId) {
                        // jobid not found. Synchronous command
                        process_success(result, null);
                    } else {
                        // pool jobid for completion
                        timerControl = setInterval(function() {
                            pollAsyncJobResult({
                                _custom: {
                                    jobId: jobId
                                },
                                complete: function(json) {
                                    clearInterval(timerControl);
                                    process_success(result, jobId);
                                },
                                error: function(message) {
                                    clearInterval(timerControl);
                                    args.error(message, index, args);
                                }
                            });
                        }, g_queryAsyncJobResultInterval);
                    }
                },
                error: function (json) {
                    args.error(parseXMLHttpResponse(json), index, args);
                }
            });
        };

        process_command(0, {});
    };

    var autoscaleActionfilter = function(args) {
        var jsonObj = args.context.item;

        var allowedActions = [];
        allowedActions.push("remove");

        if (jsonObj.state == "enabled")
            allowedActions.push("disable");
        else if (jsonObj.state == "disabled")
            allowedActions.push("enable");

        return allowedActions;
    };

    $.validator.addMethod("noUnderscore", function(value, element) {
        return !/[_]/.test(value);
    }, "Do not use underscore");
    $.validator.addMethod("positiveNumber", function(value, element) {
        return (/^[0-9]+/).test(value);
    }, "Use positive numbers only");



    cloudStack.sections.loadbalancer = {
        title: 'label.load.balancer',
        id: 'loadbalancer',
        listView: {
            id: 'loadbalancers',
            fields: {
                name: { label: 'label.fqdn' },
                publicip: { label: 'label.ip' },
                ports: { label: 'label.port' },
                algorithm: { label: 'label.algorithm' },
                state: {
                    converter: function(str) {
                        // For localization
                        return str;
                    },
                    label: 'label.state',
                    indicator: {
                        'Add': 'off',
                        'Active': 'on',
                        'Revoke': 'off',
                        'Deleting': 'off'
                    }
                },
            },
            dataProvider: function(args) {
                var data = {};
                listViewDataProvider(args, data);

                $.ajax({
                    url: createURL("listLoadBalancerRules"),
                    data: data,
                    dataType: "json",
                    async: true,
                    success: function(data) {
                        var loadBalancerData = data.listloadbalancerrulesresponse.loadbalancerrule;
                        $(loadBalancerData).each(function() {
                            var that = this;
                            this.ports = this.publicport + ':' + this.privateport + ', ';
                            $(this.additionalportmap).each(function() {
                                that.ports += this + ', ';
                            });
                            this.ports = this.ports.substring(0, this.ports.length - 2); // remove last ', '
                            if (typeof(this.linkedloadbalancer) != 'undefined') {
                                this.linkedlb = this.linkedloadbalancer.name;
                            }
                        });
                        args.response.success({ data: loadBalancerData });
                    },
                    error: function(errorMessage) {
                        args.response.error(errorMessage);
                    }
                });
            },
            detailView: {
                name: 'Load Balancer Details',
                isMaximized: true,
                noCompact: true,
                tabs: {
                    // see loadbalancer/tabDetails.js
                    details: {},
                    networks: {
                        title: 'Networks',
                        listView: {
                            id: 'networks',
                            fields: {
                                name: { label: 'label.name' },
                                cidr: { label: 'label.cidr' },
                            },
                            dataProvider: function(args) {
                                // Make sure load balancer object is always up to date

                                $.ajax({
                                    url: createURL("listLoadBalancerRules"),
                                    data: {
                                        id: args.context.loadbalancers[0].id,
                                    },
                                    dataType: "json",
                                    async: false,
                                    success: function(json) {
                                        args.context.loadbalancers[0] = json.listloadbalancerrulesresponse.loadbalancerrule[0];
                                    },
                                    error: function(errorMessage) {
                                        args.response.error(errorMessage);
                                    }
                                });

                                var networkidslist = [];
                                networkidslist.push(args.context.loadbalancers[0].networkid);
                                networkidslist = networkidslist.concat(args.context.loadbalancers[0].additionalnetworkids);

                                var networks = [];
                                $(networkidslist).each(function() {
                                    $.ajax({
                                        url: createURL('listGloboLbNetworks'),
                                        data: {
                                            id: this.valueOf()
                                        },
                                        async: false,
                                        success: function(json) {
                                            var network = json.listnetworksresponse.network[0];
                                            if(!network.cidr){
                                                network.cidr = network.ip6cidr;
                                            }
                                            networks.push(network);
                                        },
                                        error: function(errorMessage) {
                                            args.response.error(errorMessage);
                                        }
                                    });
                                });
                                args.response.success({ data: networks });
                            },
                            actions: {
                                remove: {
                                    label: 'label.delete',
                                    messages: {
                                        confirm: function(args) {
                                            return 'Are you sure you want to disassociate network ' + args.context.networks[0].name + ' from load balancer ' + args.context.loadbalancers[0].name + '?';
                                        },
                                        notification: function(args) {
                                            return 'Remove Network From Load Balancer';
                                        }
                                    },
                                    action: function(args) {
                                        $.ajax({
                                            url: createURL("removeNetworksFromLoadBalancerRule"),
                                            data: {
                                                id: args.context.loadbalancers[0].id,
                                                networkids: args.context.networks[0].id
                                            },
                                            dataType: "json",
                                            success: function(json) {
                                                args.response.success({
                                                    _custom: {
                                                        jobId: json.removenetworksfromloadbalancerruleresponse.jobid,
                                                        fullRefreshAfterComplete: true
                                                    },
                                                });
                                            },
                                            error: function(errorMessage) {
                                                args.response.error(parseXMLHttpResponse(errorMessage));
                                            }
                                        });
                                    },
                                    notification: {
                                        poll: pollAsyncJobResult
                                    }
                                },
                                add: {
                                    label: 'Associate Network to Load Balancer',
                                    createForm: {
                                        title: 'Associate Network to Load Balancer',
                                        fields: {
                                            network: {
                                                label: 'label.network',
                                                validation: { required: true },
                                                select: function(args) {
                                                    var networks = [];
                                                    $.ajax({
                                                        url: createURL('listGloboLbNetworks'),
                                                        data: {
                                                            supportedservices: 'lb'
                                                        },
                                                        dataType: "json",
                                                        async: false,
                                                        success: function(json) {
                                                            var lb = args.context.loadbalancers[0];
                                                            $(json.listnetworksresponse.network).each(function() {
                                                                // Remove those that are already associated to load balancer
                                                                if (lb.networkid != this.id && lb.additionalnetworkids.indexOf(this.id) === -1) {
                                                                    networks.push({id: this.id, description: this.name});
                                                                }
                                                            });
                                                        }
                                                    });
                                                    args.response.success({
                                                        data: networks
                                                    });
                                                }
                                            }
                                        },
                                    },
                                    action: function(args) {
                                        var lbruleid = args.context.loadbalancers[0].id;
                                        $.ajax({
                                            url: createURL("assignNetworksToLoadBalancerRule"),
                                            data: {
                                                id: lbruleid,
                                                networkids: args.data.network
                                            },
                                            dataType: "json",
                                            success: function(json) {
                                                args.response.success({
                                                    _custom: {
                                                        jobId: json.assignnetworkstoloadbalancerruleresponse.jobid,
                                                        fullRefreshAfterComplete: true
                                                    },
                                                });
                                            },
                                            error: function(errorMessage) {
                                                args.response.error(parseXMLHttpResponse(errorMessage));
                                            }
                                        });
                                    },
                                    messages: {
                                        notification: function(args) {
                                            return 'Assign network to Load Balancer';
                                        }
                                    },
                                    notification: {
                                        poll: pollAsyncJobResult
                                    }
                                }
                            }
                        }
                    },
                    // see loadbalancer/tabVms.js
                    vms: {},
                    // see loadbalancer/tabPools.js
                    pools: {},
                    autoscale: {
                        title: 'label.autoscale',
                        listView: {
                            id: 'autoscalegroups',
                            hideSearchBar: true,
                            fields: {
                                id: { label: 'label.id' },
                                minmembers: { label: 'Min VMs' },
                                deployedvms: { label: 'AutoScale VMs' },
                                maxmembers: { label: 'Max VMs' },
                                state: {
                                    converter: function(str) {
                                        // For localization
                                        return str;
                                    },
                                    label: 'label.state',
                                    indicator: {
                                        'enabled': 'on',
                                        'disabled': 'off',
                                        'revoke': 'off',
                                    }
                                },
                            },
                            dataProvider: function(args) {
                                $.ajax({
                                    url: createURL('listAutoScaleVmGroups'),
                                    data: {
                                        listAll: true,
                                        lbruleid: args.context.loadbalancers[0].id
                                    },
                                    success: function(json) {
                                        var response = json.listautoscalevmgroupsresponse.autoscalevmgroup ?
                                            json.listautoscalevmgroupsresponse.autoscalevmgroup : [];

                                        $(response).each(function() {
                                            this.nbscaleuppolicies = this.scaleuppolicies.length;
                                            this.nbscaledownpolicies = this.scaledownpolicies.length;
                                        });

                                        $.ajax({
                                            url: createURL('listLoadBalancerRuleInstances'),
                                            data: {
                                                id: args.context.loadbalancers[0].id,
                                            },
                                            success: function(data) {
                                                lbinstances = data.listloadbalancerruleinstancesresponse.loadbalancerruleinstance ?
                                                    data.listloadbalancerruleinstancesresponse.loadbalancerruleinstance : [];
                                                if (response[0]) {
                                                    response[0].deployedvms = response[0].autoscalegroupcountmembers;
                                                }

                                                args.response.success({
                                                    actionFilter: autoscaleActionfilter,
                                                    data: response
                                                });
                                            },
                                            error: function(errorMessage) {
                                                args.response.error(errorMessage);
                                            }
                                        });
                                    },
                                    error: function (errorMessage) {
                                        args.response.error(errorMessage);
                                    }
                                });
                            },
                            detailView: {
                                name: 'AutoScale Details',
                                isMaximized: true,
                                noCompact: true,
                                tabs: {
                                    details: {
                                        title: 'label.details',
                                        fields: [{
                                            id: {
                                                label: 'label.id'
                                            },
                                            autoScaleVmGroupName: {
                                                label: 'label.name'
                                            },
                                            serviceOfferingName: {
                                                label: 'label.menu.service.offerings'
                                            },
                                            templateName: {
                                                label: 'label.template'
                                            },
                                            minInstance: {
                                                label: 'Min VMs'
                                            },
                                            autoscalegroupcountmembers: {
                                                label: 'AutoScale VMs'
                                            },
                                            maxInstance: {
                                                label: 'Max VMs'
                                            },
                                        }],
                                        tags: cloudStack.api.tags({
                                            resourceType: 'AutoScaleVmGroup',
                                            contextId: 'autoscalegroups'
                                        }),
                                        dataProvider: function(args) {
                                            $.ajax({
                                                url: createURL('listAutoScaleVmGroups'),
                                                data: {
                                                    listAll: true,
                                                    lbruleid: args.context.loadbalancers[0].id
                                                },
                                                success: function(json) {
                                                    var response = json.listautoscalevmgroupsresponse.autoscalevmgroup ?
                                                        json.listautoscalevmgroupsresponse.autoscalevmgroup : [];

                                                    $(response).each(function() {
                                                        this.nbscaleuppolicies = this.scaleuppolicies.length;
                                                        this.nbscaledownpolicies = this.scaledownpolicies.length;
                                                    });

                                                    var autoscaleVmGroup = response[0];

                                                    $.ajax({
                                                        url: createURL('listAutoScaleVmProfiles'),
                                                        data: {
                                                            listAll: true,
                                                            id: autoscaleVmGroup.vmprofileid
                                                        },
                                                        success: function(json) {
                                                            var autoscaleVmProfile = json.listautoscalevmprofilesresponse.autoscalevmprofile[0];

                                                            //get service offering name
                                                            var serviceOfferingName;
                                                            function getServiceOfferingName(name) {
                                                                serviceOfferingName = name;
                                                            }
                                                            $.ajax({
                                                                url: createURL("listServiceOfferings&issystem=false"),
                                                                data: {
                                                                    id: autoscaleVmProfile.serviceofferingid
                                                                },
                                                                dataType: "json",
                                                                async: false,
                                                                success: function(json) {
                                                                    var serviceofferings = json.listserviceofferingsresponse.serviceoffering;
                                                                    getServiceOfferingName(serviceofferings[0].name);
                                                                }
                                                            });

                                                            //get template name
                                                            var templateName;
                                                            function getTemplateName(name) {
                                                                templateName = name;
                                                            }
                                                            $.ajax({
                                                                url: createURL("listTemplates"),
                                                                data: {
                                                                    templatefilter: 'executable',
                                                                    id: autoscaleVmProfile.templateid
                                                                },
                                                                dataType: "json",
                                                                async: false,
                                                                success: function(json) {
                                                                     var templates = json.listtemplatesresponse.template;
                                                                     getTemplateName(templates[0].name);
                                                                }
                                                            });

                                                            var originalAutoscaleData = {
                                                                id: autoscaleVmGroup.id,
                                                                autoscalegroupcountmembers: autoscaleVmGroup.autoscalegroupcountmembers,
                                                                templateName: templateName,
                                                                serviceOfferingId: autoscaleVmProfile.serviceofferingid,
                                                                serviceOfferingName: serviceOfferingName,
                                                                minInstance: autoscaleVmGroup.minmembers,
                                                                maxInstance: autoscaleVmGroup.maxmembers,
                                                                autoScaleVmGroupName: autoscaleVmGroup.autoscalegroupvmprefixname,
                                                                context: {
                                                                    autoscaleVmGroup: autoscaleVmGroup,
                                                                    autoscaleVmProfile: autoscaleVmProfile
                                                                }
                                                            };
                                                            args.response.success({
                                                                data: originalAutoscaleData
                                                            });
                                                        }
                                                    });
                                                },
                                                error: function (errorMessage) {
                                                    args.response.error(errorMessage);
                                                }
                                            });
                                        }
                                    }
                                }
                            },

                            actions: {
                                add: {
                                    label: 'Configure AutoScale',
                                    action: {
                                        custom: function(args) {
                                            args.context.networks = [];
                                            args.context.networks.push({zoneid: args.context.loadbalancers[0].zoneid});
                                            args.context.multiRules = [];
                                            args.context.multiRules.push(args.context.loadbalancers[0]);
                                            args.context.loadbalancer = args.context.loadbalancers[0];
                                            var returnFunction = cloudStack.uiCustom.autoscaler(cloudStack.autoscaler);
                                            return returnFunction(args);
                                        }
                                    },
                                    messages: {
                                        notification: function() {
                                            return 'Update AutoScale';
                                        }
                                    },
                                    notification: {
                                        poll: pollAsyncJobResult
                                    },
                                },
                                enable: {
                                    label: 'label.enable.autoscale',
                                    messages: {
                                        confirm: function(args) {
                                            return 'Are you sure you want to enable AutoScale?';
                                        },
                                        notification: function(args) {
                                            return 'label.enable.autoscale';
                                        }
                                    },
                                    action: function(args) {
                                        $.ajax({
                                            url: createURL('enableAutoScaleVmGroup'),
                                            data: {
                                                id: args.context.autoscalegroups[0].id
                                            },
                                            async: true,
                                            success: function(json) {
                                                var jid = json.enableautoscalevmGroupresponse.jobid;
                                                args.response.success({
                                                    _custom: {
                                                        jobId: jid,
                                                        getUpdatedItem: function(json) {
                                                            var response = json.queryasyncjobresultresponse.jobresult.autoscalevmgroup;
                                                            if (window.lbinstances !== undefined && response) {
                                                                response.deployedvms = lbinstances.length;
                                                            }
                                                            return response;
                                                        },
                                                        fullRefreshAfterComplete: true
                                                    }
                                                });
                                            }
                                        });
                                    },
                                    notification: {
                                        poll: pollAsyncJobResult
                                    },
                                },
                                disable: {
                                    label: 'label.disable.autoscale',
                                    messages: {
                                        confirm: function(args) {
                                            return 'Are you sure you want to disable AutoScale?';
                                        },
                                        notification: function(args) {
                                            return 'label.disable.autoscale';
                                        }
                                    },
                                    action: function(args) {
                                        $.ajax({
                                            url: createURL('disableAutoScaleVmGroup'),
                                            data: {
                                                id: args.context.autoscalegroups[0].id
                                            },
                                            async: true,
                                            success: function(json) {
                                                var jid = json.disableautoscalevmGroupresponse.jobid;
                                                args.response.success({
                                                    _custom: {
                                                        jobId: jid,
                                                        getUpdatedItem: function(json) {
                                                            var response = json.queryasyncjobresultresponse.jobresult.autoscalevmgroup;
                                                            if (window.lbinstances !== undefined && response) {
                                                                response.deployedvms = lbinstances.length;
                                                            }
                                                            return response;
                                                        },
                                                        fullRefreshAfterComplete: true
                                                    }
                                                });
                                            }
                                        });
                                    },
                                    notification: {
                                        poll: pollAsyncJobResult
                                    },
                                },
                                remove: {
                                    label: 'label.delete',
                                    messages: {
                                        confirm: function(args) {
                                            return 'Are you sure you want to remove AutoScale?';
                                        },
                                        notification: function(args) {
                                            return 'Remove Autoscale';
                                        }
                                    },
                                    action: function(args) {
                                        $.ajax({
                                            url: createURL('deleteAutoScaleVmGroup'),
                                            data: {
                                                id: args.context.autoscalegroups[0].id,
                                                removedependencies: true,
                                            },
                                            async: true,
                                            success: function(json) {
                                                var jid = json.deleteautoscalevmgroupresponse.jobid;
                                                args.response.success({
                                                    _custom: {
                                                        jobId: jid,
                                                        fullRefreshAfterComplete: true
                                                    }
                                                });
                                            }
                                        });
                                    },
                                    notification: {
                                        poll: pollAsyncJobResult
                                    },
                                }
                            },
                        },
                    },
                },
                actions: {
                    restart: {
                        label: 'Retry Register DNS for Load Balancer',
                        custom: {
                            buttonLabel: 'label.configure'
                        },
                        preFilter: function(args) {
                            isToShowRegisterDnsButton = true;
                            function showRegisterDnsButton(result){
                                isToShowRegisterDnsButton = result;
                            }
                            $.ajax({
                                url: createURL("getGloboResourceConfiguration"),
                                data: {
                                    resourceid: args.context.loadbalancers[0].id,
                                    resourcetype: 'LOAD_BALANCER',
                                    resourcekey: 'isDNSRegistered'
                                },
                                dataType: "json",
                                async: false,
                                    success: function(json) {
                                        var conf = json.getgloboresourceconfigurationresponse.globoresourceconfiguration.configurationvalue
                                        if(conf == undefined || conf == "true") {
                                            showRegisterDnsButton(false);
                                        } else if (conf == "false") {
                                            showRegisterDnsButton(true);
                                        }
                                },
                                error: function (errorMessage) {
                                    showRegisterDnsButton(false);
                                    //args.response.error(errorMessage);
                                }
                            });
                            return isToShowRegisterDnsButton;
                            
                        },
                        action: function(args) {
                            var show_error_message = function(json) {
                                args.response.error(parseXMLHttpResponse(json));
                            };
                            $.ajax({
                                url: createURL("registerDnsForResource"),
                                data: {
                                    uuid: args.context.loadbalancers[0].id,
                                    resourcetype: "LOAD_BALANCER"
                                },
                                dataType: "json",
                                async: false,
                                success: function(data) {
                                    cloudStack.ui.notifications.add({
                                            desc: 'message.registry.dns.for.load.balancer.successful',
                                            section: 'Details',
                                            poll: pollAsyncJobResult,
                                            _custom: {
                                                jobId: data.registerdnsforresourceresponse.jobid
                                            }
                                        },
                                        function() {
                                            $(window).trigger('cloudStack.fullRefresh');
                                            $('.loading-overlay').remove();
                                        }, {},
                                        show_error_message, {}
                                         // job deleteLoadBalancerRule
                                    );
                                    
                                    

                                },
                                error: function(data){
                                    $('.loading-overlay').remove();
                                    $(window).trigger('cloudStack.fullRefresh');
                                } // ajax deleteLoadBalancerRule
                            });
                        },
                        messages: {
                            confirm: function(args) {
                                return 'label.action.registry.dns.for.load.balancer';
                            },
                            notification: function() {
                                return 'Notification for Retry Register DNS';
                            },
                            complete: function(args) {
                                return 'message.registry.dns.for.load.balancer.successfull';
                            }
                        },
                        notification: {
                            label: 'DNS Registered',
                            poll: pollAsyncJobResult
                        }
                    },
                    editLoadBalancer: {
                        label: 'Edit Load Balancer',
                        custom: {
                            buttonLabel: 'label.configure'
                        },
                        action: function(args) {
                            var oldStickiness;
                            // var oldHealthcheck;

                            var lb = args.context.loadbalancers[0];

                            $.ajax({
                                url: createURL('listLBStickinessPolicies'),
                                data: {
                                    lbruleid: lb.id
                                },
                                async: false,
                                success: function(json) {
                                    var response = json.listlbstickinesspoliciesresponse.stickinesspolicies[0];
                                    if (!response || !response.stickinesspolicy ||
                                        !response.stickinesspolicy[0] || !response.stickinesspolicy[0].name) {
                                        oldStickiness = "None";
                                    } else {
                                        oldStickiness = response.stickinesspolicy[0].name;
                                    }
                                }
                            });

                            cloudStack.dialog.createForm({
                                form: {
                                    title: 'Edit Load Balancer',
                                    fields: {
                                        stickiness: {
                                            label: 'label.stickiness',
                                            defaultValue: oldStickiness,
                                            select: function(args) {
                                                var network;
                                                $.ajax({
                                                    url: createURL('listGloboLbNetworks'),
                                                    data: {
                                                        id: lb.networkid
                                                    },
                                                    dataType: "json",
                                                    async: false,
                                                    success: function(json) {
                                                        network = json.listnetworksresponse.network[0];
                                                    },
                                                    error: function(json) {
                                                        args.response.error(parseXMLHttpResponse(json));
                                                    }
                                                });

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
                                            }
                                        }
                                    }
                                },
                                after: function(args2) {
                                    var lastJobId;
                                    args.response.success({
                                        _custom: {
                                            getLastJobId: function() { return lastJobId; },
                                            getUpdatedItem: function() {
                                                var loadbalancer = null;
                                                $.ajax({
                                                    url: createURL("listLoadBalancerRules"),
                                                    data: {
                                                        id: lb.id
                                                    },
                                                    dataType: "json",
                                                    async: false,
                                                    success: function(data) {
                                                        var loadBalancerData = data.listloadbalancerrulesresponse.loadbalancerrule;
                                                        $(loadBalancerData).each(function() {
                                                            var that = this;
                                                            this.ports = this.publicport + ':' + this.privateport + ', ';
                                                            $(this.additionalportmap).each(function() {
                                                                that.ports += this + ', ';
                                                            });
                                                            this.ports = this.ports.substring(0, this.ports.length - 2); // remove last ', '
                                                        });
                                                        loadbalancer = loadBalancerData[0];
                                                    }
                                                });
                                                return loadbalancer;
                                            }
                                        }
                                    });

                                    cascadeAsyncCmds({
                                        commands: [
                                            {
                                                name: 'listLBStickinessPolicies',
                                                data: { lbruleid: lb.id }
                                            },
                                            {
                                                name: 'deleteLBStickinessPolicy',
                                                data: function(last_result) {
                                                    // If stickiness existed before and new value is different than old value
                                                    listLbStickinessResult = last_result.listlbstickinesspoliciesresponse.stickinesspolicies;
                                                    if (listLbStickinessResult &&
                                                        listLbStickinessResult[0].stickinesspolicy.length > 0 &&
                                                        listLbStickinessResult[0].stickinesspolicy.name != args2.data.stickiness.valueOf()) {
                                                        return { id: last_result.listlbstickinesspoliciesresponse.stickinesspolicies[0].stickinesspolicy[0].id };
                                                    }
                                                    // skip this command
                                                    return false;
                                                }
                                            },
                                            {
                                                name: 'createLBStickinessPolicy',
                                                data: function () {
                                                    if (args2.data.stickiness.valueOf() != 'None' && args2.data.stickiness.valueOf() != oldStickiness) {
                                                        return {
                                                            lbruleid: lb.id,
                                                            name: args2.data.stickiness.valueOf(),
                                                            methodname: args2.data.stickiness.valueOf()
                                                        };
                                                    }
                                                    return false;
                                                }
                                            }
                                        ],
                                        success: function(data, jobId) {
                                            lastJobId = jobId;
                                        },
                                        error: function(message) {
                                            lastJobId = -1;
                                            args.response.error(message);
                                        }
                                    });
                                }
                            });

                            $('.create-form').find('.cancel').bind("click", function( event, ui ) {
                                $('.loading-overlay').remove();
                                return true;
                            });
                        },
                        messages: {
                            notification: function() {
                                return 'Update Load Balancer';
                            }
                        },
                        notification: {
                            poll: function(args) {
                                var lastJobId = args._custom.getLastJobId();
                                if (lastJobId === undefined) {
                                    return;
                                } else if (lastJobId === null) {
                                    args.complete({
                                        data: args._custom.getUpdatedItem()
                                    });
                                    return;
                                }
                                args._custom.jobId = lastJobId;
                                return pollAsyncJobResult(args);
                            }
                        }
                    },
                    linkloadbalancer: {
                        label: 'Link load balancer with pools from another load balancer',
                        custom: {
                            buttonLabel: 'label.configure'
                        },
                        preFilter: function(args) {
                            var linked = args.context.loadbalancers[0].linkedloadbalancer
                            if ( typeof(linked) == 'undefined') {
                                return true;
                            }
                            return false;
                        },
                        action: function(args){
                            var lb = args.context.loadbalancers[0];
                            var result = [];
                            cloudStack.dialog.createForm({
                                form: {
                                    title: 'Link Load Balancer with pool(s) from',
                                    fields: {
                                        linkablelb: {
                                            label: 'LB',
                                            select: function(args) {
                                                $.ajax({
                                                    url: createURL("listGloboLinkableLoadBalancers"),
                                                    data: {
                                                        lbruleid: lb.id
                                                    },
                                                    async: false,
                                                    success: function(data) {
                                                        $(data.listlinkableloadbalancerresponse.linkableloadbalancerresponse).each(function(){
                                                            result.push({
                                                                         id:   this.uuid, 
                                                                         name: this.name, 
                                                                         description: this.name
                                                                     });
                                                        });
                                                    }
                                                });

                                                args.response.success({
                                                    data: result
                                                });
                                            }
                                        }
                                    }
                                },
                                after: function(args2) {
                                    var lastJobId;

                                    $.ajax({
                                        url: createURL("linkGloboLoadBalancer"),
                                        async: false,
                                        data: {
                                            sourcelbid: lb.id,
                                            targetlbid: args2.data.linkablelb
                                        },
                                        success: function(data, jobId) {
                                            console.log(data)
                                            
                                            lastJobId = data.linkgloboloadbalancerresponse.jobid;
                                        },
                                        error: function(message) {
                                            lastJobId = -1;
                                            args.response.error(message);
                                        }

                                    })
                                    args.response.success({
                                        _custom: {
                                            getLastJobId: function() { return lastJobId; },
                                            getUpdatedItem: function() {
                                                var loadbalancer = null;
                                                $.ajax({
                                                    url: createURL("listLoadBalancerRules"),
                                                    data: {
                                                        id: lb.id
                                                    },
                                                    dataType: "json",
                                                    async: false,
                                                    success: function(data) {
                                                        var loadBalancerData = data.listloadbalancerrulesresponse.loadbalancerrule;
                                                        $(loadBalancerData).each(function() {
                                                            var that = this;
                                                            this.ports = this.publicport + ':' + this.privateport + ', ';
                                                            $(this.additionalportmap).each(function() {
                                                                that.ports += this + ', ';
                                                            });
                                                            this.ports = this.ports.substring(0, this.ports.length - 2); // remove last ', '
                                                        });
                                                        loadbalancer = loadBalancerData[0];

                                                        if (typeof(loadbalancer.linkedloadbalancer) != 'undefined') {
                                                            loadbalancer.linkedlb = loadbalancer.linkedloadbalancer.name;
                                                        }
                                                    }
                                                });
                                                return loadbalancer;
                                            }
                                        }
                                    });


                                    
                                }
                            });
                        },
                        messages: {
                            notification: function() {
                                return 'Link Load Balancer';
                            }
                        },
                        notification: {
                            poll: function(args) {
                                var lastJobId = args._custom.getLastJobId();
                                if (lastJobId === undefined) {
                                    return;
                                } else if (lastJobId === null) {
                                    args.complete({
                                        data: args._custom.getUpdatedItem()
                                    });
                                    return;
                                }
                                args._custom.jobId = lastJobId;
                                return pollAsyncJobResult(args);
                            }
                        }
                    },
                    unlinkloadbalancer: {
                        label: 'Unlink load balancer, creating new pools',
                        custom: {
                            buttonLabel: 'label.configure'
                        },
                        preFilter: function(args) {
                            var linked = args.context.loadbalancers[0].linkedloadbalancer
                            if ( typeof(linked) == 'undefined') {
                                return false;
                            }
                            return true;
                        },
                        action: function(args) {
                            var lastJobId;
                            var lb = args.context.loadbalancers[0];
                            $.ajax({
                                url: createURL("unlinkGloboLoadBalancer"),
                                async: false,
                                data: {
                                    lbruleid: lb.id,
                                },
                                success: function(data, jobId) {
                                    lastJobId = data.unlinkgloboloadbalancerresponse.jobid;
                                },
                                error: function(message) {
                                    lastJobId = -1;
                                    args.response.error(message);
                                }

                            })
                            args.response.success({
                                _custom: {
                                    getLastJobId: function() { return lastJobId; },
                                    getUpdatedItem: function() {
                                        var loadbalancer = null;
                                        $.ajax({
                                            url: createURL("listLoadBalancerRules"),
                                            data: {
                                                id: lb.id
                                            },
                                            dataType: "json",
                                            async: false,
                                            success: function(data) {
                                                var loadBalancerData = data.listloadbalancerrulesresponse.loadbalancerrule;
                                                $(loadBalancerData).each(function() {
                                                    var that = this;
                                                    this.ports = this.publicport + ':' + this.privateport + ', ';
                                                    $(this.additionalportmap).each(function() {
                                                        that.ports += this + ', ';
                                                    });
                                                    this.ports = this.ports.substring(0, this.ports.length - 2); // remove last ', '
                                                });
                                                loadbalancer = loadBalancerData[0];
                                                if (typeof(loadbalancer.linkedloadbalancer) != 'undefined') {
                                                    loadbalancer.linkedlb = loadbalancer.linkedloadbalancer.name;
                                                }
                                                
                                            }
                                        });
                                        return loadbalancer;
                                    }
                                }
                            });

                        },
                        messages: {
                            confirm: function(args) {
                                return 'message.action.unlink.loadbalancer';
                            },
                            notification: function(args) {
                                return 'label.action.unlink.loadbalancer';
                            }
                        },
                        notification: {
                            poll: function(args) {
                                var lastJobId = args._custom.getLastJobId();
                                if (lastJobId === undefined) {
                                    return;
                                } else if (lastJobId === null) {
                                    args.complete({
                                        data: args._custom.getUpdatedItem()
                                    });
                                    return;
                                }
                                args._custom.jobId = lastJobId;
                                return pollAsyncJobResult(args);
                            }
                        }
                    }
                }
            },
            actions: {
                remove: {
                    label: 'label.delete',
                    messages: {
                        confirm: function(args) {
                            return 'Are you sure you want to remove load balancer ' + args.context.loadbalancers[0].name + '?';
                        },
                        notification: function(args) {
                            return 'Removing Ip Address';
                        }
                    },
                    action: function(args) {
                        var ipToBeReleased = args.context.loadbalancers[0].publicipid;

                        var show_error_message = function(json) {
                            args.response.error(parseXMLHttpResponse(json));
                        };

                        $.ajax({
                            url: createURL("deleteGloboLoadBalancer"),
                            data: {
                                id: args.context.loadbalancers[0].id
                            },
                            dataType: "json",
                            success: function(data) {
                                cloudStack.ui.notifications.add({
                                        desc: 'label.action.delete.load.balancer',
                                        section: 'Network',
                                        poll: pollAsyncJobResult,
                                        _custom: {
                                            jobId: data.deletegloboloadbalancerresponse.jobid,
                                            fullRefreshAfterComplete: true
                                        }
                                    },
                                    function() {
                                    }, {},
                                    show_error_message, {} // job deleteLoadBalancerRule
                                );
                            },
                            error: show_error_message // ajax deleteLoadBalancerRule
                        });
                    },
                    notification: {
                        poll: pollAsyncJobResult
                    },
                },
                add: {} // see addlb.js
            }
        },
        utils: {healthcheck: healthcheckTypes}
    };
})(cloudStack, jQuery);
