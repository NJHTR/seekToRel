type MacNotesGlyphProps = { size?: number }

/** A compact Notes glyph shared by the Dock, menus and Finder application grid. */
export default function MacNotesGlyph({ size = 32 }: MacNotesGlyphProps) {
  return <svg width={size} height={size} viewBox="0 0 64 64" focusable="false" aria-hidden="true">
    <rect x="9" y="5" width="46" height="54" rx="8" fill="#fffdf4" />
    <path d="M9 16h46V13a8 8 0 0 0-8-8H17a8 8 0 0 0-8 8v3Z" fill="#eab52d" />
    <path d="M17 27h30M17 35h30M17 43h21" stroke="#d4bd79" strokeWidth="3" strokeLinecap="round" />
  </svg>
}
