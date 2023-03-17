/* (C)2023 */
package ru.mail.jira.plugins.myteam.service;

import com.atlassian.jira.user.ApplicationUser;
import org.jetbrains.annotations.Nullable;
import ru.mail.jira.plugins.myteam.controller.dto.ReminderDto;

public interface ReminderService {
  int addReminder(ReminderDto reminder, @Nullable ApplicationUser user);
}