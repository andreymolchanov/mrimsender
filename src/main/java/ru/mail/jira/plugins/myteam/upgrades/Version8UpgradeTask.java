/* (C)2024 */
package ru.mail.jira.plugins.myteam.upgrades;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.activeobjects.external.ModelVersion;
import com.atlassian.sal.api.transaction.TransactionCallback;
import lombok.extern.slf4j.Slf4j;
import ru.mail.jira.plugins.myteam.db.model.IssueCreationSettings;

@Slf4j
public class Version8UpgradeTask {

  public ModelVersion getModelVersion() {
    return ModelVersion.valueOf("8");
  }

  public void upgrade(final ModelVersion currentVersion, final ActiveObjects activeObjects) {
    log.info("Current version " + currentVersion.toString());
    if (currentVersion.isOlderThan(getModelVersion())) {
      activeObjects.migrate(IssueCreationSettings.class);
      log.info("Run upgrade task to version 8");
      activeObjects.executeInTransaction(
          (TransactionCallback<Void>)
              () -> {
                for (IssueCreationSettings settings :
                    activeObjects.find(IssueCreationSettings.class)) {
                  settings.setAllowedHandleCfValues(Boolean.FALSE);
                  settings.save();
                }
                return null;
              });
    }
  }
}
