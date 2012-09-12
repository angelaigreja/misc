
package com.pekall.pctool.test;

import android.test.AndroidTestCase;

import com.pekall.pctool.model.calendar.CalendarInfo;
import com.pekall.pctool.model.calendar.CalendarUtil;
import com.pekall.pctool.model.calendar.EventInfo;

import junit.framework.Assert;

import java.util.Date;


public class CalendarUtilTestCase extends AndroidTestCase {

    public void testAddCalendar() throws Exception {
        CalendarInfo calendarInfo = new CalendarInfo();

        calendarInfo.name = "test add calendar 2";

        long calendarId = CalendarUtil.addCalendar(getContext(), calendarInfo);

        assertTrue(calendarId > 0);
    }

    public void testAddEvent() throws Exception {
        EventInfo er = new EventInfo();
        er.alertTime = 100;
        er.note = "这是我的测试1";
        er.place = "华阳";
        er.title = "事件测试";
        er.calendarId = 1;
        er.startTime = new Date().getTime();
        er.endTime = er.startTime + 30 * 60 * 1000;
        boolean success = CalendarUtil.addEvent(mContext, er) > 0;
        System.out.println("------>flag" + success);
        Assert.assertTrue(success);
    }
}
