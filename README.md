# Voice for Android

Voice notes that stay yours. Record a thought, and the phone transcribes it
itself — no sign-up, no subscription, no server, and no recording sent to
anybody's service.

Android client for the [Voice](https://github.com/dotancohen/voice)
note-taking application.

## Everything happens on the phone

Nothing leaves the device unless you pair it with another device or the account
has cloud storage, and nothing needs a network to work:

- **Transcription runs on the phone.** Whisper runs locally, on a model copied
  to the phone once. No sign-up, no API key, no per-minute charge, and no
  recording of yours is uploaded to anybody's service to be transcribed — not
  even in a queue, not even briefly.
- **Recordings are ordinary files on the phone.** Once all files access is
  granted (Android 11 and newer), they are kept in the shared
  `Recordings/Voice` folder, which stays when the application is removed. Until
  then they are kept in the application's own folder, which Android deletes
  with the application. Another folder can be chosen in Settings.
- **Searching, tagging and playing back** are all local. The application is
  fully usable in an aeroplane, in a basement, in a foreign country with the
  data switched off, or with no SIM at all.
- **Syncing is optional, and needs no server.** Your devices pair with each
  other by a code, read with the camera or opened as a link, and then sync
  directly; the phone can listen, so that your other devices can connect to it.
  Nothing passes through a company.
- **Cloud storage for the recordings is optional too**, and separate: notes can
  sync while the recordings stay only on the devices that made them. Recordings
  in the bucket can be encrypted with the account's own key.

The first Whisper model is about a gigabyte, copied from Hugging Face once, and
that is the only network use transcription needs.

## Made for speaking, not for typing

- A note is created and the recorder is already in it. There is no separate
  recorder screen and nothing to file afterwards.
- Recording carries on when you leave the application, lock the phone or take
  a call — a call takes the microphone away, so you choose whether the
  recording pauses until it ends or keeps the silence.
- A note holds as many recordings as you like, and each recording as many
  transcriptions; you say which of them stands for the note.
- Transcribe in any language Whisper knows, with the ones you use on buttons
  of their own. Hebrew and English are there from the start.
- Transcriptions are text: searchable, selectable, copyable, and correctable.

## Made for reading while moving

- **Large interface**: every text and icon one and a half times as large, for
  reading and tapping on a horse or in a vehicle — permanently, or from a
  button in the toolbar when you need it.
- **Icon size** of its own, so the marks that say what a note holds can be
  big without shrinking what fits on the screen.
- Step to the next or previous note from inside a note; hold the button and
  that note's row appears first, so you can decide without going there.
- Play at 0.5× to 3× with the pitch unchanged.

## Careful with your notes

- **Nothing is lost quietly.** Deleting puts a note in a trash bin with its
  recordings until you recover it or delete it permanently, and deleting it
  permanently is the one thing in the application that destroys a note.
- **A snapshot before every sync.** A copy of the notes database is taken
  before each sync, the newest five are kept, and any of them can be restored.
- **No last-write-wins.** Two devices that changed the same note both keep
  what they wrote; the conflict is recorded rather than resolved by whoever
  synced last.
- **Times are the clock where they happened.** A note recorded at 15:20 in
  Jerusalem still reads 15:20 after you fly to New York, and how the date is
  written is yours to choose, down to a free-form pattern.
- **Merge** several notes into the oldest of them, recordings and tags and
  all.

## Requirements

- Android 10 (API 29) or higher.
- For syncing: another device running Voice. Not needed otherwise.

## What it asks for, and why

- **Microphone** — recording, and testing a microphone under Settings →
  Recorder → Microphones. Asked for the first time either is used.
- **Camera** — reading the code another device shows when pairing. Asked for
  when the code reader opens; the code can be pasted instead.
- **Notifications** — on Android 13 and newer, asked for each time the
  application opens until it is granted. They show the recording timer, a
  transcription's progress with its Stop button, a sync operation's progress
  with its Cancel button, the listener with its Stop button, and the recording
  being played. Recording and transcription carry on whether or not you grant
  it.
- **All files access** — recordings are kept in the shared Recordings folder,
  or a folder you choose, where you can reach them with other tools, rather
  than locked inside the application. Never asked for by itself: Settings →
  Grant opens the system page.

Judge the rest by the code rather than by a promise. The application connects
to:

- Hugging Face, when a Whisper model is copied to the phone;
- the device whose pairing code it reads, and the devices of your account it is
  paired with or given by address, when you start an operation or check a
  connection; when a device is not at its known address, the phone looks for it
  on the local network;
- the account's cloud bucket, if it has one, when you press Upload or Download.

While **Listen for devices** is on, the phone also accepts connections from
devices on its local network and announces itself there (mDNS).

## Everything else

Recording as Opus (128 kb/s, or 32 kb/s for speech), AAC or 16 kHz WAV ·
choose the microphone · start recording the moment a voice note is opened · a
hierarchy of tags, with search terms like `tag:Work` · a star on a note and a
filter for the starred ones · a search that hides itself until you want it ·
one to six lines of each note in the list · a queue view showing what is being
transcribed and what is waiting · where each copy of a recording is, and a list
of issues that need your attention · the row you just left pointed out when you
return to the list · importing a folder of existing recordings · Material
Design 3 with your own colours, and the system's light and dark themes ·
right-to-left throughout, written for Hebrew first.

## More

- **[User manual](USER_MANUAL.md)** — how it is used, screen by screen and
  setting by setting.
- **[Building and hacking](DEVELOPMENT.md)** — building from source, the
  architecture, and the tests.

## License

GPL version 3.0 or above
