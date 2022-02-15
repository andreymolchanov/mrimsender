import React, { ReactElement, useLayoutEffect, useState } from 'react';
import { AsyncCreatableSelect, OptionsType, OptionType } from '@atlaskit/select';
import contextPath from 'wrm/context-path';
import axios, { AxiosResponse } from 'axios';

const createOption = (label: string) => ({
  label,
  value: label.toLowerCase().replace(/\W/g, ''),
});

type Props = {
  id: string;
  className?: string;
  defaultLabels?: Array<string>;
  onChange: (value: OptionsType<OptionType>) => void;
};

const loadLabels = async (query: string): Promise<Array<OptionType>> => {
  return new Promise((resolve, reject) => {
    axios
      .get(`${contextPath()}/rest/api/1.0/labels/suggest`, {
        params: { query },
      })
      .then((response: AxiosResponse<{ suggestions: ReadonlyArray<{ html: string; label: string }> }>) => {
        resolve(response.data.suggestions.map((s) => createOption(s.label)));
      })
      .catch(reject);
  });
};

const LabelsSelect = ({ id, className, defaultLabels, onChange }: Props): ReactElement => {
  const [value, setValue] = useState<OptionsType<OptionType>>();
  const [options, setOptions] = useState<OptionsType<OptionType>>([]);

  const handleChange = (newValue: OptionsType<OptionType>) => {
    setValue(newValue);
    onChange(newValue);
  };

  const handleCreate = (inputValue: string) => {
    const newOption = createOption(inputValue);

    const newOptions = options.slice();
    newOptions.push(newOption);

    const newValue = value ? value.slice() : [];

    newValue.push(newOption);
    setValue(newValue);
    setOptions(options);
    onChange(newValue);
  };

  useLayoutEffect(() => {
    const newValues = new Set<string>(defaultLabels);
    if (defaultLabels) {
      const mappedOptions = Array.from(newValues).map(createOption);

      setValue(mappedOptions);
      onChange(mappedOptions);

      options.forEach((l) => !newValues.has(String(l.value)) && newValues.add(String(l.value)));
    }

    loadLabels('')
      .then((labelOptions) => {
        labelOptions.forEach((l) => !newValues.has(String(l.value)) && newValues.add(String(l.value)));

        setOptions(Array.from(newValues).map(createOption));
      })
      .catch(() => {
        setOptions(Array.from(newValues).map(createOption));
      });
  }, [defaultLabels]);

  return (
    <AsyncCreatableSelect
      className={className}
      isMulti
      onChange={handleChange}
      onCreateOption={handleCreate}
      options={options}
      inputId={id}
      value={value}
      cacheOptions
      defaultOptions={options}
      loadOptions={loadLabels}
    />
  );
};

export default LabelsSelect;
