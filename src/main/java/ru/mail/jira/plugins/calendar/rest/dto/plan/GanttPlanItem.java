package ru.mail.jira.plugins.calendar.rest.dto.plan;

import lombok.Getter;
import lombok.Setter;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import ru.mail.jira.plugins.calendar.rest.dto.gantt.GanttTaskForm;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@Getter @Setter
@XmlRootElement
@JsonIgnoreProperties(ignoreUnknown = true)
public class GanttPlanItem extends GanttTaskForm {
    @XmlElement
    private String taskId;
}
