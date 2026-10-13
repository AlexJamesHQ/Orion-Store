import { Capacitor } from '@capacitor/core';
import AppTracker, { MaterialYouPalette, MaterialYouResult } from '../plugins/AppTracker';
import MaterialYou from '../plugins/MaterialYou';

export interface ComputedM3Theme {
  primary: string;
  primaryRgb: string;
  primaryLight: string;
  accent: string;
  accentRgb: string;
}

let cachedNativeResult: MaterialYouResult | null = null;

export const hexToRgbString = (hex?: string, fallback = '99 102 241'): string => {
  if (!hex || typeof hex !== 'string') return fallback;
  const clean = hex.replace('#', '').trim();
  if (clean.length === 3) {
    const c0 = clean.charAt(0);
    const c1 = clean.charAt(1);
    const c2 = clean.charAt(2);
    const r = parseInt(c0 + c0, 16);
    const g = parseInt(c1 + c1, 16);
    const b = parseInt(c2 + c2, 16);
    return `${r} ${g} ${b}`;
  }
  if (clean.length >= 6) {
    const r = parseInt(clean.substring(0, 2), 16);
    const g = parseInt(clean.substring(2, 4), 16);
    const b = parseInt(clean.substring(4, 6), 16);
    if (isNaN(r) || isNaN(g) || isNaN(b)) return fallback;
    return `${r} ${g} ${b}`;
  }
  return fallback;
};

export const fetchNativeMaterialYouPalette = async (): Promise<MaterialYouResult> => {
  if (!Capacitor.isNativePlatform()) {
    return { supported: false };
  }
  try {
    const res = await MaterialYou.getMaterialYouPalette();
    if (res && (res.supported || res.palette)) {
      cachedNativeResult = res;
      return res;
    }
  } catch (err) {
    // Fallback to AppTracker plugin
  }
  try {
    const res = await AppTracker.getMaterialYouPalette();
    if (res && (res.supported || res.palette)) {
      cachedNativeResult = res;
      return res;
    }
  } catch (err) {
    return { supported: false };
  }
  return { supported: false };
};

export const getCachedNativePalette = (): MaterialYouResult | null => cachedNativeResult;

export const computeMaterialYouColors = (options: {
  isNightMode: boolean;
  palette?: MaterialYouPalette | null;
}): ComputedM3Theme => {
  const { isNightMode, palette } = options;

  if (isNightMode) {
    // In Dark / Night / OLED mode: use luminance-80 tones (bright on dark backgrounds)
    // Use accent1_200 for max vibrancy on dark. If seed is available, accent1_300 is even punchier.
    const primary = palette?.accent1_300 || palette?.accent1_200 || palette?.accent1_500 || '#d0bcff';
    const primaryLight = palette?.accent1_100 || palette?.accent1_200 || '#eaddff';
    const accent = palette?.accent3_200 || palette?.accent2_200 || palette?.accent3_500 || palette?.accent1_200 || '#efb8c8';

    return {
      primary,
      primaryRgb: hexToRgbString(primary, '208 188 255'),
      primaryLight,
      accent,
      accentRgb: hexToRgbString(accent, '239 184 200'),
    };
  } else {
    // In Light mode: use luminance-40 tones (deep, rich contrast on white/light)
    const primary = palette?.accent1_600 || palette?.accent1_500 || palette?.accent1_800 || '#6750a4';
    const primaryLight = palette?.accent1_100 || palette?.accent1_200 || '#eaddff';
    const accent = palette?.accent3_600 || palette?.accent3_500 || palette?.accent2_500 || palette?.accent1_600 || '#7d5260';

    return {
      primary,
      primaryRgb: hexToRgbString(primary, '103 80 164'),
      primaryLight,
      accent,
      accentRgb: hexToRgbString(accent, '125 82 96'),
    };
  }
};


const MATERIAL_YOU_VAR_NAMES = [
  '--color-primary',
  '--color-primary-rgb',
  '--color-primary-light',
  '--color-accent',
  '--color-accent-rgb',
];

const MATERIAL_YOU_STYLE_ID = 'material-you-dynamic-vars';

/**
 * Generate comprehensive CSS overrides for ALL Tailwind primary utility classes.
 * 
 * WHY: The compiled CSS contains duplicate Tailwind rules — one set uses
 * var(--color-primary-rgb) and a SECOND set uses hardcoded rgb(99 102 241).
 * The second set wins by cascade order, making CSS variable changes useless.
 * We must override every class directly with !important.
 */
function generateMaterialYouCSS(rgb: string, primaryHex: string, primaryLightHex: string, accentHex: string, accentRgb: string): string {
  const P = 'html.material-you';

  // Background opacities used in the app
  const bgOpacities: [string, string][] = [
    ['', '1'], ['\\/0', '0'], ['\\/5', '.05'], ['\\/10', '.1'],
    ['\\/15', '.15'], ['\\/20', '.2'], ['\\/40', '.4'],
    ['\\/50', '.5'], ['\\/70', '.7'],
    ['\\/\\[0\\.04\\]', '.04'], ['\\/\\[0\\.06\\]', '.06'], ['\\/\\[0\\.08\\]', '.08'],
  ];

  // Text opacities
  const textOpacities: [string, string][] = [
    ['', '1'], ['\\/30', '.3'], ['\\/40', '.4'], ['\\/50', '.5'],
    ['\\/60', '.6'], ['\\/70', '.7'], ['\\/80', '.8'],
  ];

  // Border opacities
  const borderOpacities: [string, string][] = [
    ['', '1'], ['\\/15', '.15'], ['\\/20', '.2'], ['\\/25', '.25'],
    ['\\/30', '.3'], ['\\/35', '.35'], ['\\/40', '.4'],
    ['\\/50', '.5'], ['\\/60', '.6'],
  ];

  // Shadow opacities
  const shadowOpacities: [string, string][] = [
    ['\\/5', '.05'], ['\\/10', '.1'], ['\\/20', '.2'],
    ['\\/25', '.25'], ['\\/30', '.3'], ['\\/40', '.4'],
  ];

  // Ring opacities
  const ringOpacities: [string, string][] = [
    ['\\/15', '.15'], ['\\/35', '.35'], ['\\/40', '.4'], ['\\/45', '.45'],
    ['\\/10', '.1'], ['\\/50', '.5'],
  ];

  // Gradient from opacities
  const fromOpacities: [string, string][] = [
    ['', '1'], ['\\/10', '.1'], ['\\/15', '.15'], ['\\/20', '.2'],
    ['\\/25', '.25'], ['\\/30', '.3'],
  ];

  const lines: string[] = [];

  // Also set CSS vars for raw CSS usage (water-wave, m3-slider, etc.)
  lines.push(`:root { --color-primary: ${primaryHex} !important; --color-primary-rgb: ${rgb} !important; --color-primary-light: ${primaryLightHex} !important; --color-accent: ${accentHex} !important; --color-accent-rgb: ${accentRgb} !important; }`);

  // --- Background ---
  for (const [suffix, alpha] of bgOpacities) {
    lines.push(`${P} .bg-primary${suffix} { background-color: rgb(${rgb} / ${alpha}) !important; }`);
  }

  // --- Text ---
  for (const [suffix, alpha] of textOpacities) {
    lines.push(`${P} .text-primary${suffix} { color: rgb(${rgb} / ${alpha}) !important; }`);
  }

  // --- Border ---
  for (const [suffix, alpha] of borderOpacities) {
    lines.push(`${P} .border-primary${suffix} { border-color: rgb(${rgb} / ${alpha}) !important; }`);
  }

  // --- Shadow ---
  for (const [suffix, alpha] of shadowOpacities) {
    lines.push(`${P} .shadow-primary${suffix} { --tw-shadow-color: rgb(${rgb} / ${alpha}) !important; }`);
  }

  // --- Ring ---
  for (const [suffix, alpha] of ringOpacities) {
    lines.push(`${P} .ring-primary${suffix} { --tw-ring-color: rgb(${rgb} / ${alpha}) !important; }`);
  }

  // --- Gradients ---
  for (const [suffix, alpha] of fromOpacities) {
    lines.push(`${P} .from-primary${suffix} { --tw-gradient-from: rgb(${rgb} / ${alpha}) !important; }`);
  }
  lines.push(`${P} .to-primary { --tw-gradient-to: rgb(${rgb}) !important; }`);
  lines.push(`${P} .to-primary\\/10 { --tw-gradient-to: rgb(${rgb} / .1) !important; }`);

  // --- Decoration ---
  lines.push(`${P} .decoration-primary\\/30 { text-decoration-color: rgb(${rgb} / .3) !important; }`);

  // --- Outline ---
  lines.push(`${P} .outline-primary { outline-color: rgb(${rgb}) !important; }`);

  // --- Selection ---
  lines.push(`${P} .selection\\:bg-primary\\/30 ::selection, ${P} .selection\\:bg-primary\\/30::selection { background-color: rgb(${rgb} / .3) !important; }`);

  // --- Hover variants ---
  lines.push(`${P} .hover\\:bg-primary:hover { background-color: rgb(${rgb}) !important; }`);
  lines.push(`${P} .hover\\:bg-primary\\/5:hover { background-color: rgb(${rgb} / .05) !important; }`);
  lines.push(`${P} .hover\\:bg-primary\\/15:hover { background-color: rgb(${rgb} / .15) !important; }`);
  lines.push(`${P} .hover\\:bg-primary\\/20:hover { background-color: rgb(${rgb} / .2) !important; }`);
  lines.push(`${P} .hover\\:bg-primary\\/90:hover { background-color: rgb(${rgb} / .9) !important; }`);
  lines.push(`${P} .hover\\:bg-primary\\/\\[0\\.14\\]:hover { background-color: rgb(${rgb} / .14) !important; }`);
  lines.push(`${P} .hover\\:text-primary:hover { color: rgb(${rgb}) !important; }`);
  lines.push(`${P} .hover\\:text-primary\\/80:hover { color: rgb(${rgb} / .8) !important; }`);
  lines.push(`${P} .hover\\:border-primary:hover { border-color: rgb(${rgb}) !important; }`);
  lines.push(`${P} .hover\\:border-primary\\/20:hover { border-color: rgb(${rgb} / .2) !important; }`);
  lines.push(`${P} .hover\\:border-primary\\/30:hover { border-color: rgb(${rgb} / .3) !important; }`);
  lines.push(`${P} .hover\\:border-primary\\/35:hover { border-color: rgb(${rgb} / .35) !important; }`);
  lines.push(`${P} .hover\\:border-primary\\/40:hover { border-color: rgb(${rgb} / .4) !important; }`);
  lines.push(`${P} .hover\\:border-primary\\/50:hover { border-color: rgb(${rgb} / .5) !important; }`);
  lines.push(`${P} .hover\\:border-primary\\/70:hover { border-color: rgb(${rgb} / .7) !important; }`);
  lines.push(`${P} .hover\\:shadow-primary\\/10:hover { --tw-shadow-color: rgb(${rgb} / .1) !important; }`);
  lines.push(`${P} .hover\\:shadow-primary\\/40:hover { --tw-shadow-color: rgb(${rgb} / .4) !important; }`);

  // --- Focus variants ---
  lines.push(`${P} .focus\\:border-primary:focus { border-color: rgb(${rgb}) !important; }`);
  lines.push(`${P} .focus\\:border-primary\\/60:focus { border-color: rgb(${rgb} / .6) !important; }`);
  lines.push(`${P} .focus\\:border-primary\\/70:focus { border-color: rgb(${rgb} / .7) !important; }`);
  lines.push(`${P} .focus\\:ring-primary\\/10:focus { --tw-ring-color: rgb(${rgb} / .1) !important; }`);
  lines.push(`${P} .focus\\:ring-primary\\/50:focus { --tw-ring-color: rgb(${rgb} / .5) !important; }`);
  lines.push(`${P} .focus-visible\\:outline-primary:focus-visible { outline-color: rgb(${rgb}) !important; }`);

  // --- Focus-within variants ---
  lines.push(`${P} .focus-within\\:border-primary\\/50:focus-within { border-color: rgb(${rgb} / .5) !important; }`);
  lines.push(`${P} .focus-within\\:ring-primary\\/10:focus-within { --tw-ring-color: rgb(${rgb} / .1) !important; }`);

  // --- Group hover ---
  lines.push(`${P} .group:hover .group-hover\\:bg-primary\\/10 { background-color: rgb(${rgb} / .1) !important; }`);
  lines.push(`${P} .group:hover .group-hover\\:bg-primary\\/20 { background-color: rgb(${rgb} / .2) !important; }`);
  lines.push(`${P} .group:hover .group-hover\\:text-primary { color: rgb(${rgb}) !important; }`);
  lines.push(`${P} .group:hover .group-hover\\:shadow-primary\\/20 { --tw-shadow-color: rgb(${rgb} / .2) !important; }`);
  lines.push(`${P} .group:hover .group-hover\\:shadow-primary\\/35 { --tw-shadow-color: rgb(${rgb} / .35) !important; }`);
  lines.push(`${P} .group:hover .group-hover\\:ring-primary\\/40 { --tw-ring-color: rgb(${rgb} / .4) !important; }`);

  // --- Dark variant ---
  lines.push(`${P} .dark\\:bg-primary\\/15:is(.dark *) { background-color: rgb(${rgb} / .15) !important; }`);

  return lines.join('\n');
}

export const applyMaterialYouTheme = (options: {
  isNightMode: boolean;
  nativeResult?: MaterialYouResult | null;
}): void => {
  if (typeof document === 'undefined') return;
  const { isNightMode, nativeResult } = options;

  // Strategy A: Java pre-computes primary/primaryRgb/accent/accentRgb at top level
  let primary = nativeResult?.primary;
  let primaryRgb = nativeResult?.primaryRgb;
  let accent = nativeResult?.accent;
  let accentRgb = nativeResult?.accentRgb;

  // Strategy B: Compute from palette if top-level fields are missing
  if (!primary || !primaryRgb) {
    const palette = nativeResult?.palette;
    const computed = computeMaterialYouColors({ isNightMode, palette });
    primary = primary || computed.primary;
    primaryRgb = primaryRgb || computed.primaryRgb;
    accent = accent || computed.accent;
    accentRgb = accentRgb || computed.accentRgb;
  }

  const primaryLight = nativeResult?.palette?.accent1_100 || nativeResult?.palette?.accent1_200 || '#bfdbfe';
  const finalPrimary = primary || '#3b82f6';
  const finalPrimaryRgb = primaryRgb || '59 130 246';
  const finalAccent = accent || '#06b6d4';
  const finalAccentRgb = accentRgb || '6 182 212';

  // Inject comprehensive CSS overrides for ALL primary utility classes
  let styleEl = document.getElementById(MATERIAL_YOU_STYLE_ID) as HTMLStyleElement | null;
  if (!styleEl) {
    styleEl = document.createElement('style');
    styleEl.id = MATERIAL_YOU_STYLE_ID;
    document.head.appendChild(styleEl);
  }

  styleEl.textContent = generateMaterialYouCSS(
    finalPrimaryRgb, finalPrimary, primaryLight, finalAccent, finalAccentRgb
  );

  console.log('[MaterialYou] Applied comprehensive overrides:', { primary: finalPrimary, rgb: finalPrimaryRgb });
};

export const removeMaterialYouTheme = (): void => {
  if (typeof document === 'undefined') return;
  const styleEl = document.getElementById(MATERIAL_YOU_STYLE_ID);
  if (styleEl) {
    styleEl.remove();
  }
  const root = document.documentElement;
  MATERIAL_YOU_VAR_NAMES.forEach((varName) => {
    root.style.removeProperty(varName);
  });
};
