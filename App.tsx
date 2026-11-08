/**
 * Audio player. One screen: the files in Document/Audio and the transport.
 *
 * @format
 */

import React, {useCallback, useEffect, useState} from 'react';
import {
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
  listTracks,
  pause,
  play,
  resume,
  seekBy,
  setLoop,
  stop,
  type AudioStatus,
  type AudioTrack,
} from './src/audio';

const EMPTY = 'No audio files in Document/Audio';
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
  const [status, setStatus] = useState<AudioStatus>(idleStatus);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const apply = useCallback((next: AudioStatus) => {
    setStatus(next);
    setMessage(null);
  }, []);

  const load = useCallback(async () => {
    try {
      const allowed = await ensureReadPermission();
      if (!allowed) {
        setTracks([]);
        setMessage(PERMISSION);
        return;
      }
      const [files, current] = await Promise.all([listTracks(), getStatus()]);
      setTracks(files);
      setStatus(current);
      setMessage(files.length === 0 ? EMPTY : null);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Could not load audio');
    }
  }, []);

  useEffect(() => {
    load();
    const subscription = audioEvents.addListener('AudioStatus', apply);
    return () => subscription.remove();
  }, [apply, load]);

  const run = useCallback(
    async (action: () => Promise<AudioStatus>) => {
      if (busy) {
        return;
      }
      setBusy(true);
      try {
        apply(await action());
      } catch (error) {
        setMessage(error instanceof Error ? error.message : 'Playback failed');
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
    if (tracks[0]) {
      run(() => play(tracks[0].path));
    }
  };

  return (
    <View style={styles.screen}>
      <StatusBar barStyle="dark-content" backgroundColor="#ffffff" />
      <View style={styles.header}>
        <Text style={styles.title}>Audio</Text>
        <Pressable
          style={styles.close}
          onPress={() => {
            PluginManager.closePluginView();
          }}>
          <Text style={styles.closeLabel}>Close</Text>
        </Pressable>
      </View>

      <FlatList
        data={tracks}
        keyExtractor={item => item.path}
        style={styles.list}
        ListEmptyComponent={
          <Text style={styles.empty}>{message ?? EMPTY}</Text>
        }
        renderItem={({item}) => {
          const selected = item.path === status.path;
          return (
            <Pressable
              style={[styles.row, selected && styles.rowSelected]}
              onPress={() => run(() => play(item.path))}>
              <Text style={[styles.rowLabel, selected && styles.rowLabelSelected]}>
                {item.name}
              </Text>
            </Pressable>
          );
        }}
      />

      <Text style={styles.time}>
        {formatTime(status.positionMs)} / {formatTime(status.durationMs)}
      </Text>
      {message && tracks.length > 0 ? (
        <Text style={styles.error}>{message}</Text>
      ) : null}

      <View style={styles.controls}>
        <Pressable style={styles.button} onPress={onPlayPause}>
          <Text style={styles.buttonLabel}>{status.playing ? 'Pause' : 'Play'}</Text>
        </Pressable>
        <Pressable style={styles.button} onPress={() => run(stop)}>
          <Text style={styles.buttonLabel}>Stop</Text>
        </Pressable>
        <Pressable style={styles.button} onPress={() => run(() => seekBy(-SEEK_MS))}>
          <Text style={styles.buttonLabel}>Back 15s</Text>
        </Pressable>
        <Pressable style={styles.button} onPress={() => run(() => seekBy(SEEK_MS))}>
          <Text style={styles.buttonLabel}>Forward 15s</Text>
        </Pressable>
        <Pressable
          style={styles.button}
          onPress={() => run(() => setLoop(!status.loop))}>
          <Text style={styles.buttonLabel}>{status.loop ? 'Loop on' : 'Loop off'}</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#ffffff',
    paddingHorizontal: 32,
    paddingTop: 28,
    paddingBottom: 32,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 24,
  },
  title: {
    color: '#000000',
    fontSize: 36,
    fontWeight: '600',
  },
  close: {
    borderWidth: 1,
    borderColor: '#000000',
    paddingHorizontal: 28,
    paddingVertical: 14,
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
    fontSize: 24,
    paddingVertical: 24,
  },
  row: {
    borderWidth: 1,
    borderColor: '#000000',
    paddingHorizontal: 20,
    paddingVertical: 22,
    marginBottom: 12,
    backgroundColor: '#ffffff',
  },
  rowSelected: {
    backgroundColor: '#000000',
  },
  rowLabel: {
    color: '#000000',
    fontSize: 24,
  },
  rowLabelSelected: {
    color: '#ffffff',
  },
  time: {
    color: '#000000',
    fontSize: 32,
    marginTop: 8,
    marginBottom: 16,
  },
  error: {
    color: '#000000',
    fontSize: 18,
    marginBottom: 12,
  },
  controls: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  button: {
    borderWidth: 1,
    borderColor: '#000000',
    backgroundColor: '#ffffff',
    paddingHorizontal: 22,
    paddingVertical: 18,
    marginRight: 12,
    marginBottom: 12,
    minHeight: 72,
    justifyContent: 'center',
  },
  buttonLabel: {
    color: '#000000',
    fontSize: 22,
  },
});

export default App;
