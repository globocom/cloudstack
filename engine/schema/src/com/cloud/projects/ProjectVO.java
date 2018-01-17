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
package com.cloud.projects;

import java.util.Date;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.cloudstack.api.Identity;
import org.apache.cloudstack.api.InternalIdentity;

import com.cloud.utils.NumbersUtil;
import com.cloud.utils.db.GenericDao;

@Entity
@Table(name = "projects")
public class ProjectVO implements Project, Identity, InternalIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "name")
    private String name;

    @Column(name = "display_text")
    String displayText;

    @Column(name = "domain_id")
    long domainId;

    @Column(name = "project_account_id")
    long projectAccountId;

    @Column(name = "business_service_id")
    private String businessServiceId;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "component_id")
    private String componentId;

    @Column(name = "sub_component_id")
    private String subComponentId;

    @Column(name = "product_id")
    private String productId;

    @Column(name = "detailed_usage")
    private boolean detailedUsage;

    @Column(name = "state")
    @Enumerated(value = EnumType.STRING)
    private State state;

    @Column(name = GenericDao.CREATED_COLUMN)
    private Date created;

    @Column(name = GenericDao.REMOVED_COLUMN)
    private Date removed;

    @Column(name = "uuid")
    private String uuid;

    protected ProjectVO() {
        this.uuid = UUID.randomUUID().toString();
    }

    public ProjectVO(String name, String displayText, long domainId, long projectAccountId) {
        this.name = name;
        this.displayText = displayText;
        this.projectAccountId = projectAccountId;
        this.domainId = domainId;
        this.state = State.Disabled;
        this.uuid = UUID.randomUUID().toString();
    }

    public ProjectVO(String name, String displayText, long domainId, long projectAccountId, String businessServiceId, String clientId, String componentId, String subComponentId, String productId, Boolean detailedUsage) {
        this(name, displayText, domainId, projectAccountId);
        this.businessServiceId = businessServiceId;
        this.clientId = clientId;
        this.componentId = componentId;
        this.subComponentId = subComponentId;
        this.productId = productId;
        this.detailedUsage = detailedUsage;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }

    @Override
    public long getDomainId() {
        return domainId;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public Date getCreated() {
        return created;
    }

    @Override
    public Date getRemoved() {
        return removed;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("Project[");
        buf.append(id).append("|name=").append(name).append("|domainid=").append(domainId).append("]");
        return buf.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ProjectVO)) {
            return false;
        }
        ProjectVO that = (ProjectVO)obj;
        if (this.id != that.id) {
            return false;
        }

        return true;
    }

    @Override
    public long getProjectAccountId() {
        return projectAccountId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getBusinessServiceId() {
        return businessServiceId;
    }

    public void setBusinessServiceId(String businessServiceId) {
        this.businessServiceId = businessServiceId;
    }

    public String getComponentId() {
        return componentId;
    }

    public void setComponentId(String componentId) {
        this.componentId = componentId;
    }

    public String getSubComponentId() {
        return subComponentId;
    }

    public void setSubComponentId(String subComponentId) {
        this.subComponentId = subComponentId;
    }

    public Boolean isDetailedUsage() {
        return detailedUsage;
    }

    public void setDetailedUsage(Boolean detailedUsage) {
        this.detailedUsage = detailedUsage;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void setState(State state) {
        this.state = state;
    }

    @Override
    public String getUuid() {
        return this.uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public int hashCode() {
        return NumbersUtil.hash(id);
    }
}
