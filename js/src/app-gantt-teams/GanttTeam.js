/* eslint-disable flowtype/require-valid-file-annotation */
import React from 'react';
import PropTypes from 'prop-types';

import {connect} from 'react-redux';

import memoize from 'lodash.memoize';

import Avatar, { AvatarItem, AvatarGroup }  from '@atlaskit/avatar';
import Button, {ButtonGroup} from '@atlaskit/button';
import Dropdown, { DropdownItemGroup, DropdownItem } from '@atlaskit/dropdown-menu';
import DynamicTable from '@atlaskit/dynamic-table';
import ChevronRightIcon from '@atlaskit/icon/glyph/chevron-right';
import ChevronDownIcon from '@atlaskit/icon/glyph/chevron-down';
import MoreIcon from '@atlaskit/icon/glyph/more';

import {AddUsersDialog} from './AddUsersDialog';

import {ConfirmDialog} from '../common/ak/ConfirmDialog';

import {InlineEditSingleLineTextInput} from '../common/ak/InlineEditSingleLineTextInput';
import {GanttTeamActionCreators} from '../service/gantt.teams.reducer';
import {ganttTeamService} from '../service/gantt.team.store';


class GanttTeamInternal extends React.Component {
    static propTypes = {
        team: PropTypes.object.isRequired, // eslint-disable-line react/forbid-prop-types
        setTeams: PropTypes.func
    };

    state = {
        activeDialog: null,
        showingUsers: false
    };

    _toggleDialog = memoize(
        (dialog) => () => this.setState(state => {
            if (state.activeDialog === dialog) {
                return {
                    activeDialog: null
                };
            }
            return {
                activeDialog: dialog
            };
        })
    );

    _showUsers = (e) => {
        e.preventDefault();
        this.setState({showingUsers: !this.state.showingUsers});
    };

    _onConfirmEditTeamName = (name) => {
        return new Promise((resolve, reject) => {
            ganttTeamService
                .editTeam({
                    ...this.props.team,
                    name
                })
                .then(
                    teams => {
                        this.props.setTeams(teams);
                        resolve();
                    },
                    error => {
                        reject(error);
                    }
                )
        })
    };

    _onConfirmEditWeeklyHours = (user) => {
        return (hours) => {
            return new Promise((resolve, reject) => {
                ganttTeamService
                    .editUser(this.props.team, {...user, weeklyHours: hours})
                    .then(
                        teams => {
                            this.props.setTeams(teams);
                            resolve();
                        },
                        error => {
                            reject(error);
                        }
                    );
            })
        }
    };

    _onConfirmDeleteTeam = () => {
        ganttTeamService
            .deleteTeam(this.props.team)
            .then(teams => {
                this.props.setTeams(teams);
            });
    };

    _onConfirmDeleteUser = (user) => {
        ganttTeamService
            .deleteUser(this.props.team, user)
            .then(teams => {
                this.props.setTeams(teams);
            });
    };

    _createUsersTableHead = () => {
        return {
            cells: [
                {
                    key: 'name',
                    content: 'Название',
                    isSortable: true,
                },
                {
                    key: 'weeklyHours',
                    content: 'Продолжительность рабочей недели',
                    isSortable: false,
                },
                {
                    key: 'options',
                    content: 'Опции',
                    isSortable: false,
                },
            ],
        };
    };

    _getUsersRows = (team) => { // eslint-disable-line consistent-return
        if (team.users.length !== 0) {
            return team.users.map((user) => ({
                key: `gantt-team-${team.id}-user-row-${user.key}`,
                cells: [
                    {
                        key: user.key,
                        content: (
                            <AvatarItem
                                avatar={<Avatar src={user.avatarUrl} name={user.displayName} isDisabled/>}
                                primaryText={user.displayName}
                                secondaryText={user.email}
                                backgroundColor="transparent"
                                isActive={false}
                                isHover={false}
                                isFocus={false}
                                isSelected={false}
                            />
                        ),
                    },
                    {
                        content: (
                            <InlineEditSingleLineTextInput
                                isLabelHidden
                                isFitContainerWidthReadView={false}
                                id={user.key}
                                value={user.weeklyHours ? user.weeklyHours : 40}
                                onConfirm={this._onConfirmEditWeeklyHours(user)}
                            />
                        )
                    },
                    {
                        content: (
                            <div>
                                <Dropdown trigger={
                                    <Button
                                        appearance="subtle"
                                        iconBefore={<MoreIcon label=""/>}
                                    />
                                }>
                                    <DropdownItemGroup>
                                        <DropdownItem
                                            onClick={this._toggleDialog(`confirmDeleteUser${user.id}`)}
                                        >
                                            Удалить
                                        </DropdownItem>
                                    </DropdownItemGroup>
                                </Dropdown>
                                {this.state.activeDialog === `confirmDeleteUser${user.id}` ?
                                    <ConfirmDialog
                                        header="Удалить пользователя из команды"
                                        onConfirm={() => this._onConfirmDeleteUser(user)}
                                        onClose={this._toggleDialog(`confirmDeleteUser${user.id}`)}
                                    >
                                        <div>{`Вы уверены, что хотите удалить пользователя ${user.displayName} из команды ${team.name}`}</div>
                                    </ConfirmDialog> :
                                    null
                                }
                            </div>
                        ),
                    },
                ],
            }));
        }
    };

    render() {
        const {activeDialog, showingUsers} = this.state;
        const {team} = this.props;

        return (
            <div className="gantt-team">
                <div className="gantt-team-header">
                    <Button
                        appearance="subtle-link"
                        iconBefore={showingUsers ? <ChevronDownIcon label=""/> : <ChevronRightIcon label=""/>}
                        onClick={e => this._showUsers(e)}
                    />
                    <div className="gantt-title">
                        <InlineEditSingleLineTextInput
                            isLabelHidden
                            isFitContainerWidthReadView={false}
                            id={team.id}
                            value={team.name}
                            viewClassNames="gantt-team-name"
                            onConfirm={this._onConfirmEditTeamName}
                        />
                    </div>
                    <div className="flex-grow"/>
                    {team.users.length > 0 && <AvatarGroup
                        data={team.users.map(user => ({
                            key: user.key,
                            name: user.displayName ,
                            src: user.avatarUrl,
                            size: 'medium',
                            appearance: 'circle',
                            enableTooltip: true,
                        }))}
                        maxCount={3}
                    />}
                    <Dropdown
                        trigger={
                            <ButtonGroup>
                                <Button
                                    appearance="subtle"
                                    iconBefore={<MoreIcon label=""/>}
                                />
                            </ButtonGroup>
                        }
                        position="bottom right"
                    >
                        <DropdownItemGroup>
                            <DropdownItem
                                onClick={this._toggleDialog('addUsers')}
                            >
                                Добавить пользователей
                            </DropdownItem>
                            <DropdownItem
                                onClick={this._toggleDialog('confirmDeleteTeam')}
                            >
                                Удалить
                            </DropdownItem>
                        </DropdownItemGroup>
                    </Dropdown>
                    {activeDialog === 'addUsers' ?
                        <AddUsersDialog
                            team={team}
                            onClose={this._toggleDialog('addUsers')}
                        >
                            <div>{`Вы уверены, что хотите удалить команду "${team.name}"`}</div>
                        </AddUsersDialog> :
                        null
                    }
                    {activeDialog === 'confirmDeleteTeam' ?
                        <ConfirmDialog
                            header="Удалить команду"
                            onConfirm={this._onConfirmDeleteTeam}
                            onClose={this._toggleDialog('confirmDeleteTeam')}
                        >
                            <div>{`Вы уверены, что хотите удалить команду ${team.name}`}</div>
                        </ConfirmDialog> :
                        null
                    }
                </div>
                { showingUsers &&
                    <div className="gantt-team-users">
                        <DynamicTable
                            head={this._createUsersTableHead()}
                            rows={this._getUsersRows(team)}
                            rowsPerPage={10}
                            defaultPage={1}
                            emptyView={<div>There are no users added yet.</div>}
                            loadingSpinnerSize="large"
                            isLoading={false}
                            isFixedSize
                            defaultSortKey="name"
                            defaultSortOrder="ASC"
                        />
                    </div>
                }
            </div>
        );
    }
}

export const GanttTeam =
    connect(
        null,
        GanttTeamActionCreators
    )(GanttTeamInternal);
