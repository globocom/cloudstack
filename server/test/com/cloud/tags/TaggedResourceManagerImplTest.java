package com.cloud.tags;

import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.router.NetworkHelper;
import com.cloud.server.ResourceTag;
import com.cloud.tags.dao.ResourceTagDao;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

public class TaggedResourceManagerImplTest extends TestCase {

    public void setUp() throws Exception {
        super.setUp();

    }

    public void tearDown() throws Exception {

    }

    public void testAddTagToRemoveMetadata() {
        TaggedResourceManagerImpl manager = new TaggedResourceManagerImpl();

        Map<Long, Map<String, String>> vmsIdResourceTagToRemove = new HashMap<Long, Map<String, String>>();

        //userVmId = 3
        manager.addTagToVMMetadata(vmsIdResourceTagToRemove, 3l, "MY_KEY", "123-123", ResourceTag.ResourceObjectType.UserVm);

        Map<String, String> tags = vmsIdResourceTagToRemove.get(3l);
        assertNotNull(tags);
        assertTrue( tags.containsKey("TAG_MY_KEY"));
        assertEquals("123-123", tags.get("TAG_MY_KEY"));

        //userVmId = 3
        manager.addTagToVMMetadata(vmsIdResourceTagToRemove, 3l, "MY_KEY_12", "444", ResourceTag.ResourceObjectType.UserVm);

        tags = vmsIdResourceTagToRemove.get(3l);
        assertNotNull(tags);
        assertEquals(2, tags.size());
        assertTrue( tags.containsKey("TAG_MY_KEY_12"));
        assertEquals("444", tags.get("TAG_MY_KEY_12"));

        //userVmId = 2
        manager.addTagToVMMetadata(vmsIdResourceTagToRemove, 2l, "MY_I_12", null, ResourceTag.ResourceObjectType.UserVm);

        tags = vmsIdResourceTagToRemove.get(3l);
        assertNotNull(tags);
        assertEquals(2, tags.size());

        tags = vmsIdResourceTagToRemove.get(2l);
        assertNotNull(tags);
        assertEquals(1, tags.size());
        assertTrue( tags.containsKey("TAG_MY_I_12"));
        assertEquals("", tags.get("TAG_MY_I_12"));

    }
    public void testAddTagToRemoveMetadata_wrong_type() {
        //resourceObjectType != UserVM
        TaggedResourceManagerImpl manager = new TaggedResourceManagerImpl();

        Map<Long, Map<String, String>> vmsIdResourceTagToRemove = new HashMap<Long, Map<String, String>>();

        manager.addTagToVMMetadata(vmsIdResourceTagToRemove, 3l, "MY_KEY", "123123", ResourceTag.ResourceObjectType.AutoScaleVmGroup);

        Map<String, String> tags = vmsIdResourceTagToRemove.get(3l);
        assertNull(tags);
    }

    public void testDeleteTagsMetadata() throws InsufficientCapacityException, ResourceUnavailableException {
        TaggedResourceManagerImpl manager = new TaggedResourceManagerImpl();

        ResourceTagDao resourceTagDao = mock(ResourceTagDao.class);

        List<ResourceTag> listTag2 = new ArrayList<ResourceTag>();
        listTag2.add(new ResourceTagVO("MY_TAG_1", "---", 1l,2l, 2l, ResourceTag.ResourceObjectType.UserVm, "admin", "123123-123123"));
        listTag2.add(new ResourceTagVO("MY_TAG_2", "444", 1l,2l, 2l, ResourceTag.ResourceObjectType.UserVm, "admin", "123123-123123"));


        Mockito.<List<? extends ResourceTag>>when(resourceTagDao.listBy(3l, ResourceTag.ResourceObjectType.UserVm)).thenReturn(listTag2);

        List<? extends ResourceTag> listTag3 = new ArrayList<ResourceTag>();
        listTag2.add(new ResourceTagVO("TAG_TEST", "---", 1l,2l, 3l, ResourceTag.ResourceObjectType.UserVm, "admin", "123123-123123"));
        listTag2.add(new ResourceTagVO("TAG_TEST_2", "444", 1l,2l, 3l, ResourceTag.ResourceObjectType.UserVm, "admin", "123123-123123"));

        Mockito.<List<? extends ResourceTag>>when(resourceTagDao.listBy(3l, ResourceTag.ResourceObjectType.UserVm)).thenReturn(listTag3);
        manager._resourceTagDao = resourceTagDao;

        Map<Long, Map<String, String>> vmsIdResourceTagToRemove = new HashMap<Long, Map<String, String>>();

        Map<String, String> tags2 = new HashMap<String, String>();
        tags2.put("TAG_TEST_333", "");
        tags2.put("TAG_TEST_444", "");
        tags2.put(TagKeysBuilder.TAGKEYS_METADATA_KEY, "MY_TAG_1,MY_TAG_2");
        vmsIdResourceTagToRemove.put(2l, tags2);

        Map<String, String> tags3 = new HashMap<String, String>();
        tags3.put("TAG_TEST", "");
        tags3.put("TAG_TEST_2", "");
        tags2.put(TagKeysBuilder.TAGKEYS_METADATA_KEY, "TAG_TEST,TAG_TEST_2");
        vmsIdResourceTagToRemove.put(3l, tags3);

        NetworkHelper networkHelper = mock(NetworkHelper.class);
        when(networkHelper.updateVMMetadaInVrouter(2l, tags2)).thenReturn(true);
        when(networkHelper.updateVMMetadaInVrouter(3l, tags3)).thenReturn(true);
        manager._networkHelper = networkHelper;

        manager.updateVMMetaData(vmsIdResourceTagToRemove);

        verify(networkHelper, times(1)).updateVMMetadaInVrouter(2l, tags2);
        verify(networkHelper, times(1)).updateVMMetadaInVrouter(3l, tags3);
    }


}