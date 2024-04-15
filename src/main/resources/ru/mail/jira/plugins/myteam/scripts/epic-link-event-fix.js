require(['jquery'], function ($) {
  AJS.toInit(function () {
    if (GH !== undefined) {
      GH.QuickCreateIssueInEpic.addIssuesToEpic = function(issues) {
        let issueKeys = [];

        $.each(issues, function (i, issue) {
          if (issue.fields.filter(field => field.label === "Epic Link").length === 0) {
            issueKeys.push(issue.issueKey);
          }
        });

        const addToEpicRequest = {
          ignoreEpics: true,
          issueKeys: issueKeys
        }; // add issues to epic

        return GH.Ajax.put({
          url: '/epics/' + GH.QuickCreateIssueInEpic.epicKey + '/add',
          data: addToEpicRequest
        });
      }
    }
  });
});
