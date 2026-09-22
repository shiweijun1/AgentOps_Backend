import type { KnowledgeCitation } from '../types'

const CITATION_PATTERN = /\[citation:([^\]]+)\]/g

export function missingCitationMarkers(content: string, citations: KnowledgeCitation[]): string[] {
  const markers = new Set(Array.from(content.matchAll(CITATION_PATTERN), match => match[1]))
  return citations.filter(citation => citation.usedInAnswer && !markers.has(citation.chunkId))
    .map(citation => citation.chunkId)
}

export function suggestionContent(suggestion: { finalContentSnapshot: string | null; editedContent: string | null; originalContent: string }): string {
  return suggestion.finalContentSnapshot ?? suggestion.editedContent ?? suggestion.originalContent
}
