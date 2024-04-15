/* eslint-disable react/jsx-props-no-spreading */
import React, { ReactElement, useState } from 'react'
import styled from 'styled-components'
import { I18n } from '@atlassian/wrm-react-i18n'
import contextPath from 'wrm/context-path'
import DropdownMenu, {
  DropdownItem,
  DropdownItemGroup,
} from '@atlaskit/dropdown-menu'
import Button from '@atlaskit/button'
import MoreIcon from '@atlaskit/icon/glyph/more'
import PersonIcon from '@atlaskit/icon/glyph/person'
import PeopleGroupIcon from '@atlaskit/icon/glyph/people-group'
import DynamicTable from '@atlaskit/dynamic-table'
import { OptionsType, OptionType } from '@atlaskit/select'
import MyteamImage from '../../assets/myteam.png'
import {
  useGetSubscriptions,
  useRunSubscriptionMutation,
  useSubscriptionDelete,
  useSubscriptionMutation,
} from '../../shared/hooks'
import { FilterSubscription } from '../../shared/types'
import ConfirmationDialog from '../../shared/components/dialogs/ConfirmationDialog'
import CreateFilterSubscriptionDialog from './CreateFilterSubscriptionDialog'
import EditFilterSubscriptionDialog from './EditFilterSubscriptionDialog'
import { typeOptions } from './FilterSubscriptionForm'
import UsersSelect from './UsersSelect'
import JqlFilterSelect from './JqlFilterSelect'
import RecipientsSelect from './RecipientsSelect'
import GroupsSelect from './GroupsSelect'
import ChatsSelect from './ChatsSelect'

const Page = styled.div`
  background-color: #fff;
  padding: 20px;
`

const Header = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
`

const Left = styled.div`
  display: flex;
  align-items: center;

  h1 {
    margin: 0;
  }
`

const Logo = styled.img`
  padding-right: 5px;
  vertical-align: middle;
`

const PageDescription = styled.div`
  padding-block: 10px;
`

const FiltersBlock = styled.div`
  padding-bottom: 10px;
  display: flex;
  align-items: center;
`

const SmallSelect = styled.div`
  width: 200px;
  margin-right: 10px;
`

const Cell = styled.div`
  margin-block: 5px;
`

const Recipient = styled.div`
  display: flex;
  align-items: center;
  padding: 2px;

  div:last-child {
    margin-left: 5px;
  }
`

const tableHead = {
  cells: [
    {
      content: I18n.getText(
        'ru.mail.jira.plugins.myteam.subscriptions.page.table.subscriber'
      ),
      key: 'subscriber',
      isSortable: false,
    },
    {
      content: I18n.getText('report.chart.filter'),
      key: 'filter',
      isSortable: false,
    },
    {
      content: I18n.getText(
        'ru.mail.jira.plugins.myteam.subscriptions.page.table.subscribed'
      ),
      key: 'subscribed',
      isSortable: false,
    },
    {
      content: I18n.getText('filtersubscription.field.schedule'),
      key: 'schedule',
      isSortable: false,
    },
    {
      content: I18n.getText('admin.schedulerdetails.last.run'),
      key: 'lastRun',
      isSortable: false,
    },
    {
      content: I18n.getText('admin.schedulerdetails.next.run'),
      key: 'nextRun',
      isSortable: false,
    },
    {
      content: I18n.getText(
        'ru.mail.jira.plugins.myteam.subscriptions.page.subscription.field.type'
      ),
      key: 'type',
      isSortable: false,
    },
    {
      content: I18n.getText('common.words.actions'),
      key: 'actions',
      isSortable: false,
    },
  ],
}

type BuildRowsProps = {
  subscriptions?: FilterSubscription[]
  selectSubscription: (subscription: FilterSubscription) => void
  runSubscription: (subscriptionId: number) => void
  openEditDialog: (open: boolean) => void
  openDeleteDialog: (open: boolean) => void
}

const buildRows = ({
  subscriptions,
  selectSubscription,
  runSubscription,
  openEditDialog,
  openDeleteDialog,
}: BuildRowsProps) => {
  return subscriptions?.map((subscription) => ({
    cells: [
      {
        key: subscription.creator?.userKey,
        content: (
          <Cell>
            <Recipient
              key={subscription.creator?.userKey}
              title={I18n.getText('common.words.user')}
            >
              <PersonIcon size="small" label="" />
              <div>{subscription.creator?.displayName}</div>
            </Recipient>
          </Cell>
        ),
      },
      {
        key: subscription.filter?.id,
        content: (
          <Cell>
            {subscription.filter !== undefined ? (
              <a
                href={`${contextPath()}/issues/?filter=${
                  subscription.filter?.id
                }`}
                target="_blank"
                rel="noreferrer"
              >
                {subscription.filter?.name}
              </a>
            ) : (
              I18n.getText(
                'ru.mail.jira.plugins.myteam.subscriptions.page.table.filter.deleted'
              )
            )}
          </Cell>
        ),
      },
      {
        key: subscription.recipientsType,
        content: (
          <Cell>
            {subscription.recipientsType === 'USER' &&
              subscription.users?.map((user) => (
                <Recipient
                  key={user.userKey}
                  title={I18n.getText('common.words.user')}
                >
                  <PersonIcon size="small" label="" />
                  <div>{user.displayName}</div>
                </Recipient>
              ))}
            {subscription.recipientsType === 'GROUP' &&
              subscription.groups?.map((group) => (
                <Recipient
                  key={group}
                  title={I18n.getText('common.words.group')}
                >
                  <PeopleGroupIcon size="small" label="" />
                  <div>{group}</div>
                </Recipient>
              ))}
            {subscription.recipientsType === 'CHAT' &&
              subscription.chats?.map((chat) => (
                <Recipient
                  key={chat}
                  title={I18n.getText(
                    'ru.mail.jira.plugins.myteam.createChat.panel'
                  )}
                >
                  <img height={16} src={MyteamImage} alt="Myteam logo icon" />
                  <div>{chat}</div>
                </Recipient>
              ))}
          </Cell>
        ),
      },
      {
        key: subscription.scheduleDescription,
        content: (
          <Cell>
            {subscription.scheduleMode === 'advanced'
              ? subscription.advanced
              : subscription.scheduleDescription}
          </Cell>
        ),
      },
      {
        key: subscription.lastRun,
        content: (
          <Cell>
            {subscription.lastRun === undefined
              ? I18n.getText('admin.common.words.never')
              : subscription.lastRun}
          </Cell>
        ),
      },
      {
        key: subscription.nextRun,
        content: <Cell>{subscription.nextRun}</Cell>,
      },
      {
        key: subscription.type,
        content: (
          <Cell>
            {
              typeOptions.find((type) => type.value === subscription.type)
                ?.label
            }
          </Cell>
        ),
      },
      {
        content: (
          <Cell>
            <DropdownMenu
              trigger={({ triggerRef, ...props }) => (
                <Button
                  {...props}
                  appearance="subtle"
                  iconBefore={<MoreIcon label="more" />}
                  ref={triggerRef}
                />
              )}
            >
              <DropdownItemGroup>
                <DropdownItem
                  onClick={() => {
                    if (subscription.id) {
                      runSubscription(subscription.id)
                    }
                  }}
                >
                  {I18n.getText('common.forms.run.now')}
                </DropdownItem>
                <DropdownItem
                  onClick={() => {
                    selectSubscription(subscription)
                    openEditDialog(true)
                  }}
                >
                  {I18n.getText('common.words.edit')}
                </DropdownItem>
                <DropdownItem
                  onClick={() => {
                    selectSubscription(subscription)
                    openDeleteDialog(true)
                  }}
                >
                  {I18n.getText('common.words.delete')}
                </DropdownItem>
              </DropdownItemGroup>
            </DropdownMenu>
          </Cell>
        ),
      },
    ],
  }))
}

function ManageFilterSubscriptions(): ReactElement {
  const [isOpenCreateSubscriptionDialog, setOpenCreateSubscriptionDialog] =
    useState<boolean>(false)
  const [isOpenEditSubscriptionDialog, setOpenEditSubscriptionDialog] =
    useState<boolean>(false)
  const [isOpenDeleteSubscriptionDialog, setOpenDeleteSubscriptionDialog] =
    useState<boolean>(false)
  const [selectedSubscription, setSelectedSubscription] =
    useState<FilterSubscription>()

  const [filterSubscribers, setFilterSubscribers] = useState<OptionsType>()
  const [filter, setFilter] = useState<OptionType | null>()
  const [recipientsType, setRecipientsType] = useState<string | undefined>()
  const [users, setUsers] = useState<OptionsType>()
  const [groups, setGroups] = useState<OptionsType>()
  const [chats, setChats] = useState<OptionsType>()

  const getRecipients = () => {
    if (recipientsType === 'USER') {
      return users?.map((user) => user.value.toString())
    }
    if (recipientsType === 'GROUP') {
      return groups?.map((group) => group.value.toString())
    }
    if (recipientsType === 'CHAT') {
      return chats?.map((chat) => chat.value.toString())
    }
    return undefined
  }

  const subscriptions = useGetSubscriptions({
    subscribers: filterSubscribers?.map(({ value }) => value.toString()),
    filterId:
      filter !== undefined && filter !== null
        ? parseInt(filter.value.toString(), 10)
        : undefined,
    recipientsType,
    recipients: getRecipients(),
  })
  const deleteSubscription = useSubscriptionDelete()
  const subscriptionMutation = useSubscriptionMutation()
  const runSubscriptionMutation = useRunSubscriptionMutation()

  const runSubscription = (subscriptionId: number) => {
    runSubscriptionMutation.mutate(subscriptionId, {
      onSuccess: () => {
        subscriptions.refetch()
      },
    })
  }

  return (
    <Page>
      <Header>
        <Left>
          <Logo height={48} src={MyteamImage} alt="Myteam logo" />
          <h1>
            {I18n.getText(
              'ru.mail.jira.plugins.myteam.subscriptions.page.title'
            )}
          </h1>
        </Left>
        <Button onClick={() => setOpenCreateSubscriptionDialog(true)}>
          {I18n.getText('subscriptions.add')}
        </Button>
      </Header>
      <PageDescription>
        {I18n.getText(
          'ru.mail.jira.plugins.myteam.subscriptions.page.description'
        )}
      </PageDescription>
      <FiltersBlock>
        <SmallSelect>
          <UsersSelect
            id="filterSubscriber"
            selectedValue={filterSubscribers}
            onChange={setFilterSubscribers}
            placeholder={I18n.getText(
              'ru.mail.jira.plugins.myteam.subscriptions.page.table.subscriber'
            )}
          />
        </SmallSelect>
        <SmallSelect>
          <JqlFilterSelect
            id="filter"
            selectedValue={filter}
            onChange={setFilter}
            placeholder={I18n.getText('template.subscription.filter')}
            isClearable
          />
        </SmallSelect>
        <SmallSelect>
          <RecipientsSelect
            id="recipients"
            selectedValue={recipientsType}
            onChange={(value) => {
              setRecipientsType(value?.value)
            }}
            placeholder={I18n.getText(
              'ru.mail.jira.plugins.myteam.subscriptions.page.table.subscribed'
            )}
            isClearable
          />
        </SmallSelect>
        <SmallSelect>
          {recipientsType === 'USER' && (
            <UsersSelect id="users" onChange={setUsers} />
          )}
          {recipientsType === 'GROUP' && (
            <GroupsSelect
              id="groups"
              selectedValue={groups}
              onChange={setGroups}
            />
          )}
          {recipientsType === 'CHAT' && (
            <ChatsSelect id="chats" selectedValue={chats} onChange={setChats} />
          )}
        </SmallSelect>
      </FiltersBlock>
      <DynamicTable
        head={tableHead}
        rows={buildRows({
          subscriptions: subscriptions.data,
          selectSubscription: setSelectedSubscription,
          runSubscription,
          openEditDialog: setOpenEditSubscriptionDialog,
          openDeleteDialog: setOpenDeleteSubscriptionDialog,
        })}
        rowsPerPage={20}
        defaultPage={1}
        isLoading={subscriptions.isLoading}
        emptyView={
          <h2>
            {I18n.getText(
              'ru.mail.jira.plugins.myteam.subscriptions.page.empty'
            )}
          </h2>
        }
        loadingSpinnerSize="large"
      />
      <CreateFilterSubscriptionDialog
        isOpen={isOpenCreateSubscriptionDialog}
        onClose={() => {
          setOpenCreateSubscriptionDialog(false)
          subscriptionMutation.reset()
        }}
        onSaveSuccess={(subscription) => {
          subscriptionMutation.mutate(subscription, {
            onSuccess: () => {
              setOpenCreateSubscriptionDialog(false)
              subscriptions.refetch()
            },
          })
        }}
        creationError={subscriptionMutation.error?.response?.data}
      />
      {selectedSubscription && selectedSubscription.id && (
        <EditFilterSubscriptionDialog
          isOpen={isOpenEditSubscriptionDialog}
          currentValue={selectedSubscription}
          onClose={() => {
            setOpenEditSubscriptionDialog(false)
            subscriptionMutation.reset()
          }}
          onSaveSuccess={(subscription) => {
            subscriptionMutation.mutate(subscription, {
              onSuccess: () => {
                subscriptions.refetch()
                setOpenEditSubscriptionDialog(false)
              },
            })
          }}
          editingError={subscriptionMutation.error?.response?.data}
        />
      )}
      <ConfirmationDialog
        isOpen={isOpenDeleteSubscriptionDialog}
        title={I18n.getText(
          'ru.mail.jira.plugins.myteam.subscriptions.page.subscription.delete'
        )}
        body={I18n.getText(
          'ru.mail.jira.plugins.myteam.subscriptions.page.subscription.delete.description'
        )}
        onOk={() => {
          if (selectedSubscription && selectedSubscription.id) {
            deleteSubscription.mutate(selectedSubscription.id, {
              onSuccess: () => {
                setOpenDeleteSubscriptionDialog(false)
                subscriptions.refetch()
              },
            })
          }
        }}
        onCancel={() => setOpenDeleteSubscriptionDialog(false)}
      />
    </Page>
  )
}

export default ManageFilterSubscriptions
