import { describe, it, expect } from 'vitest';
import { hexToRgbString, computeMaterialYouColors } from './materialYou';

describe('materialYou utility', () => {
  it('correctly converts hex to space-separated rgb strings', () => {
    expect(hexToRgbString('#ffffff')).toBe('255 255 255');
    expect(hexToRgbString('#000000')).toBe('0 0 0');
    expect(hexToRgbString('#6366f1')).toBe('99 102 241');
    expect(hexToRgbString('#fff')).toBe('255 255 255');
    expect(hexToRgbString('invalid')).toBe('99 102 241');
  });

  it('computes Material You night mode palette with fallback', () => {
    const theme = computeMaterialYouColors({ isNightMode: true });
    expect(theme.primary).toBe('#d0bcff');
    expect(theme.primaryRgb).toBe('208 188 255');
    expect(theme.primaryLight).toBe('#eaddff');
    expect(theme.accent).toBe('#efb8c8');
    expect(theme.accentRgb).toBe('239 184 200');
  });

  it('computes Material You light mode palette with fallback', () => {
    const theme = computeMaterialYouColors({ isNightMode: false });
    expect(theme.primary).toBe('#6750a4');
    expect(theme.primaryRgb).toBe('103 80 164');
    expect(theme.primaryLight).toBe('#eaddff');
    expect(theme.accent).toBe('#7d5260');
    expect(theme.accentRgb).toBe('125 82 96');
  });

  it('uses extracted native Monet palette when provided', () => {
    const customPalette = {
      accent1_200: '#9ecaff',
      accent1_600: '#0061a4',
      accent3_200: '#ffb4a9',
      accent3_600: '#ba1a1a',
    };
    const darkTheme = computeMaterialYouColors({ isNightMode: true, palette: customPalette });
    expect(darkTheme.primary).toBe('#9ecaff');
    expect(darkTheme.accent).toBe('#ffb4a9');

    const lightTheme = computeMaterialYouColors({ isNightMode: false, palette: customPalette });
    expect(lightTheme.primary).toBe('#0061a4');
    expect(lightTheme.accent).toBe('#ba1a1a');
  });
});
