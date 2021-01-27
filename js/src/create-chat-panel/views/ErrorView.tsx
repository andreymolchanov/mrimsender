import React, { ErrorInfo } from 'react';
import { I18n } from '@atlassian/wrm-react-i18n';
import SectionMessage from '@atlaskit/section-message';

type ErrorViewProps = {
  error?: Error;
  reactErrorInfo?: ErrorInfo;
};

export const ErrorView = (props: ErrorViewProps) => {
  const { error, reactErrorInfo } = props;
  return (
    <>
      <SectionMessage appearance="error">
        <p>{I18n.getText('ru.mail.jira.plugins.myteam.createChat.panel.error.info')}</p>
        <p>{I18n.getText('ru.mail.jira.plugins.myteam.createChat.panel.error.contact')}</p>
        {(error || reactErrorInfo) && <p>{`Details: `}</p>}
        {error && error.message && <p>{`Error: ${error.message}`}</p>}
        {reactErrorInfo && <p>{`React ErrorInfo: ${reactErrorInfo.componentStack}`}</p>}
      </SectionMessage>
    </>
  );
};

export const makeFaultTolerantComponent = (ErrorView: React.ComponentType<ErrorViewProps>) =>
  class extends React.Component<any, { error?: Error; errorInfo?: ErrorInfo }> {
    constructor(props: any) {
      super(props);
      this.state = { error: undefined, errorInfo: undefined };
    }

    componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
      // Catch errors in any components below and re-render with error message
      this.setState({
        error,
        errorInfo,
      });
      // You can also log error messages to an error reporting service here
    }

    render() {
      const { error, errorInfo } = this.state;
      if (error || errorInfo) {
        // Render error view
        return <ErrorView error={error} reactErrorInfo={errorInfo} />;
      }
      // Normally, just render children
      return this.props.children;
    }
  };
