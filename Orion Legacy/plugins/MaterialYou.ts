import { registerPlugin } from '@capacitor/core';
import type { MaterialYouPalette, MaterialYouResult } from './AppTracker';

export interface NativeMaterialYouResponse extends MaterialYouResult {
  primary?: string;
  primaryRgb?: string;
  accent?: string;
  accentRgb?: string;
}

export interface MaterialYouPluginInterface {
  getMaterialYouPalette(): Promise<NativeMaterialYouResponse>;
}

const MaterialYou = registerPlugin<MaterialYouPluginInterface>('MaterialYou');

export default MaterialYou;
