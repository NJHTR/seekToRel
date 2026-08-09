type Wallpaper = { name: string; file: string }

export const wallpapers: Wallpaper[] = [
  { name: 'Catalina', file: 'catalina.jpg' },
  { name: 'Catalina Day', file: 'catalina_day.jpg' },
  { name: 'Ventura', file: 'ventura.jpg' },
  { name: 'Monterey', file: 'monterey.jpg' },
  { name: 'Big Sur', file: 'bigsur.jpg' },
  { name: 'Big Sur Graphic', file: 'bigsurgraphic.jpg' },
  { name: 'Mojave', file: 'mojave.jpg' },
  { name: 'The Desert', file: 'thedesert.jpg' },
  { name: 'Dome', file: 'dome.jpg' },
  { name: 'Peak', file: 'peak.jpg' },
  { name: 'Iridescence', file: 'iridescence.jpg' },
  { name: 'Lake', file: 'lake.jpg' },
  { name: 'Solar Grad', file: 'solargrad.jpg' }
]

export function wallpaperUrl(file: string) {
  return `/macos-wallpapers/${file}`
}

export default function WallpaperGallery({ selected, onSelect }: { selected: Wallpaper; onSelect: (wallpaper: Wallpaper) => void }) {
  return <div className="wallpaper-gallery">
    <section className="wallpaper-preview">
      <img src={wallpaperUrl(selected.file)} alt={`${selected.name} wallpaper preview`} />
      <div><strong>{selected.name}</strong><span>Dynamic Wallpaper</span></div>
      <label><input type="checkbox" /> Change dark/light mode as wallpapers change</label>
    </section>
    <section className="wallpaper-library">
      <h2>Dynamic Wallpapers</h2>
      <div className="wallpaper-library-grid">{wallpapers.map((wallpaper) => <button key={wallpaper.file} className={selected.file === wallpaper.file ? 'selected' : ''} onClick={() => onSelect(wallpaper)}>
        <img src={wallpaperUrl(wallpaper.file)} alt="" /><span>{wallpaper.name}</span>
      </button>)}</div>
    </section>
  </div>
}
