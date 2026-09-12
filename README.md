# Voice for Android

Voice notes that stay yours. Record a thought, and the phone transcribes it
itself — no account, no subscription, no server, and nothing sent anywhere.

Android client for the [Voice](https://github.com/dotancohen/voice)
note-taking application.

## Everything happens on the phone

Nothing leaves the device unless you set up syncing, and nothing needs a
network to work:

- **Transcription runs on the phone.** Whisper runs locally, on a model you
  download once. No account, no API key, no per-minute charge, and no
  recording of yours is uploaded to anybody's service to be transcribed — not
  even in a queue, not even briefly.
- **Recordings and notes are files on the phone**, in a directory you choose,
  in ordinary formats. No cloud is between you and them.
- **Searching, tagging and playing back** are all local. The application is
  fully usable in an aeroplane, in a basement, in a foreign country with the
  data switched off, or with no SIM at all.
- **Syncing is optional and yours.** It talks to a Voice server you run, at an
  address you give it. Turn it off and the application is complete; turn it on
  and your notes reach your other devices without passing through a company.
- **Cloud storage for the audio is optional too**, and separate: notes can
  sync while the recordings stay only on the devices that made them.

The first Whisper model is a download of about a gigabyte, and that is the
only time the application needs the network for a reason of its own.

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

- **Large interface**: every text and icon half again as large, for reading
  and tapping on a horse or in a vehicle — permanently, or from a button in
  the toolbar when you need it.
- **Icon size** of its own, so the marks that say what a note holds can be
  big without shrinking what fits on the screen.
- Step to the next or previous note from inside a note; hold the button and
  that note's row appears first, so you can decide without going there.
- Play at 0.5× to 3× with the pitch unchanged, by slider or one tap.

## Careful with your notes

- **Nothing is lost quietly.** Deleting puts a note in a trash bin with its
  recordings until you recover it or remove it for good, and that is the one
  thing in the application that really destroys anything.
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
- For syncing: a Voice server you run. Not needed otherwise.

## What it asks for, and why

- **Microphone** — recording. Asked for the first time you record.
- **Notifications** — asked for when the application is first opened. It is
  what shows the recording timer and the transcription's progress and Stop
  button. Both carry on whether or not you grant it.
- **Files** — recordings are stored where you can reach them with other
  tools, rather than locked inside the application.

Judge the rest by the code rather than by a promise: the only addresses the
application contacts are the Whisper model download, the sync server you
configure, and the cloud bucket for recordings if you set one up.

## Everything else

Recording as Opus, AAC or 16 kHz WAV · choose the microphone · start
recording the moment a voice note is opened · a hierarchy of tags, with
search terms like `tag:Work` · a star on a note and a filter for the starred
ones · a search that hides itself until you want it · one to six lines of
each note in the list · a queue view showing what is being transcribed and
what is waiting · the row you just left pointed out when you return to the
list · importing a folder of existing recordings · Material Design 3 with
your own colours, and the system's light and dark themes · right-to-left
throughout, written for Hebrew first.

## More

- **[User manual](USER_MANUAL.md)** — how it is used, screen by screen and
  setting by setting.
- **[Building and hacking](DEVELOPMENT.md)** — building from source, the
  architecture, and the tests.

## License

GPL version 3.0 or above
