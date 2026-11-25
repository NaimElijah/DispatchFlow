import { Anchor, type AnchorProps } from '@mantine/core'
import { createLink } from '@tanstack/react-router'
import type { ComponentPropsWithRef } from 'react'

/** Mantine's link styling on a plain anchor, for the router to drive. */
function AnchorElement(props: AnchorProps & ComponentPropsWithRef<'a'>) {
  return <Anchor {...props} />
}

/**
 * A link in running text, styled as Mantine's `Anchor` and typed as the router's `Link`: its `to`
 * and `params` are checked against the routes.
 */
export const TextLink = createLink(AnchorElement)
