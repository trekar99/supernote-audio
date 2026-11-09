import {NativeEventEmitter, NativeModules} from 'react-native';
import {PluginManager} from 'sn-plugin-lib';

export type AudioTrack = {
  name: string;
  path: string;
};

export type AudioStatus = {
  path: string | null;
  name: string | null;
  positionMs: number;
  durationMs: number;
  playing: boolean;
  loop: boolean;
};

type AudioNative = {
  listTracks: () => Promise<AudioTrack[]>;
  play: (path: string) => Promise<AudioStatus>;
  pause: () => Promise<AudioStatus>;
  resume: () => Promise<AudioStatus>;
  toggle: () => Promise<AudioStatus>;
  stop: () => Promise<AudioStatus>;
  seekBy: (deltaMs: number) => Promise<AudioStatus>;
  setLoop: (enabled: boolean) => Promise<AudioStatus>;
  getStatus: () => Promise<AudioStatus>;
  addListener: (eventName: string) => void;
  removeListeners: (count: number) => void;
};

const native = NativeModules.AudioModule as AudioNative | undefined;

function module(): AudioNative {
  if (!native) {
    throw new Error('Audio player is not available on this device');
  }
  return native;
}

export const audioEvents = new NativeEventEmitter(NativeModules.AudioModule);

export async function ensureReadPermission(): Promise<boolean> {
  const permission = 'plugin.permission.FILE:READ';
  const status = await PluginManager.hasPermission(permission);
  if (status === 1) {
    return true;
  }
  const result = await PluginManager.requestPermission(
    permission,
    'Read access is required to load audio files.',
  );
  return result === 1 || result === 2;
}

export function listTracks(): Promise<AudioTrack[]> {
  return module().listTracks();
}

export function play(path: string): Promise<AudioStatus> {
  return module().play(path);
}

export function pause(): Promise<AudioStatus> {
  return module().pause();
}

export function resume(): Promise<AudioStatus> {
  return module().resume();
}

export function stop(): Promise<AudioStatus> {
  return module().stop();
}

export function seekBy(deltaMs: number): Promise<AudioStatus> {
  return module().seekBy(deltaMs);
}

export function getStatus(): Promise<AudioStatus> {
  return module().getStatus();
}
