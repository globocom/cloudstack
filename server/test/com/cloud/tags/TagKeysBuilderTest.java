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

package com.cloud.tags;

import com.cloud.server.ResourceTag;
import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;

public class TagKeysBuilderTest extends TestCase {


    public void testGetKeyMetadata() throws Exception {
        TaggedResourceManagerImpl manager = new TaggedResourceManagerImpl();

        String keyMetadata = TagKeysBuilder.getKeyMetadata("MYKEY");
        assertEquals("TAG_MYKEY" , keyMetadata);
    }

    public void testTagKeysBuilder() {
        TagKeysBuilder tagKeysBuilder = new TagKeysBuilder();

        tagKeysBuilder.add("MY_TAG");
        tagKeysBuilder.add("TAG_1");
        tagKeysBuilder.add("TAG_2");

        assertEquals("MY_TAG,TAG_1,TAG_2", tagKeysBuilder.value());


        tagKeysBuilder = new TagKeysBuilder();

        tagKeysBuilder.add(" MY_TAG ");
        tagKeysBuilder.add(" TAG_1");
        tagKeysBuilder.add("TAG_2");

        assertEquals("MY_TAG,TAG_1,TAG_2", tagKeysBuilder.value());
    }

    public void testBuildTagKeys() {

        List<ResourceTag> tags = new ArrayList<ResourceTag>();
        tags.add(new ResourceTagVO("MY_TAG", "321", 1l,2l, 3l, ResourceTag.ResourceObjectType.UserVm, "admin", "123123-123123"));
        tags.add(new ResourceTagVO("MY_TAG_1", "---", 1l,2l, 3l, ResourceTag.ResourceObjectType.UserVm, "admin", "123123-123123"));
        tags.add(new ResourceTagVO("MY_TAG_2", "444", 1l,2l, 3l, ResourceTag.ResourceObjectType.UserVm, "admin", "123123-123123"));

        String keys = TagKeysBuilder.buildTagKeys(tags);

        assertEquals("MY_TAG,MY_TAG_1,MY_TAG_2", keys);
    }

    public void testBuildTagKeys_whitespace() {

        List<ResourceTag> tags = new ArrayList<ResourceTag>();
        tags.add(new ResourceTagVO("MY_TAG ", "321", 1l,2l, 3l, ResourceTag.ResourceObjectType.UserVm, "admin", "123123-123123"));
        tags.add(new ResourceTagVO("MY_TAG_1 ", "---", 1l,2l, 3l, ResourceTag.ResourceObjectType.UserVm, "admin", "123123-123123"));
        tags.add(new ResourceTagVO(" MY_TAG_2 ", "444", 1l,2l, 3l, ResourceTag.ResourceObjectType.UserVm, "admin", "123123-123123"));

        String keys = TagKeysBuilder.buildTagKeys(tags);

        assertEquals("MY_TAG_,MY_TAG_1_,_MY_TAG_2_", keys);
    }

    public void testBuildTagKeys_empty_list() {
        List<ResourceTag> tags = new ArrayList<ResourceTag>();

        String keys = TagKeysBuilder.buildTagKeys(tags);
        assertEquals("", keys);


        keys = TagKeysBuilder.buildTagKeys(null);
        assertEquals("", keys);
    }
}