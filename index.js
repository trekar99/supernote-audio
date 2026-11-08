/**
 * @format
 */

import {AppRegistry, Image} from 'react-native';
import App from './App';
import {name as appName} from './app.json';
import {PluginManager} from 'sn-plugin-lib';
import {togglePlayback} from './src/audio';

const icon = Image.resolveAssetSource(require('./assets/icon.png')).uri;

AppRegistry.registerComponent(appName, () => App);

PluginManager.init();

PluginManager.registerButton(1, ['NOTE', 'DOC'], {
  id: 100,
  name: 'Audio',
  icon,
  showType: 1,
});

PluginManager.registerButton(1, ['NOTE', 'DOC'], {
  id: 101,
  name: 'Pause',
  icon,
  showType: 0,
});

PluginManager.registerButtonListener({
  onButtonPress: event => {
    if (event.id === 101) {
      togglePlayback().catch(() => {});
    }
  },
});
