import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.amm.mahjong3d',
  appName: 'Mahjong3D',
  webDir: 'dist',
  android: {
    allowMixedContent: false,
    backgroundColor: '#101820',
  },
};

export default config;
