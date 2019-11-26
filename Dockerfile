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
#
# CloudStack-simulator build

FROM oraclelinux:7.7

LABEL author="Evo Infra" email="iaas@corp.globo.com"

RUN rm -fr /etc/yum.repos.d
COPY tools/docker/evo/yum.repos.d/ /etc/yum.repos.d/

# COPY tools/docker/evo/mysql-connector-python-8.0.18-1.el7.x86_64.rpm /tmp/
# RUN rpm -i /tmp/mysql-connector-python-8.0.18-1.el7.x86_64.rpm

RUN yum clean all && yum -y update

RUN yum install -y \
    genisoimage \
    libffi-dev \
    libssl-dev \
    git \
    sudo \
    ipmitool \
    java-1.8.0-openjdk \
    java-1.7.0-openjdk \
    maven \
    python-dev \
    python-setuptools \
    python-pip \
    python-mysql.connector \
    supervisor \
    git\
    mlocate\
    which\
    rpm-build\
    glibc-devel\
    gcc\
    ws-commons\
    ws-commons-util\
    mysql-connector-python

WORKDIR /root/cloudstack

COPY tools/docker/evo/gitconfig /root/.gitconfig

ARG TAG

CMD ./packaging/package.sh --distribution centos7 --release $TAG