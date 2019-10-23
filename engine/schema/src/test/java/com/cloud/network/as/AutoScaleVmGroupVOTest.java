package com.cloud.network.as;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoScaleVmGroupVOTest {

    @Test
    public void isLocked() {
        AutoScaleVmGroupVO vo = new AutoScaleVmGroupVO();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 10);
        vo.lockExpirationDate = calendar.getTime();
        assertTrue(vo.isLocked());
    }

    @Test
    public void isNotLockedWhenLockIsNull() {
        AutoScaleVmGroupVO vo = new AutoScaleVmGroupVO();
        assertFalse(vo.isLocked());
    }

    @Test
    public void isNotLockedWhenLockedIsNotNullButIsBeforeNow() {
        AutoScaleVmGroupVO vo = new AutoScaleVmGroupVO();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, -10);
        vo.lockExpirationDate = calendar.getTime();
        assertFalse(vo.isLocked());
    }

    @Test
    public void lock() {
        AutoScaleVmGroupVO vo = new AutoScaleVmGroupVO();
        vo.lock();
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 9);
        assertTrue(vo.lockExpirationDate.after(calendar.getTime()));
        assertTrue(vo.isLocked());
    }
}