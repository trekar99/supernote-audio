/**
 * @format
 */

import {AppRegistry, DeviceEventEmitter, Image} from 'react-native';
import App from './App';
import {name as appName} from './app.json';
import {PluginManager} from 'sn-plugin-lib';
import {togglePlayback} from './src/audio';

const ACTION_DOWN = 0;
const ACTION_UP = 1;
const ACTION_MOVE = 2;
const ACTION_CANCEL = 3;
const ACTION_POINTER_DOWN = 5;
const ACTION_POINTER_UP = 6;
const MAX_MOVE = 80;
const TAP_MS = 400;
const BETWEEN_MS = 500;
const PEN_IDLE_MS = 400;

let armed = null;
let firstTapAt = 0;
let penAt = 0;

function onThreeFingerTap(msg) {
  const action = (msg?.action ?? 0) & 0xff;
  const pointers = Array.isArray(msg?.pointers) ? msg.pointers : [];
  const pen = msg?.toolType === 2 || pointers.some(pointer => pointer.toolType === 2);
  if (pen) {
    armed = null;
    firstTapAt = 0;
    penAt = Date.now();
    return;
  }
  if (Date.now() - penAt < PEN_IDLE_MS) {
    return;
  }
  const count = msg?.pointerCount || pointers.length;
  if ((action === ACTION_DOWN || action === ACTION_POINTER_DOWN) && count >= 3) {
    armed = {x: msg.x, y: msg.y, down: msg.downTime || msg.eventTime};
    return;
  }
  if (!armed) {
    return;
  }
  if (action === ACTION_MOVE) {
    const dx = msg.x - armed.x;
    const dy = msg.y - armed.y;
    if (dx * dx + dy * dy > MAX_MOVE * MAX_MOVE) {
      armed = null;
      firstTapAt = 0;
    }
    return;
  }
  if (action === ACTION_CANCEL) {
    armed = null;
    firstTapAt = 0;
    return;
  }
  if (action !== ACTION_UP && action !== ACTION_POINTER_UP) {
    return;
  }
  const dt = (msg.eventTime || 0) - (armed.down || 0);
  armed = null;
  if (dt < 0 || dt > TAP_MS) {
    firstTapAt = 0;
    return;
  }
  const now = Date.now();
  if (firstTapAt !== 0 && now - firstTapAt <= BETWEEN_MS) {
    firstTapAt = 0;
    togglePlayback().catch(() => {});
    return;
  }
  firstTapAt = now;
}

const icon = Image.resolveAssetSource(require('./assets/icon.png')).uri;

AppRegistry.registerComponent(appName, () => App);

PluginManager.init();

PluginManager.registerButton(1, ['NOTE', 'DOC'], {
  id: 100,
  name: 'Audio',
  icon,
  showType: 1,
});

PluginManager.registerMotionListener(2, {
  onMsg: onThreeFingerTap,
});

PluginManager.registerPluginLifeListener({
  onMsg(msg) {
    if (msg?.state === 2) {
      DeviceEventEmitter.emit('AudioLibraryRefresh');
    }
  },
});
