# Stonecrusher Media branding assets

Exported from the Android vector drawables in `app/src/main/res/drawable/`.

## SVG (editable source)

| File | Description |
|------|-------------|
| [stonecrusher-icon.svg](stonecrusher-icon.svg) | Full launcher: grass background + cartoon rock |
| [stonecrusher-rock.svg](stonecrusher-rock.svg) | Rock mark only (transparent background) |
| [stonecrusher-tv-banner.svg](stonecrusher-tv-banner.svg) | Android TV leanback banner (320x180): icon left, name right |

## PNG (raster exports)

| File | Size | Typical use |
|------|------|-------------|
| `stonecrusher-icon-512.png` | 512×512 | Play Store, marketing |
| `stonecrusher-icon-192.png` | 192×192 | High-DPI launcher reference |
| `stonecrusher-icon-108.png` | 108×108 | Matches Android 108dp viewport |
| `stonecrusher-rock-512.png` | 512×512 | Rock-only on transparent background |
| `stonecrusher-tv-banner-320x180.png` | 320×180 | TV home screen banner (1x) |
| `stonecrusher-tv-banner-640x360.png` | 640×360 | TV banner preview (2x) |

Regenerate PNGs after editing SVG:

```bash
cd branding
inkscape stonecrusher-icon.svg --export-type=png --export-filename=stonecrusher-icon-512.png -w 512 -h 512
inkscape stonecrusher-icon.svg --export-type=png --export-filename=stonecrusher-icon-192.png -w 192 -h 192
inkscape stonecrusher-icon.svg --export-type=png --export-filename=stonecrusher-icon-108.png -w 108 -h 108
inkscape stonecrusher-rock.svg --export-type=png --export-filename=stonecrusher-rock-512.png -w 512 -h 512
```
