import $ from 'jquery'
import GH from 'GH'

type Issue = {
  issueKey: string
  fields: Field[]
}

type Field = {
  label: string
}

export default function init(): void {
  if (GH !== undefined) {
    GH.QuickCreateIssueInEpic.addIssuesToEpic = function (issues: Issue[]) {
      const issueKeys: string[] = []

      $.each(issues, function (i, issue) {
        if (
          issue.fields.filter((field) => field.label === 'Epic Link').length === 0
        ) {
          issueKeys.push(issue.issueKey)
        }
      })

      const addToEpicRequest = {
        ignoreEpics: true,
        issueKeys,
      } // add issues to epic

      return GH.Ajax.put({
        url: `/epics/${GH.QuickCreateIssueInEpic.epicKey}/add`,
        data: addToEpicRequest,
      })
    }
  }
}
