/**
 * Copyright (C) 2015 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 **/

package org.exoplatform.calendar.storage.jcr;

import org.exoplatform.calendar.model.query.CalendarQuery;
import org.exoplatform.calendar.service.Calendar;
import org.exoplatform.calendar.service.CalendarService;
import org.exoplatform.calendar.service.GroupCalendarData;
import org.exoplatform.calendar.service.impl.CalendarServiceImpl;
import org.exoplatform.calendar.service.impl.JCRDataStorage;
import org.exoplatform.calendar.storage.CalendarDAO;
import org.exoplatform.calendar.storage.Storage;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Identity;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class JCRCalendarDAOImpl implements CalendarDAO {
  
  private Storage context;
  private JCRDataStorage            dataStorage;

  private static final Log          LOG   = ExoLogger.getExoLogger(JCRCalendarDAOImpl.class);

  private static final ListAccess<Calendar> EMPTY = new ListAccess<Calendar>() {
    @Override
    public Calendar[] load(int i, int i2) throws Exception, IllegalArgumentException {
      return new Calendar[0];
    }

    @Override
    public int getSize() throws Exception {
      return 0;
    }
  };

  public JCRCalendarDAOImpl(CalendarService service, JCRStorage context) {
    this.context = context;
    this.dataStorage = ((CalendarServiceImpl) service).getDataStorage();
  }

  @Override
  public Calendar getById(String id) {
    try {
      Calendar cal = dataStorage.getCalendarById(id);
      if (cal != null) {
        cal.setDS(JCRStorage.JCR_STORAGE);
      }
      return cal;
    } catch (Exception ex) {
      LOG.error("Exception while loading calendar by ID", ex);
      return null;
    }
  }
  
  @Override
  public org.exoplatform.calendar.model.Calendar save(org.exoplatform.calendar.model.Calendar calendar) {
    return persist(calendar, true);
  }

  @Override
  public org.exoplatform.calendar.model.Calendar update(org.exoplatform.calendar.model.Calendar cal) {
    return persist(cal, false);
  }

  private org.exoplatform.calendar.model.Calendar persist(org.exoplatform.calendar.model.Calendar cal, boolean isNew) {
    Calendar o;
    if (cal instanceof Calendar) {
      o = (Calendar)cal;
    } else {
      o = Calendar.build(cal);
    }

    int type = o.getCalType();
    
    if (type == Calendar.Type.PERSONAL.type()) {
      try {
        dataStorage.saveUserCalendar(o.getCalendarOwner(), o, isNew);
      } catch (Exception ex) {
        LOG.error(ex);
      }
    } else if (type == Calendar.Type.GROUP.type()) {
      try {
        dataStorage.savePublicCalendar(o, isNew, null);
      } catch (Exception ex) {
        LOG.error(ex);
      }
    } else {
      throw new UnsupportedOperationException("Save calendar with type '" + type + "' is not supported");
    }
    
    return cal;
  }

  @Override
  public Calendar remove(String id) {
    Calendar calendar = getById(id);
    if (calendar == null) {
      return null;
    }

    if (calendar.getCalType() == Calendar.Type.PERSONAL.type()) {
      try {
        dataStorage.removeUserCalendar(calendar.getCalendarOwner(), id);
      } catch (Exception ex) {
        LOG.error(ex);
      }
    } else if (calendar.getCalType() == Calendar.Type.GROUP.type()) {
      try {
        dataStorage.removeGroupCalendar(id);
      } catch (Exception ex) {
        LOG.error(ex);
      }
    }
    return calendar;
  }

  @Override
  public Calendar newInstance() {
    Calendar c = new Calendar();
    c.setDS(JCRStorage.JCR_STORAGE);
    return c;
  }

  @Override
  public List<org.exoplatform.calendar.model.Calendar> findCalendars(CalendarQuery query) {
    List<org.exoplatform.calendar.model.Calendar> calendars = new LinkedList<org.exoplatform.calendar.model.Calendar>();
    List<String> excludes = Collections.emptyList();
    String[] excludesId = query.getExclusions();
    if (excludesId != null) {
      excludes = Arrays.asList(excludesId);
    }
    
    try {
      Identity identity = query.getIdentity();
      List<Calendar> cals = dataStorage.getUserCalendars(identity.getUserId(), true);
      if (cals != null && cals.size() > 0) {
        for (Calendar c : cals) {
          if (!excludes.contains(c.getId())) {            
            calendars.add(c);
          }
        }
      }

      GroupCalendarData data = dataStorage.getSharedCalendars(identity.getUserId(), true);
      if (data != null && data.getCalendars().size() > 0) {
        for (Calendar c : data.getCalendars()) {
          if (!excludes.contains(c.getId())) {
            calendars.add(c);
          }
        }
      }

      List<GroupCalendarData> datas = dataStorage.getGroupCalendars(identity.getGroups().toArray(new String[0]), true, identity.getUserId());
      if (datas != null && datas.size() > 0) {
        for (GroupCalendarData d : datas) {
          for (Calendar c : d.getCalendars()) {
            if (!excludes.contains(c.getId())) {
              calendars.add(c);
            }
          }
        }
      }
      
      for (org.exoplatform.calendar.model.Calendar cal : calendars) {
        cal.setDS(JCRStorage.JCR_STORAGE);
      }

      return calendars;
    } catch (Exception ex) {
      LOG.error(ex);
    }
    return null;
  }
}
