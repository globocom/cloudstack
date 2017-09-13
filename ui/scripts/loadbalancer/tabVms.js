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

    cloudStack.sections.loadbalancer.listView.detailView.tabs['vms'] = {
        title: 'label.virtual.machines',
        listView: {
            id: 'vms',
            disableInfiniteScrolling: true,
            fields: {
                name: { label: 'label.name' },
                ip: { label: 'label.ip' },
                network: { label: 'label.network' },
            },
            dataProvider: function(args) {
                var data = {id: args.context.loadbalancers[0].id};
                listViewDataProvider(args, data);
                $.ajax({
                    url: createURL('listLoadBalancerRuleInstances'),
                    data: data,
                    success: function(data) {
                        lbinstances = [];
                        response = data.listloadbalancerruleinstancesresponse.loadbalancerruleinstance ?
                            data.listloadbalancerruleinstancesresponse.loadbalancerruleinstance : [];
                            $(response).each(function() {
                                var ipaddress;
                                var networkname;
                                $(this.nic).each(function() {
                                    // Find the NIC that is in the load balancer
                                    if (args.context.loadbalancers[0].networkid === this.networkid ||
                                        args.context.loadbalancers[0].additionalnetworkids.indexOf(this.networkid) !== -1) {
                                        ipaddress = this.ipaddress;
                                        networkname = this.networkname;
                                        return false; // break 'each' loop since we've found it
                                    }
                                });
                                lbinstances.push({id: this.id, name: this.name, ip: ipaddress, network: networkname });
                            });
                        args.response.success({
                            data: lbinstances
                        });
                    },
                    error: function(errorMessage) {
                        args.response.error(errorMessage);
                    }
                });
            },
            actions: {
                rm_custom: {
                    label: 'Remove VMs',
                    isHeader: true,
                    listView: {
                        multiSelect: true,
                        title: 'Remove VMs from Load Balancer',
                        hideSelectAction: true,
                        applyButton: 'Remove Vms',
                        fields: {
                            name: {
                                label: 'label.name',
                                truncate: true
                            },
                            displayname: {
                                label: 'label.display.name',
                                truncate: true
                            },
                            zonename: {
                                label: 'label.zone.name'
                            },
                            'instance-vm-lb': {
                                label: 'instance-vm-lb',
                                isHidden: true
                            }
                        },
                        dataProvider: function(args) {
                            var data = {};
                            listViewDataProvider(args, data);
                            data['id'] = args.context.loadbalancers[0].id;
                            data['pagesize'] = 499;
                            //data['applied'] = false;
                            $.ajax({
                                url: createURL("listLoadBalancerRuleInstances"),
                                data: data,
                                dataType: "json",
                                async: false,
                                success: function(json) {
                                    var lbinstances = [];
                                    var lb = args.context.loadbalancers[0];
                                    $(json.listloadbalancerruleinstancesresponse.loadbalancerruleinstance).each(function() {
                                        lbinstances.push({id: this.id, 'instance-vm-lb': this.id, name: this.name, displayname: this.displayname, zonename: this.zonename });
                                    });
                                    args.response.success({
                                        data: lbinstances
                                    });
                                }
                            });
                        }
                    },
                    action: function(args3) {
                        trs = $('.instance-vm-lb').parent().slice(1);
                        vmsIds = [];
                        $.each(trs, function(columnIndex, tr) {
                            checkedTrs = $(tr).find(":checked");
                            if (checkedTrs.length != 0){
                                vmsIds.push($(tr).find('.instance-vm-lb').text());
                            }
                        });
                        var jobId;
                        $.ajax({
                            url: createURL("removeFromLoadBalancerRule"),
                            data: {
                                id: args3.context.loadbalancers[0].id,
                                virtualmachineids: vmsIds.join(',')
                            },

                            dataType: "json",
                            async: true,
                            success: function(response) {
                                putOneLoadingRow();
                                checkStatus(args3, response.removefromloadbalancerruleresponse.jobid, 'Removing VM(s) from Load Balancer.');
                                //$(window).trigger('cloudStack.fullRefresh');
                            },
                            error: function(response) {
                                if ( response.status == 401){
                                    cloudStack.dialog.notice({message: 'label.session.expired'} );
                                }
                                $(window).trigger('cloudStack.fullRefresh');
                            }
                        });

                    }
                },
                add_custom: {
                    label: 'Add VMs',
                    isHeader: true,
                    listView: {
                        multiSelect: true,
                        title: 'Add VMs to Load Balancer',
                        hideSelectAction: true,
                        fields: {
                            name: {
                                label: 'label.name',
                                truncate: true
                            },
                            displayname: {
                                label: 'label.display.name',
                                truncate: true
                            },
                            zonename: {
                                label: 'label.zone.name'
                            },
                            'instance-vm-lb': {
                                label: 'instance-vm-lb',
                                isHidden: true
                            }
                        },
                        dataProvider: function(args) {
                            var data = {};
                            listViewDataProvider(args, data);
                            data['id'] = args.context.loadbalancers[0].id;
                            data['applied'] = false;
                            data['pagesize'] = 499;
                            $.ajax({
                                url: createURL("listLoadBalancerRuleInstances"),
                                data: data,
                                dataType: "json",
                                async: false,
                                success: function(json) {
                                    var lbinstances = [];
                                    var lb = args.context.loadbalancers[0];
                                    $(json.listloadbalancerruleinstancesresponse.loadbalancerruleinstance).each(function() {
                                        lbinstances.push({id: this.id, 'instance-vm-lb': this.id, name: this.name, displayname: this.displayname, zonename: this.zonename });
                                    });
                                    args.response.success({
                                        data: lbinstances
                                    });
                                }
                            });
                        }
                    },
                    action: function(args3) {
                        trs = $('.instance-vm-lb').parent().slice(1);
                        vmsIds = [];
                        $.each(trs, function(columnIndex, tr) {
                            checkedTrs = $(tr).find(":checked");
                            if (checkedTrs.length != 0){
                                vmsIds.push($(tr).find('.instance-vm-lb').text());
                            }
                        });
                        var jobId;
                        $.ajax({
                            url: createURL("assignToLoadBalancerRule"),
                            data: {
                                id: args3.context.loadbalancers[0].id,
                                virtualmachineids: vmsIds.join(',')
                            },

                            dataType: "json",
                            async: true,
                            success: function(response) {
                                addLoadingRow(vmsIds);
                                checkStatus(args3, response.assigntoloadbalancerruleresponse.jobid, 'Adding VM(s) to Load Balancer Rule');
                            },
                            error: function(response) {
                                if ( response.status == 401){
                                    cloudStack.dialog.notice({message: 'label.session.expired'} );
                                }
                                $(window).trigger('cloudStack.fullRefresh');
                            }
                        });

                    }
                }
            }
        }
    };

    var checkStatus = function(args, jobId, msg) {
        checkJobStatus({
            jobId: jobId,
            msgSuccess: msg,
            ok: function(args) {
                $(window).trigger('cloudStack.fullRefresh');
            }, 
            error: function(args) {
                $(window).trigger('cloudStack.fullRefresh');
                cloudStack.dialog.notice({message: args.message} );
            }
        });
    };

    var checkJobStatus = function(configs) {
        var jobStatus;
        var poolNotificaiton = {
             desc: configs.msgSuccess,
             section: 'loadbalancer',
             interval: 1000, 
             _custom:{jobId: configs.jobId}, 
             poll: function(args){
                var jobStatus = -1;
                var jobMsg = '';
                $.ajax({url: createURL('queryAsyncJobResult'),
                        data: {jobId: configs.jobId},
                        async: false,
                        success: function(response) {
                            jobStatus = response.queryasyncjobresultresponse.jobstatus;
                            if (jobStatus == 1) {
                                args.complete();
                                configs.ok(response);
                            } else if (jobStatus == 2 || jobStatus == 3){
                                jobMsg = response.queryasyncjobresultresponse.jobresult.errortext;
                                args.error(jobMsg);
                                configs.error({message: jobMsg, response: response});
                            }
                        },
                        error: function(response) {
                            args.error();
                            configs.error(response);
                        }
                });
            } 
        };
        cloudStack.ui.notifications.add(
           poolNotificaiton, 
           function(args) { },
           {},
           function(args){ },
           {});
        return jobStatus;
    };

    var addLoadingRow = function(vmsIds) {
        var table = $('#details-tab-vms').find('table[class=body]');

        var trs = table.find('tr');

        
        if (trs.length > 0) {
            var first = $(trs[0]);
            if ( first.attr('class') === 'empty last even') {
                first.remove()
            }
        }

        var tr = createLoadingTr();

        $.each(vmsIds, function(index, vmId) {
            $(table[0]).prepend(tr.clone());
        });
        
    };

    var putOneLoadingRow = function(){
        var table = $('#details-tab-vms').find('table[class=body]');
        $(table[0]).empty();
        var tr = createLoadingTr();
        $(table[0]).prepend(tr.clone());
    };

    function createLoadingTr() {
        var fields = cloudStack.sections.loadbalancer.listView.detailView.tabs.vms.listView.fields;

        var tr = $('<tr></tr>');
        tr.addClass('odd');

        tr.append(createTdLoading('name first'));
        tr.append(createTdLoading('ip reduce-hide'));
        tr.append(createTdLoading('network reduce-hide'));

        return tr;
    }

    function createTdLoading(name){
        var div = $('<div></div>').addClass('loading');
        var td = $('<td></td>');
        td.addClass(name)
        td.append(div);

        return td;
    }



}(cloudStack, jQuery));