package ru.mail.jira.plugins.calendar.rest.dto.gantt;

import lombok.Getter;
import lombok.Setter;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@Getter @Setter
@XmlRootElement
@JsonIgnoreProperties(ignoreUnknown = true)
public class GanttLinkDto extends GanttLinkForm {
    @XmlElement
    private int id;
    @XmlElement
    private int entityId;
    @XmlElement
    private String color;
    private int calendarId;
}