package ru.mail.jira.plugins.calendar.model;

import net.java.ao.Entity;
import net.java.ao.OneToMany;
import net.java.ao.schema.Default;
import net.java.ao.schema.NotNull;

public interface UserCalendar extends Entity {
    String getUserKey();
    void setUserKey(String userKey);

    int getCalendarId();
    void setCalendarId(int calendarId);

    String getName();
    void setName(String name);

    String getColor();
    void setColor(String color);

    boolean isEnabled();
    void setEnabled(boolean isEnabled);

    @OneToMany
    FavouriteQuickFilter[] getFavouriteQuickFilters();

    @Default("AND")
    @NotNull
    String getQuickFilterJoin();
    void setQuickFilterJoin(String quickFilterJoin);
}
