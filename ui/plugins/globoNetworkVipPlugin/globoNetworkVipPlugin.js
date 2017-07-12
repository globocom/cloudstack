(function (cloudStack) {

    cloudStack.plugins.globoNetworkVipPlugin = function(plugin) {
        plugin.ui.addSection({
            id: 'globoNetworkVipPlugin',
            title: 'GloboNetwork VIPs',
            preFilter: function(args) {
                return true; // isAdmin();
            },
            listView: {
                id: 'vips',
                fields: {
                    name: { label: 'label.name' },
                    ip: { label: 'IP' },
                },
                dataProvider: function(args) {
                    plugin.ui.apiCall('listGloboNetworkVips', {
                        success: function(json) {
                            var vips = json.listglobonetworkvipsresponse.globonetworkvip || [];
                            vips.forEach(function(vip) {
                                vip.ports = vip.ports.join(", ");
                            });
                            args.response.success({ data: vips });
                        },
                        error: function(errorMessage) {
                            args.response.error(errorMessage);
                        }
                    });
                },
                detailView: {
                    name: 'VIP details',
                    isMaximized: true,
                    noCompact: true,
                    tabs: {
                        details: {
                            title: 'label.details',
                            fields: [{
                                id: {
                                    label: 'label.id'
                                },
                                name: {
                                    label: 'label.name'
                                },
                                ip: {
                                    label: 'IP'
                                },
                                cache: {
                                    label: 'Cache'
                                },
                                method: {
                                    label: 'Balancing method'
                                },
                                persistence: {
                                    label: 'Persistence'
                                },
                                healthchecktype: {
                                    label: 'Healthcheck Type'
                                },
                                healthcheck: {
                                    label: 'Healthcheck'
                                },
                                maxconn: {
                                    label: 'Max Connections'
                                },
                                ports: {
                                    label: 'Ports'
                                },
                            }],
                            dataProvider: function(args) {
                                args.response.success({ data: args.jsonObj });
                            }
                        },
                        reals: {
                            title: 'Reals',
                            listView: {
                                id: 'reals',
                                fields: {
                                    vmname: { label: 'VM' },
                                    ip: { label: 'IP' },
                                    network: { label: 'Network' },
                                    ports: { label: 'Ports' },
                                },
                                dataProvider: function(args) {
                                    plugin.ui.apiCall('listGloboNetworkReals', {
                                        data: {
                                            vipid: args.context.vips[0].id,
                                        },
                                        success: function(json) {
                                            var reals = json.listglobonetworkrealsresponse.globonetworkreal || [];
                                            args.response.success({ data: reals });
                                        },
                                        error: function(errorMessage) {
                                            args.response.error(errorMessage);
                                        }
                                    });
                                },
                                actions: {
                                    add: {
                                        label: 'Add Real',
                                        action: function(args) {
                                            return;;
                                        },
                                        messages: {
                                            confirm: function(args) {
                                                return 'This action is no longer supported. You should use the Load Balancer menu instead.';
                                            },
                                            notification: function(args) {
                                                return 'Real added successfully';
                                            }
                                        },
                                        notification: {
                                            poll: pollAsyncJobResult
                                        }
                                    },
                                    remove: {
                                        label: 'label.remove',
                                        action: function(args) {
                                           return;
                                        },
                                        messages: {
                                            confirm: function(args) {
                                                return 'This action is no longer supported. You should use the Load Balancer menu instead.';
                                            },
                                            notification: function(args) {
                                                return 'Real removed successfully';
                                            }
                                        },
                                        notification: {
                                            poll: pollAsyncJobResult
                                        }
                                    }
                                },
                            }
                        },
                    },
                },
                actions: {
                    remove: {
                        label: 'label.remove',
                        messages: {
                            confirm: function(args) {
                               return 'This action is no longer supported. You should use the Load Balancer menu instead.';
                            },
                            notification: function(args) {
                                return 'Remove GloboNetwork VIP';
                            }
                        },
                        action: function(args) {
                            return;
                        },
                        notification: {
                            poll: function(args) {
                                args.complete();
                            }
                        }
                    },
                    add: {
                        label: 'Create new VIP',
                        action: function(args) {
                            return;
                        },
                        messages: {
                            confirm: function(args) {
                                return 'This action is no longer supported. You should use the Load Balancer menu instead.';
                            },
                            notification: function(args) {
                                return 'Vip created';
                            }
                        },
                        notification: {
                            poll: pollAsyncJobResult
                        }
                    }
                }
            }
        });
    };
}(cloudStack));