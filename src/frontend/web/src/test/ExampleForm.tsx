import { exampleSchema } from '@dispatchflow/schemas'
import { DateField } from '../components/form/fields/DateField.tsx'
import { NumberField } from '../components/form/fields/NumberField.tsx'
import { PhoneField } from '../components/form/fields/PhoneField.tsx'
import { SelectField } from '../components/form/fields/SelectField.tsx'
import { TextAreaField } from '../components/form/fields/TextAreaField.tsx'
import { TextField } from '../components/form/fields/TextField.tsx'
import { TimeWindowField } from '../components/form/fields/TimeWindowField.tsx'
import { SaveButton } from '../components/form/SaveButton.tsx'
import { SchemaForm } from '../components/form/SchemaForm.tsx'
import { useSchemaForm } from '../components/form/useSchemaForm.ts'
import { notifySaved } from '../components/notifySaved.ts'
import { useLanguage } from '../components/useLanguage.ts'
import { useVersionedSave } from '../lib/versionedSave.ts'
import { EXAMPLE_DATE, EXAMPLE_DEPOTS, EXAMPLE_PATH, EXAMPLE_STRINGS } from './exampleFormData.ts'
import { sendJson } from './sendJson.ts'

/**
 * A form built from the shared pieces alone: its schema, its fields and its strings, and one save
 * call. It shows that a real form, such as the depot form, needs nothing more (#173).
 */
export function ExampleForm({ etag }: { readonly etag: string | null }) {
  const strings = EXAMPLE_STRINGS[useLanguage()]
  const form = useSchemaForm(exampleSchema, {
    defaultValues: {
      name: '',
      notes: '',
      phone: '',
      depot: '',
      date: EXAMPLE_DATE,
      window: { start: '', end: '' },
      priority: 3,
    },
  })
  const save = useVersionedSave({
    send: (body, headers) => sendJson('PUT', EXAMPLE_PATH, body, headers),
    onSaved: () => notifySaved(strings.saved),
  })
  const { control } = form

  return (
    <SchemaForm
      form={form}
      schema={exampleSchema}
      save={save}
      onSubmit={(body) => save.mutate({ body, etag })}
    >
      <TextField control={control} name="name" label={strings.name} required />
      <TextAreaField control={control} name="notes" label={strings.notes} />
      <PhoneField control={control} name="phone" label={strings.phone} required />
      <SelectField
        control={control}
        name="depot"
        label={strings.depot}
        options={EXAMPLE_DEPOTS}
        required
      />
      <DateField control={control} name="date" label={strings.date} required />
      <TimeWindowField control={control} name="window" legend={strings.window} />
      <NumberField
        control={control}
        name="weightKg"
        label={strings.weightKg}
        unit="kg"
        decimals={2}
        required
      />
      <NumberField
        control={control}
        name="volumeLitres"
        label={strings.volumeLitres}
        unit="litres"
        decimals={2}
        required
      />
      <NumberField
        control={control}
        name="serviceMinutes"
        label={strings.serviceMinutes}
        unit="minutes"
        required
      />
      <NumberField
        control={control}
        name="priority"
        label={strings.priority}
        description={strings.priorityHint}
        min={1}
        max={5}
        required
      />
      <SaveButton />
    </SchemaForm>
  )
}
