import React, { Fragment, ReactElement } from 'react'
import styled from 'styled-components'
import { I18n } from '@atlassian/wrm-react-i18n'
import Spinner from '@atlaskit/spinner'
import contextPath from 'wrm/context-path'
import Form, { Field, FormFooter, HelperMessage } from '@atlaskit/form'
import TextArea from '@atlaskit/textarea'
import Button from '@atlaskit/button'
import Avatar, { AvatarItem } from '@atlaskit/avatar'
import SectionMessage from '@atlaskit/section-message'
import {
  useAccessRequestMutation,
  useGetAccessRequest,
} from '../../shared/hooks'

type Props = {
  issueKey: string | null
}

type FormState = {
  message: string
}

const Container = styled.div`
  box-sizing: border-box;
  border: 1px solid #c1c7d0;
  border-radius: 5px;
  margin: 50px auto 0;
  padding: 40px;
  text-align: left;
  width: 700px;
  background: #ffffff;

  h2 {
    margin-bottom: 20px;
  }

  form {
    margin-top: 20px;
  }
`

const Head = styled.h1`
  a {
    margin-right: 5px;
  }
`

const EmptyPageContainer = styled.div`
  margin: 48px auto;
  text-align: center;
  width: 500px;
`

function AccessRequest({ issueKey }: Props): ReactElement {
  if (issueKey == null)
    return (
      <EmptyPageContainer>
        <h2>
          {I18n.getText(
            'ru.mail.jira.plugins.myteam.accessRequest.page.error.issueKey'
          )}
        </h2>
      </EmptyPageContainer>
    )

  const accessRequest = useGetAccessRequest(issueKey)
  const accessRequestMutation = useAccessRequestMutation()

  return (
    <Container>
      <Head>
        <a href={`${contextPath()}/browse/${issueKey}`}>{issueKey}</a>
        {I18n.getText('ru.mail.jira.plugins.myteam.accessRequest.page.title')}
      </Head>
      {accessRequest.isLoading ? (
        <Spinner size="large" />
      ) : accessRequest.data ? (
        <Form
          onSubmit={(formState: FormState) => {
            const { data, refetch } = accessRequest

            const isRequestNeeded = issueKey && data
            if (!isRequestNeeded) return

            const { users, sent } = data
            const { message } = formState

            accessRequestMutation.mutate(
              { issueKey, accessRequest: { users, message, sent } },
              { onSuccess: refetch }
            )
          }}
        >
          {({ formProps }) => (
            <form
              // eslint-disable-next-line react/jsx-props-no-spreading
              {...formProps}
            >
              <div className="form-body">
                {accessRequest.data?.sent && (
                  <SectionMessage appearance="success">
                    <p>
                      {I18n.getText(
                        'ru.mail.jira.plugins.myteam.accessRequest.page.send.success'
                      )}
                    </p>
                    <p>
                      {I18n.getText(
                        'ru.mail.jira.plugins.myteam.accessRequest.page.send.next'
                      )}
                    </p>
                  </SectionMessage>
                )}
                <Field
                  name="users"
                  label={I18n.getText(
                    'ru.mail.jira.plugins.myteam.accessRequest.page.field.users'
                  )}
                >
                  {({ fieldProps }: any) => (
                    <>
                      {accessRequest.data?.users.map(
                        ({ userKey, displayName, email, avatarUrl }) => (
                          <AvatarItem
                            key={userKey}
                            avatar={
                              <Avatar appearance="circle" src={avatarUrl} />
                            }
                            primaryText={displayName}
                            secondaryText={email}
                          />
                        )
                      )}
                      <HelperMessage>
                        {I18n.getText(
                          'ru.mail.jira.plugins.myteam.accessRequest.page.field.users.description'
                        )}
                      </HelperMessage>
                    </>
                  )}
                </Field>
                <Field
                  name="message"
                  label={I18n.getText(
                    'ru.mail.jira.plugins.myteam.accessRequest.page.field.message'
                  )}
                  defaultValue={accessRequest.data?.message}
                  isDisabled={
                    accessRequest.data?.sent || accessRequestMutation.isLoading
                  }
                >
                  {({ fieldProps }: any) => (
                    <>
                      <TextArea {...fieldProps} />
                      <HelperMessage>
                        {I18n.getText(
                          'ru.mail.jira.plugins.myteam.accessRequest.page.field.message.description'
                        )}
                      </HelperMessage>
                    </>
                  )}
                </Field>
              </div>
              <FormFooter>
                <Button
                  type="submit"
                  appearance="primary"
                  isDisabled={
                    accessRequest.data?.sent || accessRequestMutation.isLoading
                  }
                >
                  {I18n.getText('common.forms.send.request')}
                </Button>
              </FormFooter>
            </form>
          )}
        </Form>
      ) : (
        <EmptyPageContainer>
          <h2>
            {I18n.getText(
              'ru.mail.jira.plugins.myteam.accessRequest.page.error.accessRequest'
            )}
          </h2>
        </EmptyPageContainer>
      )}
    </Container>
  )
}

export default AccessRequest
