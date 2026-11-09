import {NativeEventEmitter, NativeModules} from 'react-native';
import {PluginManager} from 'sn-plugin-lib';

export type AudioTrack = {
  name: string;
  path: string;
  kind: 'file' | 'dir';
};

export type AudioLibrary = {
  path: string;
  root: boolean;
  entries: AudioTrack[];
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
  listTracks: (dir: string) => Promise<AudioLibrary>;
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

const READ = 'plugin.permission.FILE:READ';

export async function ensureReadPermission(
  forceRequest = false,
): Promise<boolean> {
  if (!forceRequest) {
    const status = await PluginManager.hasPermission(READ);
    if (status === 1) {
      return true;
    }
  }
  const result = await PluginManager.requestPermission(
    READ,
    'Read access is required to load audio files.',
  );
  return result === 1 || result === 2;
}

export function isPermissionFailure(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return typeof error === 'string' && /READ permission|1503/.test(error);
  }
  const code = 'code' in error ? String(error.code) : '';
  const message = 'message' in error ? String(error.message) : '';
  return code === 'PERMISSION' || /READ permission|1503/.test(message);
}

export function listTracks(dir?: string | null): Promise<AudioLibrary> {
  return module().listTracks(dir ?? '');
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

export function togglePlayback(): Promise<AudioStatus> {
  return module().toggle();
}
