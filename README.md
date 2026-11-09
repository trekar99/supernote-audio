# Audio

A Supernote plugin that plays local audio while you read a note or a document.

Put `.mp3` and `.wav` files in `Document/Audio`. Open a note or a PDF, tap **Audio** in the toolbar, and pick a file. Closing the player returns to the page and leaves the audio running. Sleeping the screen pauses it and keeps the position. Play resumes from there.

The player asks for read permission the first time. It only lists files inside `Document/Audio`.

## Install

Copy `SnAudio.snplg` to `MyStyle` on the device. Then open Settings → Apps → Plugins → Add Plugin and select it.

## Build

The device package is built on Linux with the Supernote plugin SDK, Node 22, JDK 21, and Android SDK 35:

```sh
./buildPlugin.sh
```

The package is `build/outputs/SnAudio.snplg`.
