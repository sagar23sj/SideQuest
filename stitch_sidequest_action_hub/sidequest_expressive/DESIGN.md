---
name: SideQuest Expressive
colors:
  surface: '#f4faff'
  surface-dim: '#c0dfee'
  surface-bright: '#f4faff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#e6f6ff'
  surface-container: '#d9f2ff'
  surface-container-high: '#ceedfd'
  surface-container-highest: '#c9e7f7'
  on-surface: '#001f2a'
  on-surface-variant: '#56423c'
  inverse-surface: '#163440'
  inverse-on-surface: '#e0f4ff'
  outline: '#89726b'
  outline-variant: '#ddc0b8'
  surface-tint: '#9f4122'
  primary: '#9f4122'
  on-primary: '#ffffff'
  primary-container: '#ff8a65'
  on-primary-container: '#752305'
  inverse-primary: '#ffb59e'
  secondary: '#6d4ea2'
  on-secondary: '#ffffff'
  secondary-container: '#c5a3ff'
  on-secondary-container: '#533487'
  tertiary: '#006a63'
  on-tertiary: '#ffffff'
  tertiary-container: '#53bbb1'
  on-tertiary-container: '#004842'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffdbd0'
  primary-fixed-dim: '#ffb59e'
  on-primary-fixed: '#3a0b00'
  on-primary-fixed-variant: '#7f2a0d'
  secondary-fixed: '#ebdcff'
  secondary-fixed-dim: '#d4bbff'
  on-secondary-fixed: '#270058'
  on-secondary-fixed-variant: '#543589'
  tertiary-fixed: '#8ef4e9'
  tertiary-fixed-dim: '#71d7cd'
  on-tertiary-fixed: '#00201d'
  on-tertiary-fixed-variant: '#00504a'
  background: '#f4faff'
  on-background: '#001f2a'
  surface-variant: '#c9e7f7'
typography:
  display-lg:
    fontFamily: Outfit
    fontSize: 57px
    fontWeight: '700'
    lineHeight: 64px
    letterSpacing: -0.25px
  headline-lg:
    fontFamily: Outfit
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
  headline-lg-mobile:
    fontFamily: Outfit
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
  title-lg:
    fontFamily: Outfit
    fontSize: 22px
    fontWeight: '500'
    lineHeight: 28px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0.5px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.25px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.1px
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  gutter: 16px
  margin-mobile: 20px
  margin-tablet: 32px
---

## Brand & Style

The brand personality is defined as the "supportive friend"—warm, encouraging, and playful yet deeply focused on helping the user succeed. It avoids the sterile, clinical feel of traditional productivity apps in favor of an expressive, high-energy aesthetic that celebrates small wins.

The visual style follows the **Material 3 Expressive** movement. It utilizes exaggerated roundedness, vibrant tonal palettes, and generous whitespace to create a UI that feels soft to the touch and emotionally resonant. Motion is critical; interactions should feel bouncy and organic, using gentle spring physics to provide tactile feedback that reinforces the app's encouraging tone.

## Colors

The palette is rooted in warmth. The **Primary** color is a soft Coral (#FF8A65), used for main actions and focus states. **Secondary** (Violet) and **Tertiary** (Teal) are used for categorizing "buckets" of content, ensuring that different life areas feel distinct and vibrant.

This design system utilizes Material 3 Tonal Palettes. Every key color generates a range of 13 tones (from 0 to 100). In Light Mode, use Tone 40 for main elements and Tone 90 for containers. In Dark Mode, use Tone 80 for main elements and Tone 30 for containers. This ensures accessible contrast ratios while maintaining the "glow" of the expressive palette.

## Typography

The typography strategy pairs the geometric, friendly curves of **Outfit** for headlines with the industrial-strength legibility of **Inter** for body text.

- **Headlines:** Use Outfit for all display and headline levels. It should feel "bouncy" and approachable. Letter spacing is slightly tightened on larger sizes to maintain impact.
- **Body:** Use Inter for all functional text, descriptions, and task inputs. It provides a grounded, neutral counterpoint to the expressive headlines.
- **Tone of Voice:** Copy should always be active and supportive. Use "Add to my actions" instead of "Submit" and "Make it happen" instead of "Done."

## Layout & Spacing

This design system uses a **Fluid Grid** model with a base-4 vertical rhythm. On mobile devices, a 4-column grid is used with 20px side margins to allow elements breathing room. 

Spacing is intentional and generous. Avoid crowding elements; the "Expressive" nature of the system relies on whitespace to prevent the vibrant colors from feeling overwhelming. Containers should use `lg` (24px) padding internally to ensure content feels "tucked in" and comfortable.

## Elevation & Depth

Depth is achieved through **Tonal Layers** rather than heavy shadows. Surfaces use the Material 3 "Surface Container" logic:
- **Level 0 (Background):** The lowest layer, usually a soft tint of the neutral color.
- **Level 1 (Cards):** Slightly elevated using a subtle, diffused shadow (Blur: 8px, Opacity: 4%) and a lighter tonal fill.
- **Level 2 (Active States/Modals):** More pronounced shadow and a border-stroke that matches the primary color at 10% opacity.

Avoid harsh black shadows. Instead, use "Ambient Shadows" that are tinted with the primary or secondary color of the element they support to maintain a soft, glowing appearance.

## Shapes

The shape language is the defining characteristic of this design system. It uses a **Pill-shaped (3)** logic.
- **Main Containers/Cards:** Use a 28dp corner radius to create a "squishy" and friendly look.
- **Buttons:** Fully pill-shaped (rounded-full) to maximize tap-target friendliness.
- **Input Fields:** Use 16dp radius to balance structure with the overall soft theme.
- **Selection States:** Use a "blob" or "organic" shape hint when an item is long-pressed or selected.

## Components

- **Buttons:** Large, pill-shaped, and high-contrast. The primary "Action" button should use a subtle gradient of the primary color to appear clickable and "juicy."
- **Collection Chips:** These are oversized (48px height) and include small circular cover art or icons on the left. They use the secondary and tertiary tonal palettes.
- **Rich Task Cards:** Feature a 28dp radius, a subtle 1px inner stroke for definition, and large "Expressive" typography for task titles. Images should have a slight zoom-on-hover (or press) effect.
- **Satisfying Progress:** Use thick, rounded-cap progress bars (8dp-12dp height). Completion should trigger a haptic pulse and a brief, playful sparkle animation.
- **Inputs:** Floating labels are avoided in favor of clear, external "Outfit" labels. The input area itself is a large, softly tinted container with 24dp padding.
- **Navigation:** Use a centered, floating Bottom App Bar with a large, elevated FAB (Floating Action Button) for adding new "SideQuests."