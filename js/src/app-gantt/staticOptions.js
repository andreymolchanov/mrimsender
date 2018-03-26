// eslint-disable-next-line import/no-extraneous-dependencies
import moment from 'moment';

import {keyedConfigs} from './scaleConfigs';
import {views} from './views';


export const groupOptions = [
    {
        value: 'assignee',
        label: 'По исполнителю'
    },
    {
        value: 'reporter',
        label: 'По постановщику'
    },
    {
        value: 'project',
        label: 'По проекту'
    },
    {
        value: 'issueType',
        label: 'По типу задачи'
    },
    {
        value: 'priority',
        label: 'По приоритету'
    },
    {
        value: 'resolution',
        label: 'По резолюции'
    },
    {
        value: 'epicLink',
        label: 'По эпику'
    },
];

export const defaultOptions = {
    scale: keyedConfigs[1].i,
    startDate: moment().subtract(1, 'months').format('YYYY-MM-DD'),
    endDate: moment().add(3, 'months').format('YYYY-MM-DD'),
    groupBy: null,
    orderBy: null,
    order: true,
    view: views.basic.key,
    columns: [
        {
            key: 'timeoriginalestimate',
            name: 'Оценка',
            isJiraField: true,
            colParams: {
                width: '53px'
            }
        },
        {
            key: 'assignee',
            name: 'Исполнитель',
            isJiraField: true,
            colParams: {
                width: '200px'
            }
        }
    ]
};
