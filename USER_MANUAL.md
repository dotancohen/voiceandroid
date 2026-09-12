# Voice for Android — user manual

How the application is used: what each screen holds, what the settings do,
and why things are arranged as they are. For what the application is, see the
[README](README.md); for building it from source,
[DEVELOPMENT.md](DEVELOPMENT.md).

## Contents

- [Setting up syncing](#setting-up-syncing)
- [The Notes list](#the-notes-list)
- [Inside a note](#inside-a-note)
- [The main recording and the main transcription](#the-main-recording-and-the-main-transcription)
- [What a transcription says about itself](#what-a-transcription-says-about-itself)
  - [How it ran](#how-it-ran)
  - [Flags](#flags)
- [Sharing](#sharing)
- [The trash bin](#the-trash-bin)
- [Reading and moving about](#reading-and-moving-about)
  - [Time format](#time-format)
  - [Playback](#playback)
  - [Interface size](#interface-size)
  - [Icon size](#icon-size)
- [Recording](#recording)
  - [Microphones](#microphones)
- [Long recordings](#long-recordings)
- [On-device transcription](#on-device-transcription)
- [While a recording plays](#while-a-recording-plays)
- [Calculating missing data](#calculating-missing-data)

## Setting up syncing

Syncing is optional. Leave it alone and the application is complete on this
phone: notes, recordings, tags, search and transcription all work with no
account and no network. Set it up and the same notes reach your other
devices, through a Voice server you run yourself.

In **Settings**:

- **Server URL** — the address of your Voice sync server, e.g.
  `https://myserver.com:8384`
- **Server Peer ID** — the 32-character hex device ID of that server
- **Device Name** — what this phone is called among your devices
- **Device ID** — generated for you, or paste an existing one to take over
  the identity of another installation

**Sync now** runs a sync there and then. Recordings can be kept out of the
sync and left on the devices that made them; the notes and their text still
travel.

## The Notes list

Each row is one Note: its Tags in their own colours, the date it was made,
its recordings as small buttons (🔊1, 🔊2 …), and the opening lines of its
text and of its main Transcription.

**The subsection.** The chevron at the end of a row opens a small section
under it, on every Note whether or not it holds recordings. There are the
buttons for that one Note: **Tags**, which opens its Tags screen, and
**Delete**, which asks and then puts it in the trash bin. Tapping a 🔊 button
opens that recording's player in the same place.

**Tag colours.** Every Tag has a colour of its own without anybody choosing
one: the first six characters of the MD5 hash of its name, read as a colour.
The same Tag is therefore the same colour on the phone, on the desktop, and
on any device you add later, with nothing stored and nothing synced. To
choose a colour instead, open Settings → Manage Tags, press a Tag's menu and
pick **Colour…**; the choice is stored with your settings and syncs. *Use the
calculated colour* puts it back. The label on a Tag is black or white
depending on how bright its colour is, so it stays readable either way.

**Searching.** The search field is hidden until you press the search icon.
**Back** closes it and puts the whole list back, rather than leaving the
application. The Tags offered for searching are loaded again every time the
list comes to the front, so a Tag created a moment ago — here or on another
device — is there.

## Inside a note

The toolbar reads left to right as: back to the list, previous note, next
note, the note's date and time, and **+**, which adds something to the note.
Only a voice recording for now.

The note's text is a plain box: tapping it starts editing and puts the
cursor in it, and the tick in the toolbar saves. There is no separate pencil
to find.

Above the text are the star, the tags button and the note's tags.

Tapping the Tags button opens the **Tags screen** for that Note: every tag,
with the ones the note carries ticked. The filter at the top is *not*
focused when the screen opens — this screen is for tapping tags, and a
keyboard over it hides most of them. Tap the filter yourself when a
hierarchy has grown too large to scan. The **+** in its toolbar creates a Tag
and puts it on this Note at once — the same dialogue used everywhere a Tag is
created, so whatever is added to it later appears in all of them.

A note's recordings are listed oldest first, which is the order they were
made in. Which of them stands for the note is yours to choose: see **The main
recording and the main transcription** below.

Settings → Advanced can start that recording playing as soon as the note is
opened. It is off by default: a note opened in company should not begin
talking on its own.

## The main recording and the main transcription

A note can hold several recordings, and a recording can hold several
transcriptions — the first pass, a second one made with a better model, a
corrected one. Only one of each can be shown where there is room for one:
the row in the notes list shows a few lines of *a* transcription, and opening
a note plays *a* recording. Which one is yours to say.

**A note's main recording.** Inside the note, each recording in the list has
a star at its right. Press it to make that recording the one the note stands
on: it is played when the note is opened (if that setting is on), it is the
one whose transcription is shown under the note in the list, and its number
carries a small star in the list so the row says which recording it is
speaking for. Pressing the star of the recording that already has it takes
the mark off, and the note goes back to standing on its oldest recording,
which is what an unmarked note does.

The same star is on the notes screen: tap a recording's button (🔊1, 🔊2) to
unfold its player, and the star sits beside the file name. A note can be
pointed at the right recording without opening it.

**A recording's main transcription.** The same choice is in the ⓘ dialogue
under each transcription, as **Main transcription**. The one marked is shown
first among that recording's transcriptions, and it is the one whose opening
lines appear under the note in the list. Without a mark, the first
transcription is used.

Both marks travel: they are stored with the note and synced, so a phone and
a desktop agree on which recording and which transcription a note is
represented by.

## What a transcription says about itself

Under every transcription are two buttons: **copy**, which puts the whole of
it on the clipboard, and **ⓘ**, which opens what is known about it.

That dialogue holds when the transcription was made, by which service, with
which model, and in which language — and when the language was detected
rather than chosen, what the service decided it was, which is what explains a
transcription that came back in the wrong one.

### How it ran

For a transcription made on this phone, the dialogue also says what the work
cost: the length of the recording, how long the transcribing took and how
that compares with real time, the CPU time and roughly how many cores were
kept busy, the peak memory (whisper.cpp allocates outside the application's
own heap, and that peak is what decides whether a model fits on a phone at
all), the size of the model file, the beam size, the sizes of the recording
and of the 16 kHz copy made for Whisper, and which phone and Android version
did the work.

These are recorded with every transcription, so two models, two beam sizes or
two phones can be compared on real recordings afterwards rather than guessed
at. A transcription made before this was recorded, or on another device,
simply shows the little it has.

### Flags

Under those are the transcription's **flags**: five things that may or may
not be true of it. Press a line to turn it on or off.

| Flag | Means |
|------|-------|
| Original | Unmodified transcription from the service |
| Verified | User has verified the transcription is accurate |
| Verbatim | Transcription includes filler words, false starts, etc. |
| Cleaned | Transcription has been cleaned up (remove filler words) |
| Polished | Transcription has been edited for readability |

They are flags rather than one state because any number of them can be true
at once: a transcription can be verified *and* cleaned, and a verbatim one
that is later tidied stops being verbatim and becomes cleaned. Nothing in the
application sets them for you except *Original*, which is true of every
transcription until somebody edits it — they are a record of what a person
has done, kept for the person who reads it next.

The flags are written into the transcription itself and travel with it, so
the desktop application shows the same five, in the same words, and a
transcription verified on the phone reads as verified there. (In the
database and in the sync protocol the field is still called `state`, which is
what it has always been called; only what you read calls them flags.)

## Sharing

The **share** button in a Note's toolbar sends what is in the Note to another
application.

When the Note holds one thing — its text and nothing else, or a single
recording — it goes straight out, because there is nothing to decide. When it
holds more, a list opens: the Note's text, each recording, and each finished
Transcription under the recording it belongs to, so it is clear what belongs
to what. Tick what should travel and press Share.

Ticking a recording does not tick its Transcriptions, and ticking a
Transcription does not send the audio: each is a thing of its own. Text goes
as text and recordings go as attachments, which is what a mail application
turns into a message with files on it. A recording that is not on this phone
is not offered — its Transcription still is, since the words are here.

## The trash bin

Deleting a note has always been reversible: the note keeps its history and
its recordings, and the other devices are told it was deleted rather than
told to forget it. **Settings → Trash** lists those notes, newest deletion
first, with the text each one had.

**Recover** puts a note back in the list, on this phone and on every device.

**Delete for good** is the one thing in the application that really destroys
something. It asks first, and then removes the note, its history, its tag
links, its attachments and the recordings that hung on that note alone, here
and on every device this phone syncs with. It cannot be undone, and a device
that has not synced yet cannot bring the note back: the removal is written
down and travels, and anything that arrives about a removed note is dropped.
A recording that another note still holds is never taken away. A copy already
uploaded to cloud storage stays in the bucket; only the local files go.

## Reading and moving about

Inside a note, a chevron on either side of the date steps to the previous and
the next note in the list you were looking at, search and star filter
included. They are up and down rather than left and right, because that is
how the list runs and because "up" needs no mirroring in Hebrew. Holding one
shows that note's row from the list, at the full width of the screen and with
twice as many lines of text as the list itself shows, so a glance is enough
to decide whether to go there.

How many lines the list shows is yours to choose: **Settings → Advanced →
Lines in the notes list**, from one to six lines of the note's text and of
its transcription.

### Time format

**Settings → Advanced → Time format** decides how every date and time in the
application is written. Thirteen choices: twelve presets and one free-form
pattern.

The twelve presets are shown as rendered examples rather than as pattern
strings, because a person reads "3 Sep 2026, 14:05" and not
`d MMM yyyy, HH:mm`. Every example depicts the same instant, Thursday
3 September 2026 at 14:05, so they differ only in formatting and can be
compared at a glance:

| Example | Pattern |
|---------|---------|
| 3 Sep 2026, 14:05 | `d MMM yyyy, HH:mm` (the default) |
| 2026-09-03 14:05 | `yyyy-MM-dd HH:mm` |
| 03/09/2026 14:05 | `dd/MM/yyyy HH:mm` |
| 09/03/2026 2:05 PM | `MM/dd/yyyy h:mm a` |
| 03.09.2026 14:05 | `dd.MM.yyyy HH:mm` |
| 3 Sep, 14:05 | `d MMM, HH:mm` |
| Thu 3 Sep, 14:05 | `EEE d MMM, HH:mm` |
| Thursday, 3 September 2026, 14:05 | `EEEE, d MMMM yyyy, HH:mm` |
| 2:05 PM, Sep 3 | `h:mm a, MMM d` |
| 2026-09-03 | `yyyy-MM-dd` |
| 3 September 2026 | `d MMMM yyyy` |
| 14:05 | `HH:mm` |

Wherever a date and a time are shown together, the time is drawn slightly
smaller than the date. A timestamp is read for its day far more often than
for its minute, and the smaller time lets the eye find the day first without
hiding the minute. Which part of the line is the time comes from the format
itself, so it works for a free-form pattern as well, in whatever order that
pattern puts the two.

The thirteenth, **Custom**, reveals a free-text field holding a
`java.text.SimpleDateFormat` pattern. The field is hidden until then, so the
screen stays quiet for everybody else, and what you type is shown rendered
underneath as you type it.

#### The pattern

Any `SimpleDateFormat` pattern works. The letters used most here:

| Letter | Means | Example |
|--------|-------|---------|
| `yyyy` / `yy` | Year | 2026 / 26 |
| `MMMM` / `MMM` / `MM` | Month as name, short name, number | September / Sep / 09 |
| `dd` / `d` | Day of the month | 03 / 3 |
| `EEEE` / `EEE` | Day of the week | Wednesday / Wed |
| `HH` / `H` | Hour, 24-hour clock | 14 |
| `hh` / `h` | Hour, 12-hour clock | 02 / 2 |
| `mm` | Minute | 05 |
| `ss` | Second | 00 |
| `a` | AM or PM | PM |
| `zzz` / `Z` | Time zone, name or offset | GMT+03:00 / +0300 |
| `'text'` | Text kept as it is | `'at'` → at |

Month and day names follow the phone's language, so `EEEE` reads
`יום רביעי` on a Hebrew phone even though the examples above are English.

#### One token of our own: `-N`

`-N` becomes the number of **whole days ago**, counted in calendar days
rather than in twenty-four-hour periods: something written at 23:50
yesterday reads `-1` at 00:10 today, which is what a person means by
"yesterday". A time still to come reads `-0` rather than a positive number.

```
yyyy-MM-dd HH:mm EEE -N   →   2026-09-05 14:03 Sat -2
```

That token is the default custom pattern. It needs its own handling because
`SimpleDateFormat` throws on an unknown pattern letter, and `N` is one: the
pattern is rewritten with a quoted private-use character (`U+E000`) in its
place, formatted, and the day count substituted afterwards. The count is
wrapped in directional isolates (`U+2066`, `U+2069`) so that the minus stays
to the left of the digits on a Hebrew or Arabic phone, where a lone minus
would otherwise be reordered.

To add another token later — `-H` for hours, say — use the same technique
with a different private-use marker.

#### What cannot go wrong

A pattern that `SimpleDateFormat` will not parse (easy to type in a free-text
field) falls back to the default, `d MMM yyyy, HH:mm`, rather than breaking
the screen.

Times are still drawn in the zone they were written in: a note recorded at
15:20 in Jerusalem reads 15:20 in whatever format you choose, from anywhere.
The days-ago count compares that moment's calendar day, in its own zone, with
today here.

### Playback

**Settings → Playback** holds what happens when a Note is opened and the
speed recordings play at. *Automatic playback* starts the Note's main
recording as the Note opens; it is off by default, since a Note opened in
company should not begin talking on its own. It used to live in Advanced
settings, which is where a person goes to change something unusual — playing
a recording is what this application is for.

A recording started in the Notes list **carries on playing** when you open
the Note, from where it had reached, and goes on playing while you visit that
Note's Tags screen. There is one player, so starting something else stops
what was playing rather than talking over it.

Under any waveform: tap to jump, or **drag** to scrub — the position follows
your finger while it moves, so a passage can be found by ear without lifting
and trying again.

### Interface size

**Settings → Advanced → Interface size** has three choices. *Standard* is the
size the application has always had. *Large* draws every text and every icon
half again as large, for reading and tapping while moving, on a horse or in a
vehicle, where the standard size cannot be hit; less fits on the screen,
which is the point. The third choice starts standard and puts a button in the
notes toolbar, so the switch is made when it is needed, and it applies to
every screen.

### Icon size

**Settings → Advanced → Icon size** sizes the marks on a note by themselves:
the speaker and transcription marks at the left of each row in the notes
list, the transcribe mark beside a recording inside a note, and the two
buttons under a transcription. *Small* is
the size they have always been, *Medium* is half again as large, and *Large*
is half again as large as medium. Each choice shows the mark at its own size
beside it, so the choice is made by looking rather than by guessing.

It is a separate setting from the interface size because the two wants are
separate: the marks say what a note holds and are tapped to open a recording,
so they are worth enlarging on their own, while the text stays at the size
that fits most notes on the screen. Choosing a large interface as well
multiplies the two.

## Recording

A recording is always made inside a note, and the note comes first: *New
Voice Recording* creates the note and opens it with the recorder in it, and
the red **microphone** in any note's toolbar adds another recording to a note
that already exists. There is no separate recorder screen, so a recording
never has to be moved into a note afterwards, and the text, tags and
recordings of a note are all edited in one place.

The recorder sits where the player of a note with recordings sits, and reads
the same way: the elapsed time in `hh:mm:ss`, a live waveform, a large red
Record/Pause button with a small **Discard** to its left and a small **Save**
to its right. Those three keep their sides in a right-to-left layout, the way
the buttons of a tape recorder stay where they are.

**Discard** asks before throwing anything away, and its question is where
*Restart* lives: *Discard* drops the recording, *Restart* drops it and begins
again from `00:00:00`, *Cancel* changes nothing. One destructive button on
the screen, not two.

A note created only to hold a recording goes away with the recording if the
recording is discarded or never made, so backing out leaves no empty notes
behind. A note that already had text or recordings is never removed this way.

Recording carries on when the app is left, and the recorder in the note picks
it up again where it was. Opening the recorder always starts from
`00:00:00`: what the last recording left on screen belongs to that recording,
not to this one.

### Microphones

**Settings → Recorder → Microphones** is a screen of its own. A phone has
three or four microphones and each has a name to give and a level meter to
watch, which filled the recorder settings so that everything under them went
unseen. The recorder screen now shows which microphone is chosen and opens
that screen when tapped.

There, each microphone can be named ("bottom", "top, near the ear"), one of
them chosen for recording, and **Test** pressed while speaking: the bar shows
how loudly that microphone hears you and the peak shows the loudest point so
far. Speak near each edge and corner of the phone to learn where each one
hears best.

## Long recordings

A Note holds a recording of any length. An eight-hour recording of somebody
sleeping is kept, played, tagged, searched and synced exactly like a
two-minute voice note; nothing about it is a special case.

Two things are **not done for you** with a long recording, because both mean
reading the whole of it:

**The waveform.** Drawing one means decoding the entire recording, which a
phone manages at perhaps fifty to two hundred times real time — so an hour of
audio is tens of seconds of work, and it holds one of the phone's three
hardware decoders for all of it. For a recording past the limits below, the
waveform area reads

> Click to generate waveform
> Resource intensive operation on large file

and the waveform is drawn when you press it. It is then **kept on this
device**, so the waiting happens once per recording; see
[Tag colours and other device settings](#the-notes-list) for what else is kept
per device rather than synced.

**Transcription, past ten minutes.** This phone transcribes recordings of up
to ten minutes. Ask for a longer one and it answers with its length, the limit,
and where the work can be done instead:

> This recording is 22:00 long. A phone transcribes up to 10 minutes;
> transcribe it on the desktop after the next sync.

Nothing is lost by asking — the recording is untouched, and it transcribes
normally on the desktop, which has no limit, once the two have synced. On the
desktop, `transcribe-backlog` finds every Recording the phone passed over and
transcribes them in one run; see *Transcribing what the phone could not* in the
desktop manual.

The reason is memory: Whisper holds the whole recording as it works, about
230 MB an hour on top of a model of one to three gigabytes. Ten minutes is
38 MB and comfortable; an hour is not, and several hours is beyond any phone.

### What counts as a large recording

| Measure | Limit |
|---------|-------|
| Length | 60 minutes |
| Size | 100 MiB |

Either one is enough. The length is the measure that matters, because the work
tracks the length of the audio; the size catches a recording whose length is
not known yet, or whose header is wrong. For reference, 100 MiB is about 52
minutes of 16 kHz WAV, 1.8 hours of Opus at 128 kb/s, or 2.4 hours of AAC at
96 kb/s.

Both numbers are in `util/MagicNumbers.kt` as `LONG_RECORDING_SECONDS` and
`LARGE_RECORDING_BYTES`, and the desktop application uses the same two.

## On-device transcription

Settings → Transcription lists the Whisper models the app can download (ggml files from
Hugging Face, kept in the app's private storage and deleted with the app):

| Model | Size | Notes |
|-------|------|-------|
| Whisper large-v3, 5-bit | 1.1 GB | Recommended. Hebrew, English, Arabic, Russian and 95 more |
| ivrit.ai large-v3-turbo | 1.6 GB | Fine-tuned on Hebrew; the most accurate for Hebrew; Hebrew only |
| ivrit.ai large-v3 | 3.1 GB | Full-size Hebrew fine-tune; needs about 4 GB of free memory |
| Whisper large-v3, full | 3.1 GB | Needs about 4 GB of free memory |
| Whisper large-v3-turbo, 5-bit | 0.6 GB | Several times faster, a little less accurate |
| Whisper medium, 5-bit | 0.5 GB | Small and fast, weaker on Hebrew |

Pick *Beam search* (accurate) or *Greedy* (fast), then open a note and tap the **transcribe**
icon beside one of its recordings. The result is a normal transcription record (service
`local_whisper`) and syncs to every other device.

### Languages

No language is built into the application. Settings → Transcription lists every language it can
be told to use, and each one is set to one of three things:

| State | Where it appears |
|-------|------------------|
| **Button** | A button of its own in the Transcribe dialog: one press transcribes in that language |
| **In the list** | Behind the drop-down in that dialog |
| **Not used** | Nowhere |

The list starts with buttons for Hebrew, English and *Detect automatically*, and Arabic, Russian,
Spanish and Greek in the drop-down; everything else is off until you switch it on. *Show all
languages* opens the whole catalogue. The language marked with the radio button is the one used
when nothing else is chosen, and it can only be one that is switched on.

### The queue

Settings → Transcription queue. Transcription runs one recording at a time —
Whisper wants about a gigabyte of memory and every core, so two at once are
slower than two in turn — and a long recording takes minutes, so a queue of a
dozen notes is an hour of work. This is where that hour is accounted for.

Three groups, read downwards as time runs forwards:

| Group | What it is |
|---|---|
| Waiting | The recordings that have not started. Newest first, each saying the place it will really be reached in and how long until it is done |
| Processing | The recording being transcribed now, with what it is doing |
| Completed | What is finished, newest first, with what the work cost |

Every row names the note the recording belongs to. Tapping that line shows the
note exactly as the notes list draws it — the same preview a held-down Previous
or Next gives inside a note — and tapping the preview opens the note.

A finished row carries the numbers this screen exists for:

| Measure | What it says |
|---|---|
| Text | How many characters the transcription came to |
| Recording | How long the recording was |
| Clock time | How long you waited, conversion and model loading included |
| Processor time | How much processor it used, which is more than the clock time when several cores work at once |
| Cores busy | Roughly how many cores it kept busy |
| Memory, peak | The most memory it held, which is what decides whether a model fits on this phone at all |
| Speed | Seconds of audio per second of work |

Those numbers are what the waiting estimates are calculated from: the estimate is
the median of what **this phone** has actually done with that model, not a
specification. Until something has finished here, no estimate is offered, because
a made-up number is worse than none.

**Transcribe this one next.** When you need one transcription before the rest,
the ▲ button on a waiting row puts it at the head of the queue. The recording
being worked on is never interrupted: it is minutes into work that would have to
start again, which would cost more than the wait. "Next" means next after that
one. The ✕ button takes a waiting recording out of the queue, and **Stop all**
empties the queue and cuts short the one running.

The desktop application has the same queue for its own work; see its user manual.

### While a transcription is waiting

A recording whose transcription has been asked for but has not arrived shows the transcribe mark
with a clock turning over it, in the notes list and in the note. A transcription that failed does
not: waiting will not turn it into a transcription, so the error is shown instead.

Transcription text can be selected with a long press, and the icon beside its date copies the
whole transcription.

### While it runs

Transcription continues with the screen off, while you are in another
application, and after the application is swiped out of the recent-apps list.
A twenty-minute recording is minutes of solid work for the phone, so this is
the difference between a transcription that finishes and one that never
does.

The notification in the shade names the recording, says how many are waiting behind it, and
carries a **Stop** button. Stop abandons the whole queue: whisper is asked to stop between windows
of audio and returns within about a second, the partial text is thrown away, and the pending
record of the stopped recording is deleted, leaving the recording as it was. Android 13 and newer
ask for permission to show notifications the first time the app is opened; without it the work
still runs, but there is nothing to see and no Stop button.

Asking for a transcription while the application is not on screen — from the
ADB tool, with the phone locked — answers "Transcription will start when the
app is open", and the recording waits in the queue until it is. Nothing is
written down until the work really begins, so an attempt that never started
leaves no "Pending..." row behind.

If the application is killed while a transcription is running (a reboot, or the phone reclaiming memory),
the record it left says "Error: the app was closed before the transcription finished". Once that
recording has really been transcribed, the message is deleted: a recording keeps its
transcriptions, not the history of what interrupted them.

## While a recording plays

The recording being listened to appears in the notification drawer, with how far
through it is, the note it belongs to, and three things to do: **Pause** (which
becomes **Play** once paused), **Stop**, and a tap on the notification itself,
which opens the note the recording is in.

It is not only a convenience. Android stops an ordinary application's work when
it leaves the screen, and this notification is a foreground service, which is
what keeps a recording playing with the screen off or another application in
front. Stop ends it and takes the notification down; Pause keeps both, so the
recording can be carried on from the drawer.

## Calculating missing data

Settings → Calculate missing data.

Some facts about a recording are not known when it arrives, and some were never
calculated: a recording imported before lengths were recorded has no length, a
recording copied without its dates has no creation date, and a note written
before the display caches existed has no cache. None of it is lost — it can be
read back off the file, or computed again — but until it is, the application shows
less than it knows, and the decisions that depend on a length (whether a
waveform is drawn without asking, whether the phone will transcribe a
recording) have nothing to go on.

The screen counts what is missing first and changes nothing until you tap
**Calculate what can be calculated**, because reading the length of every
recording is work and you should see what it is for before it starts. The refresh button at
the top right counts again.

What it calculates:

| What | Where it comes from |
|---|---|
| Recordings with no length recorded | the file's header, read without decoding the audio, so an eight-hour recording costs the same as a one-minute one |
| Recordings with no creation date | the file's date, or the date in the name the recording arrived under, by the same rule the desktop uses |
| Notes with no display cache | recomputed from the note, its tags and its recordings |

What it never calculates, and says so instead:

| What | Why not |
|---|---|
| Recordings whose file is not on this phone | there is nothing here to read. Download them first, or run this on the device that has them |
| Recordings with no timezone recorded | a timezone cannot be derived from a file, and writing this phone's offset would state something false about where the recording was made |

Running it twice is harmless: the second run finds nothing to do. Nothing it
writes is an edit by you, so none of it touches a note's history; the values
reach your other devices as ordinary metadata on the next sync.

The desktop application has the same repair, in the same words, on all four of
its interfaces.
