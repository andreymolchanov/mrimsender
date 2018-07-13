package ru.mail.jira.plugins.calendar.service;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.filter.SearchRequestService;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.LocaleManager;
import com.atlassian.jira.datetime.DateTimeFormatter;
import com.atlassian.jira.datetime.DateTimeStyle;
import com.atlassian.jira.exception.GetException;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.RendererManager;
import com.atlassian.jira.issue.customfields.impl.CalculatedCFType;
import com.atlassian.jira.issue.customfields.impl.DateCFType;
import com.atlassian.jira.issue.customfields.impl.DateTimeCFType;
import com.atlassian.jira.issue.fields.AssigneeSystemField;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.Field;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.LabelsSystemField;
import com.atlassian.jira.issue.fields.NavigableField;
import com.atlassian.jira.issue.fields.ReporterSystemField;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutItem;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutManager;
import com.atlassian.jira.issue.fields.renderer.JiraRendererPlugin;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.label.Label;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.issue.search.ClauseNames;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchRequest;
import com.atlassian.jira.jql.builder.JqlClauseBuilder;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.timezone.TimeZoneManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.query.Query;
import com.atlassian.query.clause.Clause;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.UrlMode;
import com.atlassian.sal.api.message.I18nResolver;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.mail.jira.plugins.calendar.common.Consts;
import ru.mail.jira.plugins.calendar.common.FieldUtils;
import ru.mail.jira.plugins.calendar.common.UserUtils;
import ru.mail.jira.plugins.calendar.configuration.NonWorkingDay;
import ru.mail.jira.plugins.calendar.configuration.WorkingDaysService;
import ru.mail.jira.plugins.calendar.model.Calendar;
import ru.mail.jira.plugins.calendar.model.FavouriteQuickFilter;
import ru.mail.jira.plugins.calendar.model.QuickFilter;
import ru.mail.jira.plugins.calendar.model.UserCalendar;
import ru.mail.jira.plugins.calendar.rest.dto.*;
import ru.mail.jira.plugins.calendar.rest.dto.EventDto;
import ru.mail.jira.plugins.calendar.rest.dto.EventGroup;
import ru.mail.jira.plugins.calendar.rest.dto.IssueInfo;
import ru.mail.jira.plugins.calendar.service.applications.JiraSoftwareHelper;
import ru.mail.jira.plugins.calendar.util.FieldUtil;
import ru.mail.jira.plugins.commons.CommonUtils;

import javax.annotation.Nullable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class CalendarEventServiceImpl implements CalendarEventService {
    private final static Logger log = LoggerFactory.getLogger(CalendarEventServiceImpl.class);

    private static final int MILLIS_IN_DAY = 86_400_000;

    private final ApplicationProperties applicationProperties;
    private final CalendarService calendarService;
    private final CustomEventService customEventService;
    private final CustomFieldManager customFieldManager;
    private final FieldManager fieldManager;
    private final DateTimeFormatter dateTimeFormatter;
    private final IssueService issueService;
    private final LocaleManager localeManager;
    private final FieldLayoutManager fieldLayoutManager;
    private final JiraDeprecatedService jiraDeprecatedService;
    private final WorkingDaysService workingDaysService;
    private final RendererManager rendererManager;
    private final SearchRequestService searchRequestService;
    private final SearchService searchService;
    private final UserCalendarService userCalendarService;
    private final I18nResolver i18nResolver;
    private final AvatarService avatarService;
    private final JiraSoftwareHelper jiraSoftwareHelper;
    private final TimeZoneManager timeZoneManager;
    private final UserUtils userUtils;

    @Autowired
    public CalendarEventServiceImpl(
            @ComponentImport ApplicationProperties applicationProperties,
            @ComponentImport CustomFieldManager customFieldManager,
            @ComponentImport FieldManager fieldManager,
            @ComponentImport DateTimeFormatter dateTimeFormatter,
            @ComponentImport IssueService issueService,
            @ComponentImport LocaleManager localeManager,
            @ComponentImport FieldLayoutManager fieldLayoutManager,
            @ComponentImport RendererManager rendererManager,
            @ComponentImport SearchRequestService searchRequestService,
            @ComponentImport SearchService searchService,
            @ComponentImport I18nResolver i18nResolver,
            @ComponentImport AvatarService avatarService,
            CalendarService calendarService,
            CustomEventService customEventService,
            JiraDeprecatedService jiraDeprecatedService,
            UserCalendarService userCalendarService,
            JiraSoftwareHelper jiraSoftwareHelper,
            WorkingDaysService workingDaysService,
            @ComponentImport TimeZoneManager timeZoneManager,
            UserUtils userUtils) {
        this.applicationProperties = applicationProperties;
        this.fieldManager = fieldManager;
        this.localeManager = localeManager;
        this.calendarService = calendarService;
        this.customEventService = customEventService;
        this.customFieldManager = customFieldManager;
        this.dateTimeFormatter = dateTimeFormatter;
        this.issueService = issueService;
        this.fieldLayoutManager = fieldLayoutManager;
        this.jiraDeprecatedService = jiraDeprecatedService;
        this.workingDaysService = workingDaysService;
        this.rendererManager = rendererManager;
        this.searchRequestService = searchRequestService;
        this.searchService = searchService;
        this.userCalendarService = userCalendarService;
        this.i18nResolver = i18nResolver;
        this.avatarService = avatarService;
        this.jiraSoftwareHelper = jiraSoftwareHelper;
        this.timeZoneManager = timeZoneManager;
        this.userUtils = userUtils;
    }

    @Override
    public EventDto getEvent(int calendarId, final ApplicationUser user, String issueKey, boolean forGantt) throws GetException {
        Calendar calendar = calendarService.getCalendar(calendarId);

        String startField = calendar.getEventStart();
        CustomField startCF = null;
        if (startField.startsWith("customfield_")) {
            startCF = customFieldManager.getCustomFieldObject(startField);
            if (log.isTraceEnabled())
                log.trace("find customfield for startField. startCF={}", startCF);
            if (startCF == null)
                throw new IllegalArgumentException("Bad custom field id => " + startField);
        }

        String endField = calendar.getEventEnd();
        CustomField endCF = null;
        if (StringUtils.isNotEmpty(endField) && endField.startsWith("customfield_")) {
            endCF = customFieldManager.getCustomFieldObject(endField);
            if (log.isDebugEnabled())
                log.debug("find customfield for endField. endCF={}", endCF);
            if (endCF == null)
                throw new IllegalArgumentException("Bad custom field id => " + endField);
        }

        return buildEvent(
            calendar,
            user,
            issueService.getIssue(user, issueKey).getIssue(),
            startField,
            startCF,
            endField,
            endCF,
            null,
            EventBuilderOptions.builder().forGantt(forGantt).ignoreMissingStart(true).includeIssueInfo(true).build()
        );
    }

    @Override
    public List<EventDto> findEvents(
        final int calendarId, String groupBy,
        final String start,
        final String end,
        final ApplicationUser user,
        boolean includeIssueInfo
    ) throws ParseException, SearchException, GetException {
        return findEvents(
            calendarId, start, end, user, null,
            EventBuilderOptions
                .builder()
                .includeIssueInfo(includeIssueInfo)
                .groupBy(groupBy)
                .build()
        );
    }

    private List<EventDto> findEvents(
        final int calendarId,
        final String start,
        final String end,
        final ApplicationUser user,
        Order order,
        EventBuilderOptions options
    ) throws ParseException, SearchException, GetException {
        if (log.isDebugEnabled())
            log.debug(
                    "findEvents with params. calendarId={}, start={}, end={}, user={}",
                    calendarId, start, end, user.toString()
            );
        Calendar calendarModel = calendarService.getCalendar(calendarId);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setTimeZone(timeZoneManager.getTimeZoneforUser(user));
        SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd");
        utcFormat.setTimeZone(Consts.UTC_TZ);

        String source = calendarModel.getSource();

        List<EventDto> result;
        Date parsedStart = dateFormat.parse(start);
        Date parsedEnd = dateFormat.parse(end);
        Date utcStart = utcFormat.parse(start);
        Date utcEnd = utcFormat.parse(end);

        if (source.startsWith("project_"))
            result = getProjectEvents(calendarModel, Long.parseLong(source.substring("project_".length())),
                                      calendarModel.getEventStart(), calendarModel.getEventEnd(), parsedStart, parsedEnd, user, order, options);
        else if (source.startsWith("filter_"))
            result = getFilterEvents(calendarModel, Long.parseLong(source.substring("filter_".length())),
                                     calendarModel.getEventStart(), calendarModel.getEventEnd(), parsedStart, parsedEnd, user, order, options);
        else if (source.startsWith("jql_"))
            result = getJqlEvents(calendarModel, StringUtils.substringAfter(source, "jql_"),
                                  calendarModel.getEventStart(), calendarModel.getEventEnd(), parsedStart, parsedEnd, user, order, options);
        else {
            result = new ArrayList<>();
        }

        result.addAll(customEventService.getEvents(user, calendarModel, parsedStart, parsedEnd, utcStart, utcEnd));

        return result;
    }

    @Override
    public IssueInfo getEventInfo(ApplicationUser user, int calendarId, String eventId) throws GetException {
        Calendar calendar = calendarService.getCalendar(calendarId);
        IssueService.IssueResult issueResult = jiraDeprecatedService.issueService.getIssue(user, eventId);
        MutableIssue issue = issueResult.getIssue();
        return getEventInfo(calendar, issue, user);
    }

    private List<EventDto> getProjectEvents(Calendar calendar, long projectId,
                                            String startField, String endField,
                                            Date startTime, Date endTime,
                                            ApplicationUser user, Order order, EventBuilderOptions options) throws SearchException {
        JqlClauseBuilder jqlBuilder = JqlQueryBuilder.newClauseBuilder();
        jqlBuilder.project(projectId);
        return getEvents(calendar, jqlBuilder, startField, endField, startTime, endTime, user, order, options);
    }

    private Clause getSelectedQuickFilterClause(Calendar calendar, ApplicationUser user) {
        JqlClauseBuilder jqlBuilder = JqlQueryBuilder.newClauseBuilder();
        UserCalendar userCalendar = userCalendarService.find(calendar.getID(), user.getKey());
        if (userCalendar != null) {
            for (FavouriteQuickFilter favouriteQuickFilter : userCalendar.getFavouriteQuickFilters()) {
                QuickFilter quickFilter = favouriteQuickFilter.getQuickFilter();
                if (favouriteQuickFilter.isSelected() && (quickFilter.isShare() || quickFilter.getCreatorKey().equals(user.getKey()))) {
                    Clause selectedQuickFiltersClause = null;
                    SearchService.ParseResult parseResult = jiraDeprecatedService.searchService.parseQuery(user, quickFilter.getJql());
                    if (parseResult.isValid())
                        selectedQuickFiltersClause = parseResult.getQuery().getWhereClause();
                    else
                        log.error("JQL is invalid => {}", quickFilter.getJql());
                    if (selectedQuickFiltersClause != null)
                        jqlBuilder.and().sub().addClause(selectedQuickFiltersClause).endsub();
                }
            }
        }
        return jqlBuilder.buildClause();
    }

    private List<EventDto> getFilterEvents(
        Calendar calendar,
        long filterId,
        String startField,
        String endField,
        Date startTime,
        Date endTime,
        ApplicationUser user, Order order, EventBuilderOptions options
    ) throws SearchException {
        if (log.isDebugEnabled())
            log.debug(
                    "getFilterEvents with params. calendar={}, filterId={}, startField={}, endField={}, startTime={}, endTime={}, user={}",
                    calendar, filterId, startField, endField, startTime, endTime, user
            );
        JiraServiceContext jsCtx = new JiraServiceContextImpl(user);
        SearchRequest filter = searchRequestService.getFilter(jsCtx, filterId);

        if (log.isDebugEnabled())
            log.debug("find filter by id. filter={}", filter);
        if (filter == null) {
            log.error("Filter with id => " + filterId + " is null. Maybe it was deleted");
            return new ArrayList<>(0);
        }

        JqlClauseBuilder jqlBuilder = JqlQueryBuilder.newClauseBuilder(filter.getQuery());
        return getEvents(calendar, jqlBuilder, startField, endField, startTime, endTime, user, order, options);
    }

    private JqlClauseBuilder getCalendarQueryBuilder(ApplicationUser user, Calendar calendar) {
        String source = calendar.getSource();

        if (source.startsWith("project_")) {
            JqlClauseBuilder jqlBuilder = JqlQueryBuilder.newClauseBuilder();
            jqlBuilder.project(Long.parseLong(source.substring("project_".length())));
            return jqlBuilder;
        } else if (source.startsWith("filter_")) {
            long filterId = Long.parseLong(source.substring("filter_".length()));

            JiraServiceContext jsCtx = new JiraServiceContextImpl(user);
            SearchRequest filter = searchRequestService.getFilter(jsCtx, filterId);

            if (log.isDebugEnabled())
                log.debug("find filter by id. filter={}", filter);
            if (filter == null) {
                log.error("Filter with id => " + filterId + " is null. Maybe it was deleted");
                return null;
            }

            return JqlQueryBuilder.newClauseBuilder(filter.getQuery());
        } else if (source.startsWith("jql_")) {
            String jql = StringUtils.substringAfter(source, "jql_");

            SearchService.ParseResult parseResult = jiraDeprecatedService.searchService.parseQuery(user, jql);

            if (parseResult.isValid()) {
                return JqlQueryBuilder.newClauseBuilder(parseResult.getQuery());
            } else {
                log.error("JQL is invalid => {}", jql);
            }
        }
        return null;
    }

    private List<EventDto> getJqlEvents(
        Calendar calendar,
        String jql,
        String startField,
        String endField,
        Date startTime,
        Date endTime,
        ApplicationUser user, Order order, EventBuilderOptions options
    ) throws SearchException {
        if (log.isDebugEnabled())
            log.debug(
                    "getJqlEvents with params. calendar={}, jql={}, startField={}, endField={}, startTime={}, endTime={}, user={}",
                    calendar, jql, startField, endField, startTime, endTime, user
            );
        if (log.isDebugEnabled())
            log.debug("find filter by jql. jql={}", jql);
        if (jql == null) {
            log.error("JQL => is null.");
            return new ArrayList<>(0);
        }
        SearchService.ParseResult parseResult = jiraDeprecatedService.searchService.parseQuery(user, jql);

        if (parseResult.isValid()) {
            JqlClauseBuilder jqlBuilder = JqlQueryBuilder.newClauseBuilder(parseResult.getQuery());
            return getEvents(calendar, jqlBuilder, startField, endField, startTime, endTime, user, order, options);
        } else {
            log.error("JQL is invalid => {}", jql);
            return new ArrayList<>(0);
        }
    }

    @Override
    public Stream<EventDto> getGanttUnboundedEvents(
        Calendar calendar,
        String groupBy,
        ApplicationUser user,
        Order order,
        Long sprintId,
        List<String> fields,
        boolean forPlan
    ) throws SearchException {
        String startField = calendar.getEventStart();
        String endField = calendar.getEventEnd();

        CustomField startCF = getCf(startField);
        CustomField endCF = getCf(endField);

        List<Issue> issues = searchService
                .search(
                        user,
                        getUnboundedEventsQuery(user, calendar, sprintId, order, forPlan, forPlan),
                        PagerFilter.getUnlimitedFilter()
                )
                .getIssues();
        return issues
                .stream()
                .map(issue -> buildEventWithGroups(
                        calendar,
                        user,
                        issue,
                        startField,
                        startCF,
                        endField,
                        endCF,
                        EventBuilderOptions
                            .builder()
                            .fields(fields)
                            .groupBy(groupBy)
                            .includeIssueInfo(true)
                            .ignoreMissingStart(true)
                            .forGantt(true)
                            .build()
                ))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    @Override
    public Stream<EventDto> getGanttEventsWithDuration(
        Calendar calendar,
        String groupBy,
        ApplicationUser user,
        Order order,
        Date startTime,
        Date endTime,
        List<String> fields
    ) throws SearchException {
        return getEvents(
            calendar,
            JqlQueryBuilder.newBuilder(getUnboundedEventsQuery(user, calendar, null, order, true, false)).where(),
            calendar.getEventStart(),
            calendar.getEventEnd(),
            startTime,
            endTime,
            user,
            null,
            EventBuilderOptions
                .builder()
                .includeIssueInfo(true)
                .ignoreMissingStart(true)
                .fields(fields)
                .forGantt(true)
                .groupBy(groupBy)
                .build()
        ).stream();
    }

    @Override
    public Query getUnboundedEventsQuery(ApplicationUser user,
                                         Calendar calendar,
                                         Long sprintId,
                                         Order order,
                                         boolean onlyEstimated,
                                         boolean onlyUnresolved) {
        JqlClauseBuilder queryBuilder = getCalendarQueryBuilder(user, calendar);
        if (queryBuilder == null) {
            throw new RuntimeException("Unable to get calendar query");
        }

        if (onlyEstimated) {
            queryBuilder
                    .and()
                    .sub()
                    .originalEstimate().isNotEmpty();

            String startField = calendar.getEventStart();
            String endField = calendar.getEventEnd();

            String startFieldClause = getClauseName(startField);
            String endFieldClause = getClauseName(endField);

            if (startFieldClause != null && endFieldClause != null) {
                queryBuilder
                        .or()
                        .sub()
                        .addCondition(startFieldClause).isNotEmpty()
                        .and().addCondition(endFieldClause).isNotEmpty()
                        .endsub();
            }

            queryBuilder.endsub();
        }

        if (sprintId != null) {
            if (!jiraSoftwareHelper.isAvailable()) {
                throw new RuntimeException("jira software is not available");
            }

            queryBuilder
                    .and()
                    .customField(jiraSoftwareHelper.getSprintField().getIdAsLong())
                    .eq(sprintId);
        }

        if (onlyUnresolved) {
            queryBuilder.and().resolution().isEmpty();
        }

        Clause selectedQuickFiltersClause = getSelectedQuickFilterClause(calendar, user);
        if (selectedQuickFiltersClause != null) {
            queryBuilder.and().sub().addClause(selectedQuickFiltersClause).endsub();
        }

        return withOrder(queryBuilder.buildQuery(), order);
    }

    private CustomField getCf(String fieldId) {
        CustomField cf = null;

        if (fieldId == null) {
            return null;
        }

        if (fieldId.startsWith("customfield_")) {
            cf = customFieldManager.getCustomFieldObject(fieldId);
            if (cf == null)
                throw new IllegalArgumentException("Bad custom field id => " + fieldId);
        }
        return cf;
    }

    private List<EventDto> getEvents(
        Calendar calendar,
        JqlClauseBuilder jqlBuilder,
        String startField,
        String endField,
        Date startTime,
        Date endTime,
        ApplicationUser user, Order order, EventBuilderOptions options
    ) throws SearchException {
        List<EventDto> result = new ArrayList<>();

        CustomField startCF = null;
        if (startField.startsWith("customfield_")) {
            startCF = customFieldManager.getCustomFieldObject(startField);
            if (log.isDebugEnabled())
                log.debug("find customfield for startField. startCF={}", startCF);
            if (startCF == null)
                throw new IllegalArgumentException("Bad custom field id => " + startField);
        }

        CustomField endCF = null;
        if (StringUtils.isNotEmpty(endField) && endField.startsWith("customfield_")) {
            endCF = customFieldManager.getCustomFieldObject(endField);
            if (log.isDebugEnabled())
                log.debug("find customfield for endField. endCF={}", endCF);
            if (endCF == null)
                throw new IllegalArgumentException("Bad custom field id => " + endField);
        }
        DateTimeFormatter userDateFormat = jiraDeprecatedService.dateTimeFormatter.forUser(user).withStyle(DateTimeStyle.ISO_8601_DATE);

        jqlBuilder.and().sub();
        addDateCondition(startField, startTime, endTime, jqlBuilder, userDateFormat);
        if (StringUtils.isNotEmpty(endField)) {
            jqlBuilder.or();
            addDateCondition(endField, startTime, endTime, jqlBuilder, userDateFormat);
            jqlBuilder.or();
            addStartLessCondition(startField, startTime, jqlBuilder, userDateFormat);
            jqlBuilder.and();
            addEndGreaterCondition(endField, endTime, jqlBuilder, userDateFormat);
        }
        jqlBuilder.endsub();

        Clause selectedQuickFiltersClause = getSelectedQuickFilterClause(calendar, user);
            if (selectedQuickFiltersClause != null)
            jqlBuilder.and().sub().addClause(selectedQuickFiltersClause).endsub();

        Query query = withOrder(jqlBuilder.buildQuery(), order);

        List<Issue> issues = searchService.search(user, query, PagerFilter.getUnlimitedFilter()).getIssues();
        if (log.isDebugEnabled())
            log.debug("searchProvider.search(). query={}, user={}, issues.size()={}", query.toString(), user, issues.size());
        for (Issue issue : issues) {
            try {
                //todo: ignoreMissingStart=false
                buildEventWithGroups(calendar, user, issue, startField, startCF, endField, endCF, options).ifPresent(result::add);
            } catch (Exception e) {
                log.error(String.format("Error while trying to translate issue => %s to event", issue.getKey()), e);
            }
        }
        return result;
    }

    private Query withOrder(Query query, Order order) {
        if (order != null) {
            String fieldClauseName = getClauseName(order.getField());

            if (fieldClauseName != null) {
                return JqlQueryBuilder.newBuilder(query).orderBy().clear().add(fieldClauseName, order.getOrder(), true).buildQuery();
            }
        }
        return query;
    }

    private String getClauseName(String fieldKey) {
        if (fieldKey != null) {
            Field field = fieldManager.getField(FieldUtil.getFieldId(fieldKey));

            if (field instanceof NavigableField) {
                if (field instanceof CustomField) {
                    ClauseNames clauseNames = ((CustomField) field).getClauseNames();
                    if (clauseNames != null && clauseNames.getPrimaryName() != null) {
                        return clauseNames.getPrimaryName();
                    }
                } else {
                    return field.getId();
                }
            }
        }
        return null;
    }

    private Optional<EventDto> buildEventWithGroups(
            Calendar calendar, ApplicationUser user, Issue issue,
            String startField, CustomField startCF, String endField, CustomField endCF, EventBuilderOptions options
    ) {
        if (calendar == null || issue == null) {
            return Optional.empty();
        }

        String groupBy = options.getGroupBy();

        List<EventGroup> groups = null;
        if ("component".equals(groupBy)) {
            Collection<ProjectComponent> components = issue.getComponents();

            if (components != null && components.size() > 0) {
                groups = components
                        .stream()
                        .map(component -> EventGroup.builder().id("component/" + component.getId()).name(component.getName()).build())
                        .collect(Collectors.toList());
            }
        } else if ("fixVersion".equals(groupBy)) {
            Collection<Version> versions = issue.getFixVersions();

            if (versions != null && versions.size() > 0) {
                groups = versions
                        .stream()
                        .map(version -> EventGroup.builder().id("fixVersion/" + version.getId()).name(version.getName()).build())
                        .collect(Collectors.toList());
            }
        } else if ("affectsVersion".equals(groupBy)) {
            Collection<Version> versions = issue.getAffectedVersions();

            if (versions != null && versions.size() > 0) {
                groups = versions
                        .stream()
                        .map(version -> EventGroup.builder().id("fixVersion/" + version.getId()).name(version.getName()).build())
                        .collect(Collectors.toList());
            }
        } else if ("labels".equals(groupBy)) {
            Collection<Label> labels = issue.getLabels();

            if (labels != null && labels.size() > 0) {
                groups = labels
                        .stream()
                        .map(label -> EventGroup.builder().id("labels/" + label.getLabel()).name(label.getLabel()).build())
                        .collect(Collectors.toList());
            }
        } else if ("assignee".equals(groupBy)) {
            String group;
            String groupName;

            ApplicationUser assignee = issue.getAssignee();
            if (assignee != null) {
                group = "assignee/" + assignee.getName();
                groupName = assignee.getDisplayName();
            } else {
                group = "assignee/zz-unassigned";
                groupName = i18nResolver.getText("assignee.types.unassigned");
            }

            groups = ImmutableList.of(
                    EventGroup
                            .builder()
                            .id(group)
                            .name(groupName)
                            .avatar(avatarService.getAvatarURL(user, assignee, Avatar.Size.LARGE).toString())
                            .build()
            );
        } else if ("reporter".equals(groupBy)) {
            String group;
            String groupName;

            ApplicationUser reporter = issue.getReporter();
            if (reporter != null) {
                group = "reporter/" + reporter.getName();
                groupName = reporter.getDisplayName();
            } else {
                group = "reporter/zz-anonymous";
                groupName = i18nResolver.getText("common.words.anonymous");
            }

            groups = ImmutableList.of(
                    EventGroup
                            .builder()
                            .id(group)
                            .name(groupName)
                            .avatar(avatarService.getAvatarURL(user, reporter, Avatar.Size.LARGE).toString())
                            .build()
            );
        } else if ("issueType".equals(groupBy)) {
            IssueType issueType = issue.getIssueType();
            groups = ImmutableList.of(
                    EventGroup
                            .builder()
                            .id("issueType/" + issueType.getId())
                            .name(issueType.getNameTranslation())
                            .avatar(getBaseUrl() + issueType.getIconUrl())
                            .build()
            );
        } else if ("project".equals(groupBy)) {
            Project project = issue.getProjectObject();
            groups = ImmutableList.of(
                    EventGroup
                            .builder()
                            .id("project/" + project.getId())
                            .name(project.getName())
                            .avatar(avatarService.getProjectAvatarURL(project, Avatar.Size.LARGE).toString())
                            .build()
            );
        } else if ("priority".equals(groupBy)) {
            Priority priority = issue.getPriority();
            groups = ImmutableList.of(
                    EventGroup
                            .builder()
                            .id("priority/" + priority.getSequence())
                            .name(priority.getNameTranslation())
                            .avatar(priority.getCompleteIconUrl())
                            .build()
            );
        } else if ("epicLink".equals(groupBy)) {
            if (jiraSoftwareHelper.isAvailable()) {
                Issue epicLink = null;

                CustomField epicLinkField = jiraSoftwareHelper.getEpicLinkField();
                if (issue.isSubTask()) {
                    if (issue.getParentObject() != null) {
                        epicLink = (Issue) issue.getParentObject().getCustomFieldValue(epicLinkField);
                    }
                } else {
                    epicLink = (Issue) issue.getCustomFieldValue(epicLinkField);
                }

                if (epicLink != null) {
                    groups = ImmutableList.of(
                            EventGroup
                                    .builder()
                                    .id("epic/" + epicLink.getKey())
                                    .name(issue.getKey() + " - " + epicLink.getSummary())
                                    .build()
                    );
                } else {
                    if (jiraSoftwareHelper.getEpicIssueType().equals(issue.getIssueType())) {
                        groups = ImmutableList.of(
                                EventGroup
                                        .builder()
                                        .id("epic/" + issue.getKey())
                                        .name(issue.getKey() + " - " + issue.getSummary())
                                        .build()
                        );
                    }
                }
            }
        } else if ("resolution".equals(groupBy)) {
            Resolution resolution = issue.getResolution();
            if (resolution != null) {
                groups = ImmutableList.of(
                        EventGroup
                                .builder()
                                .id("resolution/" + resolution.getId())
                                .name(resolution.getNameTranslation())
                                .build()
                );
            } else {
                groups = ImmutableList.of(
                        EventGroup
                                .builder()
                                .id("resolution/zz-unresolved")
                                .name(i18nResolver.getText("common.concepts.unresolved"))
                                .build()
                );
            }
        }
        Optional<EventDto> event = Optional.of(buildEvent(
            calendar, user, issue, startField, startCF, endField, endCF, groups, options
        ));

        event.ifPresent(eventDto -> fillExtraFields(eventDto.getIssueInfo(), options.getFields(), issue));

        return event;
    }

    private EventDto buildEvent(
        Calendar calendar, ApplicationUser user, Issue issue,
        String startField, CustomField startCF, String endField, CustomField endCF, List<EventGroup> groups,
        EventBuilderOptions options
    ) {
        if (calendar == null || issue == null)
            return null;
        DateTimeFormatter userDateFormat = jiraDeprecatedService.dateTimeFormatter.forUser(user).withStyle(DateTimeStyle.ISO_8601_DATE);
        DateTimeFormatter userDateTimeFormat = jiraDeprecatedService.dateTimeFormatter.forUser(user).withStyle(DateTimeStyle.ISO_8601_DATE_TIME);
        Date startDate = startCF == null ? retrieveDateByField(issue, startField) : retrieveDateByField(issue, startCF);
        if (log.isTraceEnabled())
            log.trace("Issue startDate={}", startDate);
        Date endDate = null;
        if (StringUtils.isNotEmpty(endField))
            endDate = endCF == null ? retrieveDateByField(issue, endField) : retrieveDateByField(issue, endCF);
        if (log.isTraceEnabled())
            log.trace("Issue endDate={}", endDate);
        boolean isAllDay = isAllDayEvent(startCF, endCF, startField, endField);
        if (log.isTraceEnabled())
            log.trace("Issue isAllDay={}", isAllDay);
        boolean dateFieldsIsDraggable = isDateFieldsDraggable(startField, startCF, endField, endCF);
        if (log.isTraceEnabled())
            log.trace("dateFieldsIsDraggable={}", dateFieldsIsDraggable);
        Long originalEstimate = issue.getOriginalEstimate();
        Long timeSpent = issue.getTimeSpent();

        EventDto event = new EventDto();
        event.setCalendarId(calendar.getID());
        event.setId(issue.getKey());
        event.setTitle(issue.getSummary());
        event.setColor(calendar.getColor());
        event.setAllDay(isAllDay);
        event.setIssueTypeImgUrl(issue.getIssueType().getIconUrl());
        if (calendar.isShowIssueStatus()) {
            event.setStatus(issue.getStatus().getName());
            event.setStatusColor(issue.getStatus().getStatusCategory().getColorName());
        }
        event.setType(EventDto.Type.ISSUE);
        event.setOriginalEstimateSeconds(originalEstimate);
        event.setOriginalEstimate(originalEstimate != null ? ComponentAccessor.getJiraDurationUtils().getShortFormattedDuration(originalEstimate) : null);
        event.setTimeSpentSeconds(timeSpent);
        event.setTimeSpent(timeSpent != null ? ComponentAccessor.getJiraDurationUtils().getShortFormattedDuration(timeSpent) : null);
        event.setResolved(issue.getResolutionId() != null);
 		event.setAssignee(issue.getAssignee() != null ? userUtils.buildUserDto(issue.getAssignee(), Avatar.Size.SMALL) : null);

        if (groups != null) {
            event.setGroupField(options.getGroupBy());
            event.setGroups(groups);
        }

        if (startDate == null && endDate == null && !options.isIgnoreMissingStart()) { // Something unbelievable
            log.error("Event " + issue.getKey() + " doesn't contain startDate and endDate");
            return null;
        }

        if (startDate != null && endDate != null) {
            if ((!DUE_DATE_KEY.equals(endField) && !(endCF != null && endCF.getCustomFieldType() instanceof DateCFType)) && startDate.after(endDate) || startDate.after(new Date(endDate.getTime() + MILLIS_IN_DAY))) {
                if (!options.isForGantt()) {
                    Date tmpDate = startDate;
                    String tmpField = startField;
                    CustomField tmpCF = startCF;
                    startDate = endDate;
                    startField = endField;
                    startCF = endCF;
                    endDate = tmpDate;
                    endField = tmpField;
                    endCF = tmpCF;
                    event.setDatesError(true);
                    dateFieldsIsDraggable = false;
                } else {
                    endDate = null;
                }
            }
        }

        DateTimeFormatter startFormatter = startField.equals(DUE_DATE_KEY) || startCF != null && startCF.getCustomFieldType() instanceof DateCFType ? userDateFormat.withSystemZone() : userDateTimeFormat;
        DateTimeFormatter endFormatter = endField != null && endField.equals(DUE_DATE_KEY) || endCF != null && endCF.getCustomFieldType() instanceof DateCFType ? userDateFormat.withSystemZone() : userDateTimeFormat;
        if (startDate != null) {
            event.setStart(startFormatter.format(startDate));
            event.setStartDate(startDate);
            if (endDate != null)
                if (DUE_DATE_KEY.equals(endField) || endCF != null && endCF.getCustomFieldType() instanceof DateCFType) {
                    event.setEnd(endFormatter.format(new Date(endDate.getTime() + MILLIS_IN_DAY)));
                    event.setEndDate(new Date(endDate.getTime() + MILLIS_IN_DAY));
                } else {
                    event.setEnd(endFormatter.format(endDate));
                    event.setEndDate(endDate);
                }
        } else {
            event.setStart(endFormatter.format(endDate));
            event.setStartDate(endDate);
            event.setDatesError(true);
            dateFieldsIsDraggable = false;
        }

        event.setStartEditable(dateFieldsIsDraggable && jiraDeprecatedService.issueService.isEditable(issue, user));
        event.setDurationEditable(isDateFieldResizable(endField, endCF) && startDate != null && endDate != null && jiraDeprecatedService.issueService.isEditable(issue, user));

        if (options.isIncludeIssueInfo()) {
            event.setIssueInfo(getEventInfo(calendar, issue, user));
        }

        if (options.isForGantt()) {
            if (calendar.getGanttMilestones() != null) {
                List<EventMilestoneDto> milestones = new ArrayList<>();

                for (String fieldKey : calendar.getGanttMilestones().split(",")) {
                    String fieldId = FieldUtil.getFieldId(fieldKey);

                    Field field = fieldManager.getField(fieldId);

                    if (field == null) {
                        log.warn("non-existing field {}", fieldId);
                        continue;
                    }
                    CustomField customField = field instanceof CustomField ? (CustomField) field : null;

                    DateTimeFormatter fieldFormatter = fieldKey.equals(DUE_DATE_KEY) || customField != null && customField.getCustomFieldType() instanceof DateCFType ? userDateFormat.withSystemZone() : userDateTimeFormat;

                    Date date = customField != null ? retrieveDateByField(issue, customField) : retrieveDateByField(issue, fieldKey);

                    if (date != null) {
                        milestones.add(new EventMilestoneDto(
                            field.getId(),
                            field.getName(),
                            fieldFormatter.format(date))
                        );
                    }
                }

                event.setMilestones(milestones);
            }
        }

        return event;
    }

    private String getBaseUrl() {
        return applicationProperties.getBaseUrl(UrlMode.ABSOLUTE);
    }

    private IssueInfo getEventInfo(Calendar calendar, Issue issue, ApplicationUser user) {
        IssueInfo result = new IssueInfo(issue.getKey(), issue.getSummary());
        result.setStatusColor(issue.getStatus().getStatusCategory().getColorName());
        if (issue.getIssueType().getAvatar() != null)
            result.setAvatarUrl(String.format("secure/viewavatar?size=xsmall&avatarId=%d&avatarType=issuetype", issue.getIssueType().getAvatar().getId()));

        DateTimeFormatter userDateFormat = jiraDeprecatedService.dateTimeFormatter.forUser(user).withStyle(DateTimeStyle.ISO_8601_DATE);
        DateTimeFormatter userDateTimeFormat = jiraDeprecatedService.dateTimeFormatter.forUser(user);
        CustomField startCF = null;
        if (calendar.getEventStart().startsWith("customfield_")) {
            startCF = customFieldManager.getCustomFieldObject(calendar.getEventStart());
            if (log.isDebugEnabled())
                log.debug("find customfield for startField. startCF={}", startCF);
            if (startCF == null)
                throw new IllegalArgumentException("Bad custom field id => " + calendar.getEventStart());
        }

        CustomField endCF = null;
        if (StringUtils.isNotEmpty(calendar.getEventEnd()) && calendar.getEventEnd().startsWith("customfield_")) {
            endCF = customFieldManager.getCustomFieldObject(calendar.getEventEnd());
            if (log.isDebugEnabled())
                log.debug("find customfield for endField. endCF={}", endCF);
            if (endCF == null)
                throw new IllegalArgumentException("Bad custom field id => " + calendar.getEventEnd());
        }
        Date startDate = startCF == null ? retrieveDateByField(issue, calendar.getEventStart()) : retrieveDateByField(issue, startCF);
        if (log.isTraceEnabled())
            log.trace("Issue startDate={}", startDate);
        Date endDate = null;
        if (StringUtils.isNotEmpty(calendar.getEventEnd()))
            endDate = endCF == null ? retrieveDateByField(issue, calendar.getEventEnd()) : retrieveDateByField(issue, endCF);

        DateTimeFormatter startFormatter = calendar.getEventStart().equals(DUE_DATE_KEY) || startCF != null && startCF.getCustomFieldType() instanceof DateCFType ? userDateFormat.withSystemZone() : userDateTimeFormat;
        DateTimeFormatter endFormatter = calendar.getEventEnd() != null && calendar.getEventEnd().equals(DUE_DATE_KEY) || endCF != null && endCF.getCustomFieldType() instanceof DateCFType ? userDateFormat.withSystemZone() : userDateTimeFormat;
        if (startDate != null) {
            result.setStart(startFormatter.format(startDate));
            if (endDate != null)
                result.setEnd(endFormatter.format(endDate));
        }
        if (StringUtils.isNotEmpty(calendar.getDisplayedFields()))
            fillDisplayedFields(result, calendar.getDisplayedFields().split(","), issue);
        return result;
    }

    private void addDateCondition(String field, Date startTime, Date endTime, JqlClauseBuilder jcb, DateTimeFormatter dateTimeFormatter) {
        if (field.equals(DUE_DATE_KEY))
            jcb.dueBetween(dateTimeFormatter.format(startTime), dateTimeFormatter.format(endTime));
        else if (field.equals(CREATED_DATE_KEY))
            jcb.createdBetween(dateTimeFormatter.format(startTime), dateTimeFormatter.format(endTime));
        else if (field.equals(UPDATED_DATE_KEY))
            jcb.updatedBetween(dateTimeFormatter.format(startTime), dateTimeFormatter.format(endTime));
        else if (field.equals(RESOLVED_DATE_KEY))
            jcb.resolutionDateBetween(dateTimeFormatter.format(startTime), dateTimeFormatter.format(endTime));
        else if (field.startsWith("customfield_")) {
            CustomField customField = customFieldManager.getCustomFieldObject(field);
            if (log.isDebugEnabled())
                log.debug("add DateRangeCondition for customfield. customField={}, start={}, end={}", customField, startTime, endTime);
            if (customField == null)
                throw new IllegalArgumentException("Bad custom field id => " + field);
            jcb.customField(customField.getIdAsLong()).range(dateTimeFormatter.format(startTime), dateTimeFormatter.format(endTime));
        } else
            throw new IllegalArgumentException("Bad field => " + field);
    }

    private void addStartLessCondition(String startField, Date startTime, JqlClauseBuilder jcb, DateTimeFormatter dateTimeFormatter) {
        if (startField.equals(DUE_DATE_KEY))
            jcb.due().ltEq(dateTimeFormatter.format(startTime));
        else if (startField.equals(CREATED_DATE_KEY))
            jcb.created().ltEq(dateTimeFormatter.format(startTime));
        else if (startField.equals(UPDATED_DATE_KEY))
            jcb.updated().ltEq(dateTimeFormatter.format(startTime));
        else if (startField.equals(RESOLVED_DATE_KEY))
            jcb.resolutionDate().ltEq(dateTimeFormatter.format(startTime));
        else if (startField.startsWith("customfield_")) {
            CustomField customField = customFieldManager.getCustomFieldObject(startField);
            if (log.isDebugEnabled())
                log.debug("add DateRangeCondition for customfield. customField={}, start={}", new Object[]{customField, startTime});
            if (customField == null)
                throw new IllegalArgumentException("Bad custom field id => " + startField);
            jcb.customField(customField.getIdAsLong()).ltEq(dateTimeFormatter.format(startTime));
        } else
            throw new IllegalArgumentException("Bad field => " + startField);
    }

    private void addEndGreaterCondition(String endField, Date endTime, JqlClauseBuilder jcb, DateTimeFormatter dateTimeFormatter) {
        if (endField.equals(DUE_DATE_KEY))
            jcb.due().gtEq(dateTimeFormatter.format(endTime));
        else if (endField.equals(CREATED_DATE_KEY))
            jcb.created().gtEq(dateTimeFormatter.format(endTime));
        else if (endField.equals(UPDATED_DATE_KEY))
            jcb.updated().gtEq(dateTimeFormatter.format(endTime));
        else if (endField.equals(RESOLVED_DATE_KEY))
            jcb.resolutionDate().gtEq(dateTimeFormatter.format(endTime));
        else if (endField.startsWith("customfield_")) {
            CustomField customField = customFieldManager.getCustomFieldObject(endField);
            if (log.isDebugEnabled())
                log.debug("add DateRangeCondition for customfield. customField={}, start={}", new Object[]{customField, endTime});
            if (customField == null)
                throw new IllegalArgumentException("Bad custom field id => " + endField);
            jcb.customField(customField.getIdAsLong()).gtEq(dateTimeFormatter.format(endTime));
        } else
            throw new IllegalArgumentException("Bad field => " + endField);
    }

    private boolean isAllDayEvent(@Nullable CustomField startCF, @Nullable CustomField endCF,
                                  @Nullable String startField, @Nullable String endField) {
        boolean startFieldForDate;
        if (startCF != null)
            startFieldForDate = isDateField(startCF);
        else if (startField != null)
            startFieldForDate = startField.equals(DUE_DATE_KEY);
        else
            throw new IllegalArgumentException("Event start can not be null");

        boolean endFieldForDate;
        if (endCF != null) {
            endFieldForDate = isDateField(endCF);
        } else if (endField != null) {
            endFieldForDate = endField.equals(DUE_DATE_KEY);
        } else {
            return startFieldForDate;
        }

        return endFieldForDate && startFieldForDate;
    }

    @Override
    public EventDto moveEvent(ApplicationUser user, int calendarId, String eventId, String start, String end, boolean forGantt) throws Exception {
        return moveEvent(user, calendarId, eventId, start, end, null, forGantt);
    }

    @Override
    public EventDto moveEvent(ApplicationUser user, int calendarId, String eventId, String start, String end, String estimate, boolean forGantt) throws Exception {
        return moveEvent(user, calendarId, eventId, start, end, estimate, ImmutableList.of(), forGantt);
    }

    @Override
    public EventDto moveEvent(ApplicationUser user, int calendarId, String eventId, String start, String end, String estimate, List<String> fields, boolean forGantt) throws Exception {
        IssueService.IssueResult issueResult = issueService.getIssue(user, eventId);
        MutableIssue issue = issueResult.getIssue();
        if (!issueService.isEditable(issue, user))
            throw new IllegalArgumentException("Can not edit issue with key => " + eventId);
        Calendar calendar = calendarService.getCalendar(calendarId);
        CustomField eventStartCF = null;
        CustomField eventEndCF = null;

        if (calendar.getEventStart().startsWith("customfield_")) {
            eventStartCF = customFieldManager.getCustomFieldObject(calendar.getEventStart());
            if (eventStartCF == null)
                throw new IllegalStateException("Can not find custom field => " + calendar.getEventStart()); //todo:xxx may be not stateException
        }
        if (calendar.getEventEnd() != null && calendar.getEventEnd().startsWith("customfield_")) {
            eventEndCF = customFieldManager.getCustomFieldObject(calendar.getEventEnd());
            if (eventEndCF == null)
                throw new IllegalStateException("Can not find custom field => " + calendar.getEventEnd()); //todo:xxx may be not stateException
        }

        if (isDateFieldNotDraggable(calendar.getEventStart(), eventStartCF) && isDateFieldNotDraggable(calendar.getEventEnd(), eventEndCF))
            throw new IllegalArgumentException(String.format("Can not move event with key => %s, because it contains not draggable event date field", issue.getKey()));

        DateTimeFormatter dateTimeFormat = jiraDeprecatedService.dateTimeFormatter.forUser(user).withStyle(DateTimeStyle.ISO_8601_DATE_TIME);
        DateTimeFormatter datePickerFormat = jiraDeprecatedService.dateTimeFormatter.forUser(user).withZone(timeZoneManager.getTimeZoneforUser(user)).withStyle(DateTimeStyle.DATE_PICKER);
        DateTimeFormatter dateTimePickerFormat = jiraDeprecatedService.dateTimeFormatter.forUser(user).withStyle(DateTimeStyle.DATE_TIME_PICKER);

        IssueInputParameters issueInputParams = issueService.newIssueInputParameters();

        if (start != null && isDateFieldResizable(calendar.getEventStart(), eventStartCF)) {
            Date startDate = dateTimeFormat.parse(start);
            if (eventStartCF != null) {
                DateTimeFormatter formatter = eventStartCF.getCustomFieldType() instanceof DateTimeCFType ? dateTimePickerFormat : datePickerFormat;
                issueInputParams.addCustomFieldValue(eventStartCF.getIdAsLong(), formatter.format(startDate));
            } else
                issueInputParams.setDueDate(datePickerFormat.format(startDate));
        }
        if (end != null && isDateFieldResizable(calendar.getEventEnd(), eventEndCF) && !calendar.getEventStart().equals(calendar.getEventEnd())) {
            Date endDate = dateTimeFormat.parse(end);
            if (eventEndCF != null) {
                if (eventEndCF.getCustomFieldType() instanceof DateTimeCFType)
                    issueInputParams.addCustomFieldValue(eventEndCF.getIdAsLong(), dateTimePickerFormat.format(endDate));
                else
                    issueInputParams.addCustomFieldValue(eventEndCF.getIdAsLong(), datePickerFormat.format(new Date(endDate.getTime() - MILLIS_IN_DAY)));
            } else
                issueInputParams.setDueDate(datePickerFormat.format(new Date(endDate.getTime() - MILLIS_IN_DAY)));
        }
        if (estimate != null) {
            Long estimateSeconds = ComponentAccessor.getJiraDurationUtils().parseDuration(estimate, localeManager.getLocaleFor(user));
            issueInputParams.setOriginalAndRemainingEstimate(
                TimeUnit.SECONDS.toMinutes(estimateSeconds) + "m",
                TimeUnit.SECONDS.toMinutes(issue.getTimeSpent() != null ? estimateSeconds - issue.getTimeSpent() : estimateSeconds) + "m"
            );
        }

        IssueService.UpdateValidationResult updateValidationResult = issueService.validateUpdate(user, issue.getId(), issueInputParams);
        if (!updateValidationResult.isValid())
            throw new Exception(CommonUtils.formatErrorCollection(updateValidationResult.getErrorCollection()));

        IssueService.IssueResult updateResult = issueService.update(user, updateValidationResult);
        if (!updateResult.isValid())
            throw new Exception(CommonUtils.formatErrorCollection(updateResult.getErrorCollection()));

        EventDto event = buildEvent(
            calendar, user, issueService.getIssue(user, eventId).getIssue(),
            calendar.getEventStart(), eventStartCF, calendar.getEventEnd(), eventEndCF, ImmutableList.of(),
            EventBuilderOptions.builder().forGantt(forGantt).ignoreMissingStart(true).includeIssueInfo(true).build()
        );

        fillExtraFields(event.getIssueInfo(), fields, updateResult.getIssue());

        return event;
    }

    @Override
    public List<EventDto> getHolidays(ApplicationUser user) throws GetException {
        List<EventDto> result = new ArrayList<>();
        DateTimeFormatter userDateFormat = jiraDeprecatedService.dateTimeFormatter.forUser(user).withStyle(DateTimeStyle.ISO_8601_DATE);
        for (NonWorkingDay day : workingDaysService.getNonWorkingDays()) {
            EventDto eventDto = new EventDto();
            eventDto.setType(EventDto.Type.HOLIDAY);
            eventDto.setColor("#d7d7d7");
            eventDto.setTitle(day.getDescription());
            eventDto.setStart(userDateFormat.withSystemZone().format(day.getDate()));
            eventDto.setEnd(userDateFormat.withSystemZone().format(day.getDate()));
            eventDto.setRendering("background");
            eventDto.setAllDay(true);
            result.add(eventDto);
        }
        return result;
    }

    private Date retrieveDateByField(Issue issue, String field) {
        if (field.equals(DUE_DATE_KEY))
            return issue.getDueDate();
        if (field.equals(CREATED_DATE_KEY))
            return issue.getCreated();
        if (field.equals(UPDATED_DATE_KEY))
            return issue.getUpdated();
        if (field.equals(RESOLVED_DATE_KEY))
            return issue.getResolutionDate();
        throw new IllegalArgumentException("Unknown field => " + field);
    }

    private Date retrieveDateByField(Issue issue, CustomField customField) {
        if (log.isTraceEnabled())
            log.trace("retrieveDateByField with params. issue={}, customField={}, customFieldType={}", issue, customField, customField.getClass());
        if (!FieldUtils.isDateField(customField))
            throw new IllegalArgumentException("Bad date time => " + customField.getName());
        return (Date) issue.getCustomFieldValue(customField);
    }

    private boolean isDateTimeField(CustomField cf) {
        return cf.getCustomFieldType() instanceof DateTimeCFType || FieldUtils.isScriptRunnerField(cf);
    }

    private boolean isDateField(CustomField cf) {
        return !isDateTimeField(cf);
    }

    private boolean isDateFieldResizable(String field, CustomField customField) {
        if (customField != null) {
            return !(customField.getCustomFieldType() instanceof CalculatedCFType);
        }
        return !CREATED_DATE_KEY.equals(field) && !UPDATED_DATE_KEY.equals(field) && !RESOLVED_DATE_KEY.equals(field);
    }

    private boolean isDateFieldNotDraggable(String field, CustomField customField) {
        return !isDateFieldResizable(field, customField);
    }

    private boolean isDateFieldsDraggable(String startField, CustomField startCf, String endField, CustomField endCf) {
        return isDateFieldResizable(startField, startCf) && (isDateFieldResizable(endField, endCf) || StringUtils.isEmpty(endField));
    }

    private void fillExtraFields(IssueInfo issueInfo, List<String> fields, Issue issue) {
        if (fields != null) {
            Priority priority = issue.getPriority();
            if (priority != null) {
                issueInfo.setPrioritySequence(priority.getSequence());
            }

            for (String fieldId : fields) {
                Field field = fieldManager.getField(fieldId);
                //todo: if orderablefield, maybe render viewHtml
                if (field instanceof NavigableField) {
                    FieldLayoutItem fieldLayoutItem = fieldLayoutManager.getFieldLayout(issue).getFieldLayoutItem(fieldId);
                    String columnViewHtml = ((NavigableField) field).getColumnViewHtml(fieldLayoutItem, ImmutableMap.of(), issue);
                    if (StringUtils.isNotEmpty(columnViewHtml))
                        issueInfo.addCustomField(field.getId(), columnViewHtml);
                }
            }
        }
    }

    private void fillDisplayedFields(IssueInfo issueInfo, String[] extraFields, Issue issue) {
        DateTimeFormatter userDateTimeFormatter = dateTimeFormatter.forLoggedInUser();
        for (String extraField : extraFields) {
            if (extraField.startsWith("customfield_")) {
                CustomField customField = customFieldManager.getCustomFieldObject(extraField);
                FieldLayoutItem fieldLayoutItem = fieldLayoutManager.getFieldLayout(issue).getFieldLayoutItem(customField);
                if (customField != null) {
                    String columnViewHtml = customField.getColumnViewHtml(fieldLayoutItem, new HashMap<>(), issue);
                    if (StringUtils.isNotEmpty(columnViewHtml))
                        issueInfo.addCustomField(customField.getName(), columnViewHtml);
                }
            } else if (extraField.equals(CalendarServiceImpl.REPORTER)) {
                if (issue.getReporter() != null) {
                    FieldLayoutItem reporterLayoutItem = fieldLayoutManager.getFieldLayout(issue).getFieldLayoutItem("reporter");

                    String columnViewHtml = ((ReporterSystemField) reporterLayoutItem.getOrderableField()).getColumnViewHtml(reporterLayoutItem, new HashMap(), issue);
                    issueInfo.setReporter(columnViewHtml);
                }
            } else if (extraField.equals(CalendarServiceImpl.ASSIGNEE)) {
                if (issue.getAssignee() != null) {
                    FieldLayoutItem assigneeLayoutItem = fieldLayoutManager.getFieldLayout(issue).getFieldLayoutItem("assignee");
                    String columnViewHtml = ((AssigneeSystemField) assigneeLayoutItem.getOrderableField()).getColumnViewHtml(assigneeLayoutItem, new HashMap(), issue);
                    issueInfo.setAssignee(columnViewHtml);
                }
            } else if (extraField.equals(CalendarServiceImpl.STATUS))
                issueInfo.setStatus(issue.getStatus().getName());
            else if (extraField.equals(CalendarServiceImpl.LABELS)) {
                if (issue.getLabels() != null && !issue.getLabels().isEmpty()) {
                    FieldLayoutItem labelsLayoutItem = fieldLayoutManager.getFieldLayout(issue).getFieldLayoutItem("labels");
                    String columnViewHtml = ((LabelsSystemField) labelsLayoutItem.getOrderableField()).getColumnViewHtml(labelsLayoutItem, new HashMap(), issue);
                    issueInfo.setLabels(columnViewHtml);
                }
            } else if (extraField.equals(CalendarServiceImpl.COMPONENTS) && issue.getComponents() != null && !issue.getComponents().isEmpty()) {
                List<String> components = new ArrayList<>();
                for (ProjectComponent pc : issue.getComponents())
                    components.add(pc.getName());
                issueInfo.setComponents(components.toString());
            } else if (extraField.equals(CalendarServiceImpl.DUEDATE) && issue.getDueDate() != null)
                issueInfo.setDueDate(userDateTimeFormatter.forLoggedInUser().withSystemZone().withStyle(DateTimeStyle.ISO_8601_DATE).format(issue.getDueDate()));
            else if (extraField.equals(CalendarServiceImpl.ENVIRONMENT) && issue.getEnvironment() != null)
                issueInfo.setEnvironment(issue.getEnvironment());
            else if (extraField.equals(CalendarServiceImpl.PRIORITY) && issue.getPriority() != null) {
                issueInfo.setPriority(issue.getPriority().getName());
                issueInfo.setPriorityIconUrl(issue.getPriority().getIconUrl());
                issueInfo.setPrioritySequence(issue.getPriority().getSequence());
            } else if (extraField.equals(CalendarServiceImpl.RESOLUTION) && issue.getResolution() != null)
                issueInfo.setResolution(issue.getResolution().getName());
            else if (extraField.equals(CalendarServiceImpl.AFFECT) && issue.getAffectedVersions() != null && !issue.getAffectedVersions().isEmpty()) {
                List<String> affectVersions = new ArrayList<>();
                for (Version ver : issue.getAffectedVersions())
                    affectVersions.add(ver.getName());
                issueInfo.setAffect(affectVersions.toString());
            } else if (extraField.equals(CalendarServiceImpl.CREATED))
                issueInfo.setCreated(userDateTimeFormatter.format(issue.getCreated()));
            else if (extraField.equals(CalendarServiceImpl.UPDATED))
                issueInfo.setUpdated(userDateTimeFormatter.format(issue.getUpdated()));
            else if (extraField.equals(CalendarServiceImpl.DESCRIPTION)) {
                if (StringUtils.isNotEmpty(issue.getDescription())) {
                    JiraRendererPlugin defaultRenderer = rendererManager.getRendererForType("default-renderer");
                    JiraRendererPlugin jeditorRenderer = rendererManager.getRendererForType("jeditor-renderer");
                    String renderedDescription;
                    if (defaultRenderer != jeditorRenderer)
                        renderedDescription = jeditorRenderer.render(issue.getDescription(), null);
                    else
                        renderedDescription = rendererManager.getRendererForType("atlassian-wiki-renderer").render(issue.getDescription(), null);
                    issueInfo.setDescription(renderedDescription);
                }
            }
        }
    }
}