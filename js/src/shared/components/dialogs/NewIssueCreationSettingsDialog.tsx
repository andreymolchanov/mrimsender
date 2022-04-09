import Button from '@atlaskit/button';
import Modal, { ModalBody, ModalFooter, ModalHeader, ModalTitle, ModalTransition } from '@atlaskit/modal-dialog';
import SectionMessage from '@atlaskit/section-message';
import React, { ReactElement, useLayoutEffect, useState } from 'react';
import { IssueCreationSettings, LoadableDataState } from '../../types';
import EditIssueCreationSettingsForm, { FORM_ID } from '../EditIssueCreationSettingsForm';
import { I18n } from '@atlassian/wrm-react-i18n';
import { createChatIssueCreationSettings, loadChatIssueDefaultSettings } from '../../api/SettingsApiClient';
import { useTimeoutState } from '../../hooks';

type Props = {
  className?: string;

  chatId: string;
  isOpen: boolean;
  onClose: () => void;
  onSaveSuccess: () => void;
};

enum Status {
  Success,
  Error,
  None,
}

const DEFAULT_SETTINGS: Partial<IssueCreationSettings> = {
  enabled: true,
  tag: 'task',
};

const NewIssueCreationSettingsDialog = ({ isOpen, chatId, onClose, onSaveSuccess }: Props): ReactElement => {
  const [statusState, setStatus] = useTimeoutState<{ status: Status | null; error?: string }>({ status: Status.None });
  const [settings, setSettings] = useState<LoadableDataState<Partial<IssueCreationSettings>>>({ isLoading: false });

  useLayoutEffect(() => {
    setSettings({ isLoading: true });
    loadChatIssueDefaultSettings()
      .then((responce) => {
        const defaultData: Partial<IssueCreationSettings> = { ...DEFAULT_SETTINGS, ...responce.data };
        setSettings({ isLoading: false, data: defaultData });
      })
      .catch((e) => {
        console.error(e);
        setSettings({ isLoading: false, error: JSON.stringify(e) });
      });
  }, []);

  return (
    <ModalTransition>
      {isOpen && (
        <Modal onClose={onClose}>
          <ModalHeader>
            <ModalTitle>Новые настройки для чата</ModalTitle>
          </ModalHeader>
          {statusState.status !== Status.None ? (
            <SectionMessage appearance={statusState.status === Status.Success ? 'success' : 'error'}>
              <p>{statusState.status === Status.Success ? 'Настройки успешно сохранены.' : statusState.error}</p>
            </SectionMessage>
          ) : null}
          <ModalBody>
            <EditIssueCreationSettingsForm
              defaultSettings={settings.data ?? DEFAULT_SETTINGS}
              onCancel={() => {
                onClose();
              }}
              onSave={(settings) => {
                if (chatId) {
                  (settings as IssueCreationSettings).chatId = chatId;
                  createChatIssueCreationSettings(settings as IssueCreationSettings)
                    .then(() => {
                      onSaveSuccess();
                    })
                    .catch((e) => {
                      setStatus({ status: Status.Error, error: e.response.data.error }, 5000);
                    });
                }
              }}
            />
          </ModalBody>
          <ModalFooter>
            <Button form={FORM_ID} type="submit" appearance="primary" autoFocus>
              {I18n.getText('common.forms.create')}
            </Button>
            <Button
              form={FORM_ID}
              appearance="subtle"
              onClick={() => {
                onClose();
                setStatus({ status: Status.None }, 0);
              }}>
              {I18n.getText('common.forms.cancel')}
            </Button>
          </ModalFooter>
        </Modal>
      )}
    </ModalTransition>
  );
};

export default NewIssueCreationSettingsDialog;
