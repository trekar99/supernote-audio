/**
 * Audio player. One screen: the files in Document/Audio and the transport.
 *
 * @format
 */

import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
  DeviceEventEmitter,
  FlatList,
  Pressable,
  StatusBar,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {PluginManager} from 'sn-plugin-lib';
import {
  audioEvents,
  ensureReadPermission,
  getStatus,
  isPermissionFailure,
  listTracks,
  pause,
  play,
  resume,
  seekBy,
  stop,
  type AudioStatus,
  type AudioTrack,
} from './src/audio';

const EMPTY = 'No audio files in Document/Audio';
const EMPTY_FOLDER = 'This folder is empty';
const PERMISSION = 'Read permission is required to load audio files.';
const SEEK_MS = 15000;

const idleStatus: AudioStatus = {
  path: null,
  name: null,
  positionMs: 0,
  durationMs: 0,
  playing: false,
  loop: false,
};

function formatTime(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

function App(): React.JSX.Element {
  const [tracks, setTracks] = useState<AudioTrack[]>([]);
  const [folder, setFolder] = useState<string | null>(null);
  const folderRef = useRef<string | null>(null);
  const [status, setStatus] = useState<AudioStatus>(idleStatus);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const apply = useCallback((next: AudioStatus) => {
    setStatus(next);
    setMessage(null);
  }, []);

  const loadLibrary = useCallback(async () => {
    const [library, current] = await Promise.all([
      listTracks(folderRef.current),
      getStatus(),
    ]);
    folderRef.current = library.root ? null : library.path;
    setFolder(folderRef.current);
    setTracks(library.entries);
    setStatus(current);
    setMessage(
      library.entries.length === 0
        ? library.root
          ? EMPTY
          : EMPTY_FOLDER
        : null,
    );
  }, []);

  const load = useCallback(async () => {
    try {
      const allowed = await ensureReadPermission();
      if (!allowed) {
        setTracks([]);
        setMessage(PERMISSION);
        return;
      }
      await loadLibrary();
    } catch (error) {
      if (!isPermissionFailure(error)) {
        setTracks([]);
        setMessage('Could not load audio');
        return;
      }
      const allowed = await ensureReadPermission(true).catch(() => false);
      if (!allowed) {
        setTracks([]);
        setMessage(PERMISSION);
        return;
      }
      try {
        await loadLibrary();
      } catch {
        setTracks([]);
        setMessage(PERMISSION);
      }
    }
  }, [loadLibrary]);

  useEffect(() => {
    load();
    const subscription = audioEvents.addListener('AudioStatus', apply);
    const refresh = DeviceEventEmitter.addListener('AudioLibraryRefresh', () => {
      load();
    });
    return () => {
      subscription.remove();
      refresh.remove();
    };
  }, [apply, load]);

  useEffect(() => {
    if (!status.playing) {
      return;
    }
    let active = true;
    const timer = setInterval(() => {
      getStatus()
        .then(next => {
          if (active) {
            setStatus(next);
          }
        })
        .catch(() => {});
    }, 1000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [status.playing]);

  const run = useCallback(
    async (action: () => Promise<AudioStatus>) => {
      if (busy) {
        return;
      }
      setBusy(true);
      try {
        apply(await action());
      } catch (error) {
        setMessage(
          isPermissionFailure(error) ? PERMISSION : 'Playback failed',
        );
      } finally {
        setBusy(false);
      }
    },
    [apply, busy],
  );

  const onPlayPause = () => {
    if (status.playing) {
      run(pause);
      return;
    }
    if (status.path) {
      const atEnd =
        status.durationMs > 0 && status.positionMs >= status.durationMs - 500;
      run(atEnd ? () => play(status.path as string) : resume);
      return;
    }
    const firstFile = tracks.find(track => track.kind === 'file');
    if (firstFile) {
      run(() => play(firstFile.path));
    }
  };

  const openFolder = (path: string) => {
    folderRef.current = path;
    load();
  };

  const goUp = () => {
    const current = folderRef.current;
    if (!current) {
      return;
    }
    const slash = current.lastIndexOf('/');
    folderRef.current = slash > 0 ? current.slice(0, slash) : null;
    load();
  };

  const folderName = folder
    ? folder.slice(folder.lastIndexOf('/') + 1)
    : 'Audio';

  const controls = [
    {label: status.playing ? 'Pause' : 'Play', onPress: onPlayPause},
    {label: 'Stop', onPress: () => run(stop)},
    {label: 'Back 15s', onPress: () => run(() => seekBy(-SEEK_MS))},
    {label: 'Forward 15s', onPress: () => run(() => seekBy(SEEK_MS))},
  ];

  return (
    <View style={styles.screen}>
      <StatusBar barStyle="dark-content" backgroundColor="#ffffff" />
      <View style={styles.header}>
        <Text style={styles.title} numberOfLines={1}>
          {folderName}
        </Text>
        <View style={styles.headerActions}>
          {folder ? (
            <Pressable style={styles.close} onPress={goUp}>
              <Text style={styles.closeLabel}>Up</Text>
            </Pressable>
          ) : null}
          <Pressable
            style={styles.close}
            onPress={() => {
              PluginManager.closePluginView();
            }}>
            <Text style={styles.closeLabel}>Close</Text>
          </Pressable>
        </View>
      </View>

      <FlatList
        data={tracks}
        keyExtractor={item => item.path}
        style={styles.list}
        ListEmptyComponent={
          <Text style={styles.empty}>{message ?? EMPTY}</Text>
        }
        renderItem={({item}) => {
          const selected = item.kind === 'file' && item.path === status.path;
          return (
            <Pressable
              style={[styles.row, selected && styles.rowOn]}
              onPress={() => {
                if (item.kind === 'dir') {
                  openFolder(item.path);
                  return;
                }
                run(() => play(item.path));
              }}>
              <Text
                style={[styles.rowLabel, selected && styles.rowLabelOn]}
                numberOfLines={1}>
                {item.kind === 'dir' ? `${item.name}/` : item.name}
              </Text>
            </Pressable>
          );
        }}
      />

      <View style={styles.readout}>
        <Text style={styles.time}>
          {formatTime(status.positionMs)} / {formatTime(status.durationMs)}
        </Text>
        {message && tracks.length > 0 ? (
          <Text style={styles.error}>{message}</Text>
        ) : null}
      </View>

      <View style={styles.transport}>
        {controls.map((control, index) => (
          <Pressable
            key={control.label}
            style={[styles.button, index > 0 && styles.buttonDivider]}
            onPress={control.onPress}>
            <Text style={styles.buttonLabel}>{control.label}</Text>
          </Pressable>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#ffffff',
    paddingHorizontal: 48,
    paddingTop: 36,
    paddingBottom: 28,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: 1,
    borderBottomColor: '#000000',
    paddingBottom: 16,
  },
  title: {
    flex: 1,
    color: '#000000',
    fontSize: 28,
    fontWeight: '400',
    marginRight: 24,
  },
  headerActions: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  close: {
    paddingVertical: 8,
    paddingLeft: 24,
  },
  closeLabel: {
    color: '#000000',
    fontSize: 22,
  },
  list: {
    flex: 1,
  },
  empty: {
    color: '#000000',
    fontSize: 22,
    paddingVertical: 28,
  },
  row: {
    justifyContent: 'center',
    minHeight: 76,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#000000',
    backgroundColor: '#ffffff',
  },
  rowOn: {
    backgroundColor: '#000000',
  },
  rowLabel: {
    color: '#000000',
    fontSize: 22,
  },
  rowLabelOn: {
    color: '#ffffff',
  },
  readout: {
    alignItems: 'center',
    paddingTop: 28,
    paddingBottom: 24,
  },
  time: {
    color: '#000000',
    fontSize: 48,
    fontVariant: ['tabular-nums'],
  },
  error: {
    color: '#000000',
    fontSize: 18,
    marginTop: 12,
    textAlign: 'center',
  },
  transport: {
    flexDirection: 'row',
    borderTopWidth: 1,
    borderTopColor: '#000000',
  },
  button: {
    flex: 1,
    minHeight: 96,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonDivider: {
    borderLeftWidth: 1,
    borderLeftColor: '#000000',
  },
  buttonLabel: {
    color: '#000000',
    fontSize: 22,
  },
});

export default App;
