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

	function getPool(poolid, lbid , zoneid) {
		var data = {
            lbruleid: lbid,
            zoneid: zoneid
        };
		var pools = [];
	    $.ajax({
	        url: createURL("listGloboNetworkPools"),
	        data: data,
	        dataType: "json",
	        async: false,
	        success: function(data) {
	            pools = data.listglobonetworkpoolresponse.globonetworkpool;
	        },
	        error: function(errorMessage) {
	            args.response.error(errorMessage);
	        }
	    });
	    var pool;
	    $(pools).each(function() {
	    	if (this.id == poolid) {
	    		pool = this;
	    	}
	    });
	    return pool;
    }

    cloudStack.sections.loadbalancer.listView.detailView.tabs['pools'] = {
        title: 'Pools',
        listView: {
            id: 'pools',
            hideSearchBar: true,
            fields: {
                ports: { label: 'label.port' },
                healthchecktype: { label: 'Healthcheck Type' },
                l4protocol:  { label: 'L4 Protocol' },
                l7protocol:  { label: 'L7 Protocol' }
            },
            dataProvider: function(args) {
                var data = {
                    lbruleid: args.context.loadbalancers[0].id,
                    zoneid: args.context.loadbalancers[0].zoneid
                };
                listViewDataProvider(args, data);

                $.ajax({
                    url: createURL("listGloboNetworkPools"),
                    data: data,
                    dataType: "json",
                    async: false,
                    success: function(data) {
                        var lbPools = data.listglobonetworkpoolresponse.globonetworkpool;
                        $(lbPools).each(function() {
                            this.ports = this.vipport + ':' + this.port;
                        });
                        args.response.success({ data: lbPools });
                    },
                    error: function(errorMessage) {
                        args.response.error(errorMessage);
                    }
                });
            },
            detailView: {
                name: 'Pool Details',
                isMaximized: true,
                noCompact: true,
                tabs: {
                    details: {
                        title: 'label.details',
                        fields: [{
                            name: {
                                label: 'label.name'
                            },
                            port: {
                                label: 'label.port'
                            },
                            lbmethod: {
                                label: 'label.algorithm'
                            },
                            maxconn: {
                                label: 'Max Connections'
                            },
                            healthchecktype: {
                                label: 'Healthcheck Type'
                            },
                            healthcheck: {
                                label: 'Healthcheck'
                            },
                            healthcheckexpect: {
                                label: 'Expected Healthcheck'
                            },
                            l4protocol: {
                                label: 'L4 Protocol'
                            },
                            l7protocol: {
                                label: 'L7 Protocol'
                            }
                        }],
                        dataProvider: function(args) {
							var pool = getPool(args.context.pools[0].id, args.context.loadbalancers[0].id, args.context.loadbalancers[0].zoneid);
							args.context.poolselected = pool;

							args.response.success({
                                data: pool
                            });
                        }
                    }
                },
                actions: {
                    editPool: {
                        label: 'Edit Pool',
                        custom: {
                            buttonLabel: 'label.configure'
                        },
                        action: function(args) {
                            var pool = args.context.pools[0];
                            var lb = args.context.loadbalancers[0];
                            cloudStack.dialog.createForm({
                                form: {
                                    title: 'Edit Pool',
                                    fields: {
                                        maxconn: {
                                            label: 'Max Connections',
                                            defaultValue: pool.maxconn.toString(),
                                            validation: {
                                                required: true,
                                                positiveNumber: true
                                            }
                                        },
                                        healthchecktype: {
                                            label: 'Healthcheck Type',
                                            docID: 'helpHealthcheckType',
                                            defaultValue: pool.healthchecktype,
                                            dependsOn: ['isLbAdvanced'],
                                            select: function(args) {
                                                args.response.success({
                                                    data: healthcheckTypes.values
                                                });
                                                args.$select.change(function() {
                                                    var type = $(this).val()

                                                    if ( healthcheckTypes.isLayer4(type)) {
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
                                            label: 'Healthcheck',
                                            defaultValue: pool.healthcheck
                                        },
                                        expectedhealthcheck: {
                                            label: 'Expected Healthchecks',
                                            defaultValue: pool.healthcheckexpect,
                                            select: function(args) {
                                                var expectedHealthcheck = [];
                                                $.ajax({
                                                    url: createURL("listGloboNetworkExpectedHealthchecks"),
                                                    data: {},
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
                                        },
                                        isPoolAdvanced: {
			                                label: 'label.show.advanced.settings',
			                                isBoolean: true,
			                                defaultValue: false,
			                                isChecked: false,
			                            },
			                            redeploy: {
			                            	label: 'Redeploy VIP',
			                                isBoolean: true,
			                                defaultValue: false,
			                                isChecked: false,
			                                dependsOn: ['isPoolAdvanced'],
			                                isHidden: function (args) {
			                                   var isAdvancedChecked = $('input[name=isPoolAdvanced]:checked').length > 0;
			                                   return !isAdvancedChecked;
			                                }
			                            },
			                            l4protocol: {
			                                id: 'l4protocol',
			                                label: 'L4 Protocol',
			                                defaultValue: pool.l4protocol,
			                                validation: {
			                                    required: false
			                                },
			                                dependsOn: ['isPoolAdvanced'],
			                                isHidden: function (args) {
			                                   var isAdvancedChecked = $('input[name=isPoolAdvanced]:checked').length > 0;
			                                   return !isAdvancedChecked;
			                                },
			                                select: function(args) {
			                                   args.response.success({
			                                       data: [{id: 'TCP', description: 'TCP'},
			                                                 {id: 'UDP', description: 'UDP'}]
			                                   });
			                                }
			                            },
			                            l7protocol: {
			                                id: 'l7protocol',
			                                label: 'L7 Protocol',
			                                defaultValue: pool.l7protocol,
			                                validation: {
			                                    required: false
			                                },
			                                dependsOn: ['l4protocol', 'isPoolAdvanced'],
			                                isHidden: function (args) {
			                                   var isAdvancedChecked = $('input[name=isPoolAdvanced]:checked').length > 0;
			                                   return !isAdvancedChecked;
			                                },
			                                select: function(args) {
			                                    var data = [];
			                                    var l4protocol = $('select[name=l4protocol]').val();
			                                    
			                                    if (typeof(l4protocol) == 'undefined') {
													l4protocol = pool.l4protocol
												}

			                                    if ( l4protocol == 'TCP' ) {
			                                        data.push({id: 'HTTP', description: 'HTTP'});
			                                        data.push({id: 'HTTPS', description: 'HTTPS'});
			                                    }
			                                    data.push({id: 'OTHERS', description: 'Outros'});


			                                   args.response.success({
			                                       data: data
			                                   });
			                                }
			                            }
                                    }
                                },
                                after: function(args2) {
                                	var hasL4Change = args2.data.l4protocol != pool.l4protocol;
                                	var hasL7Change = args2.data.l7protocol != pool.l7protocol;

                                	if (args2.data.redeploy != 'on' && (hasL7Change || hasL4Change) ) {
                                		args.response.error("Only can change l4protocol/l7protocol when 'Redeploy VIP' is checked");
                                        return;
                                	}

                                    if (args2.data.healthcheck === '' && (healthcheckTypes.isLayer7(args2.data.healthchecktype))) {
                                        args.response.error(msg_validation_healthcheck_http);
                                        return;
                                    } 


                                    if (healthcheckTypes.isLayer4(args2.data.healthchecktype)) { // Empty healthcheck means TCP
                                        args2.data.expectedhealthcheck = ''; // expecthealthcheck is for HTTP/HTTPS only
                                        args2.data.healthcheck = '';
                                    }

                                    var data = {
                                        poolids: pool.id.toString(),
                                        lbruleid: lb.id,
                                        zoneid: lb.zoneid,
                                        healthchecktype: args2.data.healthchecktype,
                                        healthcheck: args2.data.healthcheck,
                                        expectedhealthcheck: args2.data.expectedhealthcheck,
                                        maxconn: args2.data.maxconn 
                                    };

                                    if (hasL4Change || hasL7Change) {
                                    	data['l4protocol'] = args2.data.l4protocol;
                                    	data['l7protocol'] = args2.data.l7protocol;
                                    	data['redeploy'] = args2.data.redeploy == 'on';
                                    }

                                    $.ajax({
                                        url: createURL('updateGloboNetworkPool'),
                                        dataType: 'json',
                                        async: true,
                                        data: data,
                                        success: function(json) {
                                            var jid = json.updateglobonetworkpoolresponse.jobid;
                                            args.response.success({
                                                _custom: {
                                                    jobId: jid,
                                                    getUpdatedItem: function(json) {
                                                        return;
                                                    }
                                                }
                                            });
                                        },
                                        error: function(errorMessage) {
                                            args.response.error(errorMessage);
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
                                return 'Update Pool';
                            }
                        },
                        notification: {
                            poll: pollAsyncJobResult
                        }
                    }
                }
            },
            actions: {
                add: {
                    label: 'Add pool',
                    preAction: function(args) {
                        var data = {
                            lbruleid: args.context.loadbalancers[0].id,
                            zoneid: args.context.loadbalancers[0].zoneid
                        };

                        var pools = [];
                        $.ajax({
                            url: createURL("listGloboNetworkPools"),
                            data: data,
                            dataType: "json",
                            async: false,
                            success: function(data) {
                                pools = data.listglobonetworkpoolresponse.globonetworkpool;
                            },
                            error: function(errorMessage) {
                                args.response.error(errorMessage);
                            }
                        });
                        return true;
                    },
                    createForm: {
                        fields: {
                            publicPort: {
                                label: 'Public port',
                                validation: {
                                    required: true,
                                    positiveNumber: true
                                }
                            },
                            privatePort: {
                                label: 'Private port',
                                validation: {
                                    required: true,
                                    positiveNumber: true
                                }
                            },
                            isPoolAdvanced: {
                               label: 'label.show.advanced.settings',
                               isBoolean: true,
                               defaultValue: false,
                               isChecked: false,
                           },
                            l4protocol: {
                                id: 'l4protocol',
                                label: 'L4 Protocol',
                                validation: {
                                    required: false
                                },
                                dependsOn: ['isPoolAdvanced'],
                                isHidden: function (args) {
                                   var isAdvancedChecked = $('input[name=isPoolAdvanced]:checked').length > 0;
                                   return !isAdvancedChecked;
                                },
                                select: function(args) {
                                   args.response.success({
                                       data: [{id: 'TCP', description: 'TCP'},
                                                 {id: 'UDP', description: 'UDP'}]
                                   });
                                }
                            },
                            l7protocol: {
                                id: 'l7protocol',
                                label: 'L7 Protocol',
                                validation: {
                                    required: false
                                },
                                dependsOn: ['isPoolAdvanced', 'l4protocol'],
                                isHidden: function (args) {
                                   var isAdvancedChecked = $('input[name=isPoolAdvanced]:checked').length > 0;
                                   return !isAdvancedChecked;
                                },
                                select: function(args) {
                                    var data = [];
                                    var l4protocol = $('select[name=l4protocol]').val();

                                    if ( l4protocol == 'TCP' || typeof(l4protocol) == 'undefined') {
                                        data.push({id: 'HTTP', description: 'HTTP'});
                                        data.push({id: 'HTTPS', description: 'HTTPS'});
                                    }
                                    data.push({id: 'OTHERS', description: 'Outros'});


                                   args.response.success({
                                       data: data
                                   });
                                }
                            },

                        },
                    },
                    action: function(args) {
                        var msg = "Are you sure you want to add this pool?<br/><br/>";
                        msg += "Public port: <span style='font-weight: bold'>" + args.data.publicPort
                        msg += "</span><br/>";
                        msg += "Private port: <span style='font-weight: bold'>" + args.data.privatePort
                        msg += "</span><br/>";

                        var isAdvancedChecked = $('input[name=isPoolAdvanced]:checked').length > 0;

                        if (isAdvancedChecked) {
                        	msg += "L4 Protocol: <span style='font-weight: bold'>" + args.data.l4protocol
                        	msg += "</span><br/>";
                        	msg += "L7 Protocol: <span style='font-weight: bold'>" + args.data.l7protocol
                        	msg += "</span><br/>";
                        }

                        cloudStack.dialog.confirm({
                            message: msg,
                            action: function() { // "Yes"
                                var poolsList = [];
                                $.ajax({
                                    url: createURL("listGloboNetworkPools"),
                                    data: {
                                        lbruleid: args.context.loadbalancers[0].id,
                                        zoneid: args.context.loadbalancers[0].zoneid
                                    },
                                    dataType: "json",
                                    async: false,
                                    success: function(data) {
                                        poolsList = data.listglobonetworkpoolresponse.globonetworkpool;
                                    },
                                    error: function(errorMessage) {
                                        args.response.error(errorMessage);
                                    }
                                });

                                $.ajax({
                                    url: createURL('createGloboNetworkPool'),
                                    dataType: 'json',
                                    async: true,
                                    data: {
                                        lbruleid: args.context.loadbalancers[0].id,
                                        zoneid: args.context.loadbalancers[0].zoneid,
                                        publicPort: args.data.publicPort,
                                        privatePort: args.data.privatePort,
                                        l4protocol: args.data.l4protocol,
                                        l7protocol: args.data.l7protocol,
                                    },
                                    success: function(json) {
                                        var jid = json.createglobonetworkpoolresponse.jobid;
                                        args.response.success({
                                            _custom: {
                                                jobId: jid,
                                                getUpdatedItem: function(json) {
                                                    $(window).trigger('cloudStack.fullRefresh');
                                                }
                                            }
                                        });
                                    },
                                    error: function(errorMessage) {
                                        args.response.error(errorMessage);
                                    }
                                });
                            },
                            cancelAction: function() { // "Cancel"
                                $(window).trigger('cloudStack.fullRefresh');
                            }
                        });
                        return;
                    },
                    messages: {
                        notification: function() {
                            return 'Add new pool';
                        }
                    },
                    notification: {
                        label: 'Add new pool',
                        poll: pollAsyncJobResult
                    },
                },
                editAll: {
                    label: 'Edit all pools',
                    isHeader: true,
                    preAction: function(args) {
                        var data = {
                            lbruleid: args.context.loadbalancers[0].id,
                            zoneid: args.context.loadbalancers[0].zoneid
                        };

                        var pools = [];
                        $.ajax({
                            url: createURL("listGloboNetworkPools"),
                            data: data,
                            dataType: "json",
                            async: false,
                            success: function(data) {
                                pools = data.listglobonetworkpoolresponse.globonetworkpool;
                            },
                            error: function(errorMessage) {
                                args.response.error(errorMessage);
                            }
                        });

                        if (pools.length === 0) {
                            // No pools
                            cloudStack.dialog.notice({
                                message: 'There are no pools. Please add a VM to this Load Balancer first.'
                            });
                            return false;
                        }
                        return true;
                    },
                    createForm: {
                        fields: {
                            maxconn: {
                                label: 'Max Connections',
                                defaultValue: "0",
                                validation: {
                                    required: true,
                                    positiveNumber: true
                                }
                            },
                            healthchecktype: {
                                label: 'Healthcheck Type',
                                docID: 'helpHealthcheckType',
                                dependsOn: ['isLbAdvanced'],
                                select: function(args) {
                                    args.response.success({
                                        data: healthcheckTypes.values
                                    });
                                    args.$select.change(function() {
                                        var type = $(this).val()
                                        if ( healthcheckTypes.isLayer4(type)) {
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
                                label: 'Healthcheck',
                                docID: 'helpHealthcheck'
                            },
                            expectedhealthcheck: {
                                label: 'Expected Healthchecks',
                                select: function(args) {
                                    var expectedHealthcheck = [];
                                    $.ajax({
                                        url: createURL("listGloboNetworkExpectedHealthchecks"),
                                        data: {},
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
                        if (args.data.healthcheck === '' && healthcheckTypes.isLayer7(args.data.healthchecktype)) {
                            args.response.error(msg_validation_healthcheck_http);
                            return;
                        } 
                        if (healthcheckTypes.isLayer4(args.data.healthchecktype)) { // Empty healthcheck means TCP
                            args.data.expectedhealthcheck = ''; // expecthealthcheck is for HTTP only
                            args.data.healthcheck = '';
                        } 

                        var msg = "Are you sure you want to apply these configurations to ALL pools?<br/><br/>";
                        msg += "Healthcheck: <span style='font-weight: bold'>" + args.data.healthchecktype + "  " + args.data.healthcheck
                        if ( args.data.expectedhealthcheck != '') {
                            msg += " - " + args.data.expectedhealthcheck 
                        }
                        msg += "</span><br/>";
                        msg += "Maxconn: <span style='font-weight: bold'>" + args.data.maxconn + "</span>";
                        cloudStack.dialog.confirm({
                            message: msg,
                            action: function() { // "Yes"
                                var poolsList = [];
                                $.ajax({
                                    url: createURL("listGloboNetworkPools"),
                                    data: {
                                        lbruleid: args.context.loadbalancers[0].id,
                                        zoneid: args.context.loadbalancers[0].zoneid
                                    },
                                    dataType: "json",
                                    async: false,
                                    success: function(data) {
                                        poolsList = data.listglobonetworkpoolresponse.globonetworkpool;
                                    },
                                    error: function(errorMessage) {
                                        args.response.error(errorMessage);
                                    }
                                });

                                var lb = args.context.loadbalancers[0];
                                
                                var poolIds = [];
                                $(poolsList).each(function() {
                                    poolIds.push(this.id);
                                });
                                $.ajax({
                                    url: createURL('updateGloboNetworkPool'),
                                    dataType: 'json',
                                    async: true,
                                    data: {
                                        poolids: poolIds.join(','),
                                        lbruleid: lb.id,
                                        zoneid: lb.zoneid,
                                        healthchecktype: args.data.healthchecktype,
                                        healthcheck: args.data.healthcheck,
                                        expectedhealthcheck: args.data.expectedhealthcheck,
                                        maxconn: args.data.maxconn,
                                    },
                                    success: function(json) {
                                        var jid = json.updateglobonetworkpoolresponse.jobid;
                                        args.response.success({
                                            _custom: {
                                                jobId: jid,
                                                getUpdatedItem: function(json) {
                                                    $(window).trigger('cloudStack.fullRefresh');
                                                }
                                            }
                                        });
                                    },
                                    error: function(errorMessage) {
                                        args.response.error(errorMessage);
                                    }
                                });
                            },
                            cancelAction: function() { // "Cancel"
                                $(window).trigger('cloudStack.fullRefresh');
                            }
                        });
                        return;
                    },
                    messages: {
                        notification: function() {
                            return 'Edit All Pools';
                        }
                    },
                    notification: {
                        poll: pollAsyncJobResult
                    },
                },
                remove: {
                    label : 'label.delete',
                    messages: {
                        confirm: function(args) {
                            return 'Are you sure you want to remove this pool:' + args.name + '?';
                        },
                        notification: function(args) {
                            return 'Removing pool';
                        }
                    },
                    action: function(args) {
                        var show_error_message = function(json) {
                          args.response.error(parseXMLHttpResponse(json));
                        };
                        var lb = args.context.loadbalancers[0];
                        var pool = args.data.jsonObj

                        if(lb.publicport == pool.vipport && lb.privateport == pool.port){
                            args.response.error("Default load balancer pool cannot be removed");
                            return;
                        }

                        $.ajax({
                            url: createURL("deleteGloboNetworkPool"),
                            data: {
                                id: pool.id,
                                lbruleid: lb.id,
                                zoneid: lb.zoneid
                            },
                            dataType: "json",
                            success: function(data) {
                                cloudStack.ui.notifications.add({
                                        desc: 'Removing pool',
                                        section: 'Load balancer',
                                        poll: pollAsyncJobResult,
                                        _custom: {
                                            jobId: data.deleteglobonetworkpoolresponse.jobid,
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
                }
            }
        }
    }
}(cloudStack, jQuery));