#!/usr/bin/python
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
# 
#   http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

import pprint
import XenAPI
import urllib
from xml.dom import minidom
import time
import commands

# Per VM dictionary (used by RRDUpdates to look up column numbers by variable names)
class VMReport(dict):
    """Used internally by RRDUpdates"""
    def __init__(self, uuid):
        self.uuid = uuid
        super(dict, self).__init__()


# Per Host dictionary (used by RRDUpdates to look up column numbers by variable names)
class HostReport(dict):
    """Used internally by RRDUpdates"""
    def __init__(self, uuid):
        self.uuid = uuid
        super(dict, self).__init__()


class PerfMonException(Exception):
    pass


class XmlConfigException(PerfMonException):
    pass


class UsageException(Exception):
    pass


class RRDUpdates:
    """ Object used to get and parse the output the http://localhost/rrd_udpates?...
    """
    def __init__(self):
        # params are what get passed to the CGI executable in the URL
        self.params = dict()
        self.params['start'] = int(time.time()) - 1000  # For demo purposes!
        self.params['host'] = 'false'   # include data for host (as well as for VMs)
        self.params['cf'] = 'AVERAGE'  # consolidation function, each sample averages 12 from the 5 second RRD
        self.params['interval'] = '60'

    def get_nrows(self):
        return self.rows

    def get_total_cpu_core(self, uuid):
	report = self.vm_reports[uuid]
        if not report:
            return 0
        else:
            param_keys = report.keys()
            result = 0
            for param in param_keys:
                if "cpu" in param:
                    result += 1
            return result

    def get_vm_data(self, uuid, param, row):
        #pp = pprint.PrettyPrinter(indent=4) 
        #pp.pprint(self.vm_reports)
        for hostIndex in xrange(0, self.hostCount):
            if uuid not in self.vm_reports[hostIndex]:
                continue
            report = self.vm_reports[hostIndex][uuid]
            col = report[param]
            return self.__lookup_data(col, row, hostIndex)

    # extract float from value (<v>) node by col,row
    def __lookup_data(self, col, row, hostIndex):
        # Note: the <rows> nodes are in reverse chronological order, and comprise
        # a timestamp <t> node, followed by self.columns data <v> nodes
        node = self.data_node[hostIndex].childNodes[self.rows - 1 - row].childNodes[col + 1]
        return float(node.firstChild.toxml())  # node.firstChild should have nodeType TEXT_NODE

    def refresh(self, login, starttime, session, override_params):
        self.params['start'] = starttime
        params = override_params
        params['session_id'] = session
        params.update(self.params)
        paramstr = "&".join(["%s=%s" % (k, params[k]) for k in params])
        # this is better than urllib.urlopen() as it raises an Exception on http 401 'Unauthorised' error
        # rather than drop into interactive mode
        self.hostCount = 0
        for host in login.host.get_all():
            #print "http://" + str(login.host.get_address(host)) + "/rrd_updates?%s" % paramstr
            sock = urllib.URLopener().open("http://" + str(login.host.get_address(host)) + "/rrd_updates?%s" % paramstr)
            xmlsource = sock.read()
            sock.close()
            xmldoc = minidom.parseString(xmlsource)
            self.__parse_xmldoc(xmldoc, self.hostCount)
            # Update the time used on the next run
            self.params['start'] = self.end_time + 1  # avoid retrieving same data twice
            self.hostCount += 1

    def __parse_xmldoc(self, xmldoc, hostIndex):
        # The 1st node contains meta data (description of the data)
        # The 2nd node contains the data
        if not hasattr(self,'meta_node'):
            self.meta_node = {}
        self.meta_node[hostIndex] = xmldoc.firstChild.childNodes[0]
        if not hasattr(self,'data_node'):
            self.data_node = {}
        self.data_node[hostIndex] = xmldoc.firstChild.childNodes[1]

        def lookup_metadata_bytag(name, hostIndex):
            return int(self.meta_node[hostIndex].getElementsByTagName(name)[0].firstChild.toxml())
            # rows = number of samples per variable
        # columns = number of variables
        self.rows = lookup_metadata_bytag('rows', hostIndex)
        self.columns = lookup_metadata_bytag('columns', hostIndex)
        # These indicate the period covered by the data
        self.start_time = lookup_metadata_bytag('start', hostIndex)
        self.step_time = lookup_metadata_bytag('step', hostIndex)
        self.end_time = lookup_metadata_bytag('end', hostIndex)
        # the <legend> Node describes the variables
        self.legend = self.meta_node[hostIndex].getElementsByTagName('legend')[0]
        # vm_reports matches uuid to per VM report
        if not hasattr(self,'vm_reports'):
            self.vm_reports = {}
        self.vm_reports[hostIndex] = {}
        # There is just one host_report and its uuid should not change!
        self.host_report = None
        # Handle each column.  (I.e. each variable)
        for col in range(self.columns):
            self.__handle_col(col, hostIndex)

    def __handle_col(self, col, hostIndex):
        # work out how to interpret col from the legend
        col_meta_data = self.legend.childNodes[col].firstChild.toxml()
        # vm_or_host will be 'vm' or 'host'.  Note that the Control domain counts as a VM!
        (cf, vm_or_host, uuid, param) = col_meta_data.split(':')
        if vm_or_host == 'vm':
            # Create a report for this VM if it doesn't exist
            if not uuid in self.vm_reports[hostIndex]:
                self.vm_reports[hostIndex][uuid] = VMReport(uuid)
                # Update the VMReport with the col data and meta data
            vm_report = self.vm_reports[hostIndex][uuid]
            vm_report[param] = col
        elif vm_or_host == 'host':
            # Create a report for the host if it doesn't exist
            if not self.host_report:
                self.host_report = HostReport(uuid)
            elif self.host_report.uuid != uuid:
                raise PerfMonException("Host UUID changed: (was %s, is %s)" % (self.host_report.uuid, uuid))
                # Update the HostReport with the col data and meta data
            self.host_report[param] = col
        else:
            raise PerfMonException("Invalid string in <legend>: %s" % col_meta_data)

def getuuid(vm_name):
    status, output = commands.getstatusoutput("xe vm-list | grep "+vm_name+" -B 1 | head -n 1 | awk -F':' '{print $2}' | tr -d ' '")
    if (status != 0):
        raise PerfMonException("Invalid vm name: %s" % vm_name)
    return output

def get_vm_cpu_number(vm_uuid):
    status, output = commands.getstatusoutput("xe vm-list params=VCPUs-number uuid=" + vm_uuid + " | head -n 1 | awk -F':' '{print $2}' | tr -d ' '")
    if (status != 0):
        raise PerfMonException("Invalid vm uuid: %s" % vm_uuid)
    return int(output)

def get_vm_group_perfmon(args={}):
    login = XenAPI.xapi_local()
    login.login_with_password("","")
    result = ""

    total_vm = int(args['total_vm'])
    total_counter = int(args['total_counter'])
    now = int(time.time()) / 60

    # Get pool's info of this host
    #pool = login.xenapi.pool.get_all()[0]
    # Get master node's address of pool
    #master = login.xenapi.pool.get_master(pool)
    #master_address = login.xenapi.host.get_address(master)
    session = login._session

    max_duration = 0
    for counter_count in xrange(1, total_counter + 1):
        duration = int(args['duration' + str(counter_count)])
        if duration > max_duration:
            max_duration = duration

    rrd_updates = RRDUpdates()
    rrd_updates.refresh(login.xenapi, now * 60 - max_duration, session, {})

    #for uuid in rrd_updates.get_vm_list():
    for vm_count in xrange(1, total_vm + 1):
        vm_name = args['vmname' + str(vm_count)]
        vm_uuid = getuuid(vm_name)
        #print "Got values for VM: " + str(vm_count) + " " + vm_uuid
        for counter_count in xrange(1, total_counter + 1):
            #refresh average
            average_cpu = 0
            average_memory = 0
            counter = args['counter' + str(counter_count)]
            total_row = rrd_updates.get_nrows()
            duration = int(args['duration' + str(counter_count)]) / 60
            duration_diff = total_row - duration
            if counter == "cpu":
                total_cpu = get_vm_cpu_number(vm_uuid)
                for row in xrange(duration_diff, total_row):
                    for cpu in xrange(0, total_cpu):
                        average_cpu += rrd_updates.get_vm_data(vm_uuid, "cpu" + str(cpu), row)
                average_cpu /= (duration * total_cpu)
                if result == "":
                    result += str(vm_count) + '.' +  str(counter_count) + ':' + str(average_cpu)
                else:
                    result += ',' + str(vm_count) +  '.' + str(counter_count) + ':' + str(average_cpu)
            elif counter == "memory":
                for row in xrange(duration_diff, total_row):
                    average_memory += rrd_updates.get_vm_data(vm_uuid, "memory_target", row) / 1048576 - rrd_updates.get_vm_data(vm_uuid, "memory_internal_free", row) / 1024
                average_memory /= duration
                if result == "":
                    result += str(vm_count) +  '.' +  str(counter_count) + ':' + str(average_memory)
                else:
                    result += ',' + str(vm_count) +  '.' +  str(counter_count) + ':' + str(average_memory)
    return result