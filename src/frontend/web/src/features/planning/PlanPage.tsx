import { formatLocalDate } from '@dispatchflow/formatters'
import { Title } from '@mantine/core'
import { getRouteApi } from '@tanstack/react-router'
import type { ReactNode } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { usePageTitle } from '../../components/usePageTitle.ts'
import type { PlanDate } from './planDate.ts'
import classes from './PlanPage.module.css'

// Reached by its ID rather than by importing planRoute, which imports this file.
const planRouteApi = getRouteApi('/_shell/plans/$date')

/** The date's own element in the heading: a machine-readable `<time>`, isolated for direction. */
function PlanDateText({ date, children }: { date: PlanDate; children?: ReactNode }) {
  return (
    <bdi>
      <time dateTime={date}>{children}</time>
    </bdi>
  )
}

/**
 * One delivery date's plan. Its heading names the date; below it, an area that fills the rest of
 * the screen holds the map (#174) and, later, the planning screen (#225).
 */
export function PlanPage() {
  const { date } = planRouteApi.useParams()
  const { t } = useTranslation('planning')
  const shown = formatLocalDate(date)
  usePageTitle(t('title', { date: shown }))

  return (
    <div className={classes.page}>
      <Title order={1}>
        <Trans
          t={t}
          i18nKey="heading"
          values={{ date: shown }}
          components={{ date: <PlanDateText date={date} /> }}
        />
      </Title>
      <div className={classes.mapArea} />
    </div>
  )
}
