# Voice for Android — user manual

How the application is used: what each screen holds, what the settings change,
and why things are arranged as they are. For what the application is, see the
[README](README.md); for building it from source,
[DEVELOPMENT.md](DEVELOPMENT.md).

## Contents

- [Starting out](#starting-out)
- [Syncing between devices](#syncing-between-devices)
  - [The words](#the-words)
  - [Pairing](#pairing)
  - [A phone that holds Notes cannot join another account](#a-phone-that-holds-notes-cannot-join-another-account)
  - [Devices](#devices)
  - [The five operations](#the-five-operations)
  - [Listen for devices](#listen-for-devices)
  - [Check connection](#check-connection)
  - [The proof line](#the-proof-line)
  - [This device](#this-device)
  - [Advanced Sync Options](#advanced-sync-options)
- [Cloud storage for Recordings](#cloud-storage-for-recordings)
  - [Upload](#upload)
  - [The upload limit](#the-upload-limit)
  - [Download](#download)
  - [Where are the copies?](#where-are-the-copies)
  - [Remove from this phone](#remove-from-this-phone)
  - [Encryption of recordings in the bucket](#encryption-of-recordings-in-the-bucket)
- [Move this device to another account](#move-this-device-to-another-account)
- [Snapshots](#snapshots)
- [The Issues screen](#the-issues-screen)
- [The Notes list](#the-notes-list)
- [Inside a Note](#inside-a-note)
- [The main recording and the main transcription](#the-main-recording-and-the-main-transcription)
- [What a Transcription says about itself](#what-a-transcription-says-about-itself)
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
  - [Where Recordings are kept](#where-recordings-are-kept)
- [Long recordings](#long-recordings)
- [On-device transcription](#on-device-transcription)
  - [The transcription queue](#the-transcription-queue)
- [While a recording plays](#while-a-recording-plays)
- [Calculating missing data](#calculating-missing-data)
- [Where the logs are](#where-the-logs-are)

## Starting out

When the Notes list holds no Notes, it reads **No notes yet.** and offers two
buttons:

- **Pair with another device** opens **Sync Settings** with the code reader
  already open. It is for a phone that is to join an account another device
  already holds. See [Pairing](#pairing).
- **Start on my own** creates an empty Note and opens it, in this phone's own
  account.

Choose before writing anything. A phone joins another account by a code only
while it holds no Notes at all, and the empty Note that **Start on my own**
creates counts as one; see
[A phone that holds Notes cannot join another account](#a-phone-that-holds-notes-cannot-join-another-account).

Syncing is optional. Without it the application is complete on this phone:
Notes, Recordings, Tags, search and transcription all work with no network.

## Syncing between devices

Settings → **Sync Settings**. The devices of one account sync directly with
each other; no server of your own is needed. The phone connects to a device
that listens, and while **Listen for devices** is on, other devices connect to
the phone. The devices this phone syncs with are listed under **Devices**.

Settings itself starts with the card **Synchronization**: the same
**Exchange with \<device>** button as on Sync Settings, the result of the last
operation in one line, and the text button **Sync Settings**.

### The words

Each of these words has one meaning, on screen and in this manual:

| Word | What happens |
|---|---|
| Sync | Changes to the database — Notes, Tags, Transcriptions, and the rows that describe Recordings — go both ways between this phone and a device. No recording file moves. |
| Upload / Download | Recording files are copied from this phone to the bucket / from the bucket to this phone. |
| Send / Fetch | Recording files are copied from this phone to a device / from a device to this phone. |
| Deliver | Sync, then send. |
| Exchange | Sync, then send and fetch. |
| Listen | The phone accepts connections from devices. |
| Host | A device serves an account that is not its own. |
| Pair | A new device receives the account's id, a key of its own, and one other device to sync with. |

A sync therefore never moves a file. The bucket is reached only by Upload and
Download, a device's files only by Send and Fetch, and each of them starts only
when you press its button.

### Pairing

One device, which already holds the account, shows a code. The new device
reads it.

**On the device that holds the account**, press **Show my code**. On the phone
the button is in the card **Sync Actions**. The phone then:

- turns on **Listen for devices** if it is off, because the reading device
  connects to this phone;
- makes a setup text that starts with `voice://pair?` and holds every address
  this phone may be reached at; the reading device tries each of them in turn
  and remembers the one that answers;
- shows the setup text as a QR code and as text, under "Treat this like a
  password: whoever reads it joins your account. Hidden in \<n> s.", with the
  buttons **Copy** and **Share**.

The code is taken off the screen after sixty seconds, or when **Hide my code**
is pressed. A code lets one device in, and it expires ten minutes after it was
made. A phone serves its own local network only, so the device that reads the
phone's code must be on the same network.

**On the new device**, press **Read a code**. The camera opens over the whole
screen; the first time, the phone asks for permission to use it.

- Every camera is tried in turn, rear first and front last. The line under the
  picture names the camera that is open, for example *Rear camera (1 of 3).
  Point it at the code shown by the other device.* When a camera fails to open,
  or gives no picture for three seconds, the next one is tried. **Another
  camera** moves to the next one by hand, and the line names it.
- The field and the buttons sit above Android's navigation buttons.
- Without a camera, or without the permission, paste the setup text into
  **Or paste the setup text** and press **Use it**. **Use it** is available
  only when the text starts with `voice://pair?`.
- **Cancel** closes the reader.

**As a link.** A setup text is also a link. Tapped in a messaging application
on the phone, it opens Voice at Sync Settings and pairs at once.

After a successful pairing the phone says "Joined account \<id> through
\<device>." and shows a card for the new device: "Paired with \<device>. Exchange
brings its notes and recordings here, and yours there." **Exchange now** starts
an exchange with that device; **Later** closes the card.

**A server that holds no account** prints a grant text instead of a code.
Pasted into the same field, it makes that server host this phone's account.
The phone says "\<device> now hosts this account. Press Deliver to send it your
notes and recordings." and offers **Deliver now** and **Later**.

When pairing fails, the reason follows **Could not join:**. Among the reasons:

- "The code is not valid: it was spent, it expired, or it was mistyped
  (TOKEN_INVALID)"
- "Could not reach device \<id>: \<reason>"
- "The device answered for account \<id>, but the code was for \<id>
  (ACCOUNT_MISMATCH)"
- the refusal described in the next section.

### A phone that holds Notes cannot join another account

A phone takes the account of a code only while its database holds no Notes at
all. Every Note counts: a Note in the trash, and an empty Note, such as the one
**Start on my own** or the **New** button creates. A phone that holds any Note
refuses the code before it connects to anything, with:

> This device holds \<n> notes of account \<id>; the code is for account \<id>.
> Show this device's code to the other one instead, or use 'account move'
> (DEVICE_HOLDS_NOTES)

Two choices remain:

- **Let the other device join this phone's account**: press **Show my code**
  on this phone and **Read a code** on the other device. The same rule applies on that device: it must hold no Notes.
- **Move this phone to the other account.** 'account move' is the desktop's
  command; on the phone it is Settings → Advanced →
  [Move this device to another account](#move-this-device-to-another-account).

### Devices

Sync Settings starts with **This device: \<name>**, this phone's own name, so it
is clear which device the screen is on. The card **Other devices of this
account** lists every other device this phone knows. For each device:

- its name, with "(last used)" after the device of the last operation;
- the first eight characters of its device id, and its address, or "no address
  yet";
- "Last reached \<time or never>, last operation \<operation>".

**Rename** opens the field **Name on this phone**; **Save name** stores the
name. The name belongs to this phone's list and changes nothing on the other device.

**Forget** removes the device from this phone's list at once, without asking:
"Forgotten. Its card will not bring it back; add it again or pair again to
undo." Forgetting a device deletes no Note, here or there.

**Add a device by its address** tells this phone where a device of the same
account listens:

- **Its device id (32 hex characters)**
- **Where it listens**, for example `https://192.168.1.10:8384`
- **A name for it**. When left empty, the name is the first eight characters of
  the id.

**Add device** is available once the id is 32 characters long and the address is
not empty. A device shows its own id and address on its sync screen; on the
phone they are **This device's ID** in the card **This device** and the line **Address** in
the card **Sync Actions**. Adding a device lets no device into the account; only
pairing lets a device in.

### The five operations

In the card **Sync Actions**, one button names the device of the last operation:
**Exchange with Desk**, where Desk is that device. With no device it reads **No
device yet**; with several devices and none used yet, **Choose a device ▸**.

The **▾** beside it (accessible name "Choose another device or operation") lists
five operations for every device:

| Menu item | What moves |
|---|---|
| **Exchange with Desk: sync, then send and fetch recordings** | Database changes both ways, then the recording files Desk lacks go to Desk and the files this phone lacks come here |
| **Deliver to Desk: sync, then send recordings** | Database changes both ways, then the recording files Desk lacks go to Desk |
| **Sync with Desk: notes only** | Database changes both ways; no file |
| **Send to Desk: recordings it lacks, no sync** | The recording files Desk lacks go to Desk |
| **Fetch from Desk: recordings this phone lacks, no sync** | The recording files this phone lacks come here |

While an operation runs:

- the button reads **Working…**;
- the card shows a progress sentence, for example "Received 12 changes from
  Desk", with a **Cancel** button;
- a notification titled with the operation, for example **Exchange**, shows the
  same sentence and has a **Cancel** action.

The operation continues with the screen off or another application in front.
One operation runs at a time.

**Cancel** stops the operation at its next page of changes, file, or chunk of a
file, and the card reads "Cancelling at the next page, file or chunk…". A file
that was part of the way across keeps the part that arrived, and the next send
or fetch of that file continues from there. A transfer broken by the network
also continues from where it stopped.

A file is tried three times: the second try straight after the first, the third a
minute after the second. A file that failed every try is reported, and the
operation goes on with the next file; after three files failed every try, the
operation stops and says how many it did not attempt. Start it again when the
connection works. Upload and download follow the same rule.

When the device gives no answer at its remembered address, the phone tries each
address the other device's card names, checking the device's certificate, and
remembers the address that answers. When none answers, the phone searches the
local network for the device for three seconds. If the device answers from another
address, the phone stores that address and runs the operation once more.

Before every sync the phone takes a [snapshot](#snapshots) of its Notes
database.

The result is one sentence, for example "Exchange with Desk: received 12
changes and sent 3, sent 2 recordings, fetched 4 recordings, 40 MB moved.", or
"… nothing to move." when nothing moved. A failure reads "Exchange with Desk
failed: \<reason>". Lines "Warning: \<warning>" and "Request \<id>" can follow. A
failed operation is also written to the [Critical Log](#where-the-logs-are).

### Listen for devices

The switch **Listen for devices**, at the top of the card **Sync Actions**, lets
other devices of the account connect to this phone. Under it the phone says
"Other devices can reach this phone" or "Off; nothing can reach this phone".

While the phone listens:

- a notification **Listening for devices** shows the phone's first address, with
  a **Stop** action;
- the phone accepts connections on port 8384, from its local network only;
- the phone announces itself on the local network, so a device that has lost
  its address can find it.

Only this switch and **Show my code** start listening.

**The idle stop.** The text button under the switch (accessible name "When the
listener stops itself") shows when listening ends by itself:

- **keep listening** (the default)
- **stop after 1 hour of silence**
- **stop after 4 hours of silence**
- **stop after 8 hours of silence**

Silence is counted from the last request the phone served, or from when it
started listening. The phone checks once a minute.

Under the switch are three lines to type into another device: **Account**
(the account's id), **Address** (where another device on the same network
reaches this phone) and **Certificate** (the fingerprint of this phone's
certificate).

**Address** shows the address the phone found through its route to the
network. When the phone cannot tell which of its addresses that is, the line
lists every candidate, followed by "Only one of these addresses is correct;
this device could not tell which. Another device tries each of them in turn."
With no address on a local network it reads "No address on a local network was
found. Is this device on a network?", and before the addresses are read, "not
read yet". Addresses of mobile data, VPNs, tunnels and virtual networks are
left out.

### Check connection

**Check connection** tests the connection to the device that the one button
names, and changes nothing. Each line reads ✓ or ✗, the thing checked, what was
found, and a code in brackets when there is one. Without a device, the only line
is "✗ Device: No device yet: read a code shown by another device, or add one by its
address".

| Line | Passes with | Fails with |
|---|---|---|
| Device | | "This device remembers no device \<id>" |
| Reachable | "\<name> answers at \<address>" | "\<address> answered with status \<status>", "\<address> does not answer: \<reason>" |
| Device | | "The device at \<address> is \<id>, not \<id>" (DEVICE_MISMATCH) |
| Certificate | "Verified by the system's root certificates", "The pinned fingerprint matches" | the certificate error (CERTIFICATE_MISMATCH) |
| Protocol | "Version \<version>" | the refusal (PROTOCOL_TOO_OLD) |
| Account | "The other device holds account \<id>" | "The other device holds account \<id>, this device \<id>" (ACCOUNT_MISMATCH) |
| Key | "This device's key is accepted" | the refusal, with its code |
| Clock | "The clocks agree to within a minute" | "This device's clock is \<n> minutes behind \<device>'s", or ahead |
| Free space there | "\<n> MB free on \<device>" | the same, when less than 64 MB is free |
| Recordings there | "The other device serves recordings", or "The other device serves notes only; no audio directory is configured there" | |
| Free space here | "\<n> MB free on this device" | the same, when less than 64 MB is free |
| Listener here | "This device is listening", or "This device is not listening; the other device cannot start an operation towards it" | |

### The proof line

The first line of Sync Settings says what exists on this phone only:
"Everything is duplicated off this device.", or, for example, "3 notes and 2
recordings are not duplicated off this device."

- A Note counts when its current text was written on this phone and no device has
  received it by sync yet.
- A Recording counts when its file is on this phone, the bucket holds no copy
  of it, and no device is known to hold it.

Under it is one line per device: "Desk: last reached \<time>, last operation
exchange". This line is the only place the phone says this: there is no
notification and no badge.

### This device

The card **This device** holds **This device's name** and **This device's ID**,
this phone's id in the account. **This device's name** starts as the first of: the phone's name in
Android's **Settings → About phone** ("Galaxy A12", or the name given there);
the phone's Bluetooth name; its maker and model ("Samsung SM-A125F"); and when
Android says none of these, an animal with the ends of the phone's addresses,
such as "Wombat 81:4c 7.21".
**Generate a new ID for this device** writes a new random id into the field; nothing
changes until **Save Settings**, under the card, is pressed. **Save Settings**
stores the name and the id.

### Advanced Sync Options

- **Full Re-sync** asks the device of the last operation for all of its changes,
  not only those since the last sync. It moves no recording file, shows no
  notification and has no Cancel. It is available only when the one button names a
  device.
- **Reset Sync Timestamps** clears this phone's record of when it last synced
  with each device, so the next sync goes through all changes again. The devices
  stay.
- The card **Debug Info** counts the Recordings and Notes in the database and
  names the audio folder; **Refresh Debug Info** counts again.

## Cloud storage for Recordings

The **bucket** is the account's cloud storage for recording files. Notes, Tags
and Transcriptions never go to the bucket; they move only by sync.

The phone has no screen for setting up a bucket. A bucket set up on another
device of the account reaches this phone by sync; until then there is no
bucket to upload to or download from.

### Upload

**Upload**, in the card **Sync Actions**, copies to the bucket every Recording
whose file is on this phone and is not yet in the bucket. The button
reads **Uploading...** while it runs; there is no notification and no Cancel.

- A file larger than 8 MiB goes to the bucket in parts. An upload that stopped
  continues next time with only the parts still missing.
- While [encryption](#encryption-of-recordings-in-the-bucket) is on, every
  upload is encrypted.

The result reads "Upload: " followed by "uploaded \<n>", "\<n> belong to another
device" (Recordings whose file is not on this phone), "\<n> failed" and "\<n> not
attempted", or "nothing to upload". A failure reads "Upload failed: \<reason>"
and is also written to the [Critical Log](#where-the-logs-are).

### The upload limit

The card **Upload limit** sets the size above which a Recording is not
uploaded: type a number of megabytes into **Upload limit (MB)** and press
**Save the upload limit**.

- The limit belongs to the account: set on any device, every device uses it.
- It is 100 MB until it is set, and at least 1 MB.
- It can be set only once the account has a bucket. Before that the phone
  shows an error that names "No bucket is configured yet".

A Recording larger than the limit stays on the devices that hold it, and
[Issues](#the-issues-screen) lists it as "larger than the account's upload
limit of \<size>".

### Download

Download works per Note, from inside the Note.

- **None of the Note's recording files are on this phone.** In place of the
  player, a cloud icon, then "Media missing: \<n> file(s) not on this device."
  and, for files no device has uploaded, "\<n> file(s) not uploaded by their
  device yet.". The button **Download** (**Downloading...** while it runs)
  appears when at least one of the missing files is in the bucket.
- **Some files are on this phone.** The player, and under it the same text with
  the button **Download missing media**.

The result reads "Media: " followed by "downloaded \<n>", "\<n> already on this
device", "\<n> not uploaded by their device yet" and "\<n> failed", or "nothing
to download". A failure reads "Download failed: \<reason>".

A file that is not in the bucket can come from a device instead: **Fetch from
\<device>** or **Exchange with \<device>**; see
[The five operations](#the-five-operations).

### Where are the copies?

In a Note's player, each Recording's row has a **⋮** button (accessible name
"More for \<file name>"). Its first item, **Where are the copies?**, opens the
dialog **Where the copies are**.

Before the dialog opens, the phone compares its audio folder with what it has
stated about it: a file that is there is stated as here, a file that is gone is
stated as gone. Then one line per place:

- "\<place>: holds it (since \<date and time>)", or
- "\<place>: does not hold it (since \<date and time>)".

A place is "the bucket", "this device", another device's name, or the first
twelve characters of its id. With nothing known: "No place is known to hold
it". When no place is known to hold it, this phone made the Recording, and its
file is not in the audio folder, a second line says how the phone made it:
"Recorded on this device, but its file was not found in the audio folder after
the recording", or "Imported on this device, but its file was not found in the
audio folder after the import". These statements travel by sync, so every
device of the account shows the same places.

### Remove from this phone

The second item of the same **⋮** menu, **Remove from this phone**, asks:
"Remove \<file name> from this phone? The recording stays, and it can be fetched
again from wherever else it is kept." **Remove** asks another place, at that
moment, whether it holds the file, and deletes this phone's copy only when one
place confirms that it does:

- **the bucket**, asked directly: it holds the object, and the object is not
  waiting to be deleted after a purge;
- **a device of the account that is stated to hold the file**: it answers over
  the network, and promises to keep its own copy for ten minutes while this
  phone removes its copy.

The phone then states that it no longer holds the file, and the Note is shown
again. The Recording, its Transcriptions and its place in the Note stay.

A refusal begins "Not removed:" and names the reason:

- "\<file name> is not on this device";
- "\<file name> was not removed: no other place confirmed that it holds the file
  now (\<what each place answered>)", where an answer is, for example, "no other
  place is known to hold it", "the bucket does not hold it", "no bucket is set
  up on this device", "\<device> does not keep it: \<reason>" or "\<device> could
  not be reached: \<reason>";
- "\<file name> was not removed: this device promised \<device> to keep its copy
  until \<time>, while \<device> removes its own".

While this phone is removing its copy, it refuses another device's request to
keep its own ("this device is removing its own copy"). Two devices that each
count on the other therefore never both remove the file.

When the bucket does not hold the file, the device that holds it must be
reachable at that moment: on the same network, and listening.

### Encryption of recordings in the bucket

Settings → **Advanced** → **Encryption of recordings in the bucket**. With
encryption on, a recording file in the bucket is encrypted, and only a device
with the account's recording key can open it.

**The key.** The account has one recording key of 43 characters. A device that
joins by a code receives it during pairing.

- **Export the key** shows the key as a QR code and as text, with "Recording
  key. Keep this on paper. Without it these recordings cannot be played." and
  **Copy**. **Hide the key** takes it off the screen.
- **Import a key** opens **Import the recording key**: type the 43 characters
  into **The 43 characters from an export** and press **Keep it**.

**The switch** reads **New uploads are plain** or **New uploads are
encrypted**. It can be turned on only after the key has been exported on this
phone, so that the key exists on paper before anything depends on it. The
setting belongs to the account: turned on here, it is on for every device after
the next sync.

- Turning it on encrypts nothing already in the bucket. **Re-upload existing
  recordings encrypted** uploads again, encrypted, the plain copies of the
  Recordings whose files are on this phone, and reports "Re-uploaded encrypted:
  \<n>; not on this phone: \<n>; failed: \<n>".
- Turning it off makes new uploads plain; files already encrypted still open on
  a device with the key.

Copies on the phone are plain: a device with the key opens a downloaded or
fetched file as it arrives.

## Move this device to another account

Settings → **Advanced** → **Move this device to another account**. This is for
merging two accounts: the Notes on this phone join the other account, Tags with
the same path become one Tag, and nothing is deleted.

1. On a device of the other account, press **Show my code**, and copy its setup
   text to this phone (**Copy** or **Share** on that device).
2. Paste it into **The other account's code**.
3. Type this account's id in full into **This account's id, typed in full**.
   The id is shown in the empty field and under **Snapshots**, and on Sync
   Settings as **Account**.
4. Press **Move \<n> notes to the other account**. The button is available once
   the code starts with `voice://pair?` and the typed id is 32 characters long.

The phone then takes a snapshot, uses the code to receive a key of the other
account, changes its account id, forgets every device, receives the other
account's Tags and makes Tags with the same path into one, and syncs with the
device whose code it read. It reports "Moved \<n> notes to account \<id> through
\<device>; \<m> tags with one path became one.", or "Not moved: \<reason>".

A grant text is refused here ("This is a grant text, not a code"), and so is a
code of this phone's own account ("That is already this device's account").

## Snapshots

Settings → **Advanced** → **Snapshots**. A copy of the Notes database is taken
before every sync, and the newest five are kept. Recording files are never part
of a snapshot.

- The line **Account** shows the account's id.
- **Take a snapshot now** takes one at once: "Snapshot written".
- Each snapshot is listed by its file name, with "\<n> notes, \<n> KB", and a
  **Restore** button.
- **Restore** asks "Restore \<name>?": "The notes database is replaced with this
  snapshot. The current state is kept as the newest snapshot, so this can be
  undone." **Restore** replaces the database; **Cancel** leaves it. After a
  restore the phone compares its audio folder with what the restored database
  says, so [where the copies are](#where-are-the-copies) is true of the folder
  as it is now.

## The Issues screen

Settings → **Issues**. What the user should know about, calculated each time the
screen opens and never stored. The phone compares its audio folder with what it
has stated first. The refresh icon (accessible name "Read the issues again")
calculates again. With nothing to report the screen reads "Nothing needs your
attention."

A section with nothing in it is left out:

| Section | Each line |
|---|---|
| Recordings not in cloud storage (\<n>) | "\<file name> (\<size>): \<reason>", the reason being "no bucket is set up for the account", "larger than the account's upload limit of \<size>", "waiting for \<places> to upload it", or "no device and no bucket is known to hold it"; when this phone made the Recording and its file is not in the audio folder, the last is followed by "; recorded on this device, but its file was not found in the audio folder after the recording", or the same for an import |
| Transcriptions whose recording is not there (\<n>) | "Transcription \<id> of recording \<id>: \<start of its text>" |
| Attachments whose note or recording is not there (\<n>) | "Attachment \<id>: its note \<id> and its recording \<id> is not there", naming only what is missing |
| Recordings no note holds (\<n>) | "\<file name> (\<id>)" |
| Tags whose names contain spaces (\<n>) | the Tag's path |

A Note in the trash still holds its Recordings. The lines are text only: tapping
one opens nothing.

## The Notes list

**The toolbar**, left to right:

- **New**: a **+** (accessible name "New note"), or a red dot ("New voice
  recording") when Settings → Recorder → **The New button creates** is set to
  **A new voice Recording**. A tap creates what that setting names; a long
  press offers **New Note** and **New Voice Recording**.
- The star filter ("Show only starred" / "Remove starred filter").
- The search icon. The search field is hidden until it is pressed.
- The size switch, when Interface size asks for one; see
  [Interface size](#interface-size).
- The gear, **Settings**. This is the only way into Settings.

**A row** is one Note. Left to right on its first line:

- the chevron ("Open this Note" / "Close this Note"), which opens the Note's
  section;
- the star ("Star this Note" / "Unstar this Note");
- when the Note has Recordings, one mark, 🔊 and the number of Recordings, with
  a transcribe mark when one of them has a Transcription, and a clock over that
  mark while a Transcription is waiting;
- the date the Note was made, the length of its Recordings, and its Tags in
  their own colours.

Under that are the opening lines of the Note's text and of its main
Transcription.

**The section.** The chevron, or the 🔊 mark, opens a section under the row, on
every Note whether or not it holds Recordings. When the Note has more than one
Recording, numbered buttons (1, 2, …) choose which one the player is on. Then
the file name and a small player, which starts playing when the section opens.
Last are the buttons for that one Note: **Tags**, which opens its Tags screen,
and **Delete**, which asks and then puts the Note in the trash bin.

**Tag colours.** Every Tag has a colour of its own without anybody choosing
one: the first six characters of the MD5 hash of its name, read as a colour.
The same Tag is therefore the same colour on the phone, on the desktop, and on
any device you add later, with nothing stored and nothing synced. To choose a
colour instead, open Settings → **Manage Tags**, press a Tag's menu and pick
**Colour…**; the choice is stored with the account's settings and syncs.
**Use the calculated colour** returns to the calculated one. The label on a Tag
is black or white depending on how bright its colour is, so it stays readable
either way. Settings → Advanced → **Draw Tags in their own colours** turns the
colours off, and Tags are then plain text.

**Searching.** The search field is hidden until you press the search icon.
**Back** closes it and shows the whole list again, rather than leaving the
application. The Tags offered for searching are read again every time the list
comes to the front, so a Tag created a moment ago — here or on another device
— is there.

## Inside a Note

The toolbar reads left to right as: back to the list, **Previous note**, **Next
note**, the Note's date and time, and **⋮** (**More**). A long press on the date
shows when the Note was created and last modified.

The **⋮** menu holds:

- **Voice recording**, with a red microphone, which adds a Recording to this
  Note. It is left out while the recorder is already open.
- **Share**; see [Sharing](#sharing).
- **History**, the Note's earlier versions, each with **Restore this version**.
- **Delete Note**, which asks and then puts the Note in the trash bin.

The Note's text is a plain box: tapping it starts editing and puts the cursor
in it, and the tick in the toolbar saves. There is no separate pencil to find.
While editing, the back arrow becomes an X that cancels the edit.

Above the text are the star, the Tags button ("Manage tags") and the Note's
Tags.

Tapping the Tags button opens the **Tags screen** for that Note: every Tag, with
the ones the Note carries ticked. The filter at the top is *not* focused when
the screen opens — this screen is for tapping Tags, and a keyboard over it hides
most of them. Tap the filter yourself when a hierarchy has grown too large to
scan. The **+** in its toolbar creates a Tag and puts it on this Note at once —
the same dialog used everywhere a Tag is created, so whatever is added to it
later appears in all of them.

A Note's Recordings are listed oldest first, by when each recording started or,
when that is not known, when it was imported. Which of them stands for the Note
is yours to choose: see
[The main recording and the main transcription](#the-main-recording-and-the-main-transcription).

Settings → **Playback** can start that Recording playing as soon as the Note is
opened. It is off by default: a Note opened in company should not begin talking
on its own.

## The main recording and the main transcription

A Note can hold several Recordings, and a Recording can hold several
Transcriptions — the first pass, a second one made with a better model, a
corrected one. Only one of each can be shown where there is room for one: the
row in the Notes list shows a few lines of *a* Transcription, and opening a Note
plays *a* Recording. Which one is yours to say.

**A Note's main recording.** Inside the Note, each Recording in the player's
list has a star beside its transcribe mark ("Make this the main Recording" /
"The main Recording of this Note"). Press it to make that Recording the one the
Note stands for: it is played when the Note is opened (if that setting is on),
it is the one the small player in the Notes list starts on, and it is the one
whose Transcription is shown under the Note in the list. Pressing the star of
the Recording that already has it takes the mark off, and the Note goes back to
its oldest Recording, which is what an unmarked Note uses.

**A Recording's main transcription.** The same choice is in the ⓘ dialog under
each Transcription, as **Main Transcription** ("Shown first here, and under the
note in the list"). The one marked is shown first among that Recording's
Transcriptions, and it is the one whose opening lines appear under the Note in
the list. Without a mark, the first Transcription is used.

Both marks travel: they are stored with the Note and synced, so a phone and a
desktop agree on which Recording and which Transcription a Note is represented
by.

## What a Transcription says about itself

Under every Transcription are two buttons: copy ("Copy this Transcription"),
which puts the whole of it on the clipboard, and **ⓘ** ("About this
Transcription"), which opens what is known about it.

That dialog holds when the Transcription was made, by which service, with which
model, and in which language — and when the language was detected rather than
chosen, what the service decided it was, which is what explains a Transcription
that came back in the wrong one.

### How it ran

For a Transcription made on this phone, the dialog also says what the work
cost: the length of the Recording, how long the transcribing took and how that
compares with real time, the CPU time and roughly how many cores were kept busy,
the peak memory (whisper.cpp allocates outside the application's own heap, and
that peak is what decides whether a model fits on a phone at all), the size of
the model file, the beam size, the sizes of the recording file and of the 16 kHz
copy made for Whisper, and which phone and Android version carried out the work.

These are recorded with every Transcription, so two models, two beam sizes or
two phones can be compared on real recordings afterwards rather than guessed
at. A Transcription made before this was recorded, or on another device, shows
only what it has.

### Flags

Under those are the Transcription's **flags**: five things that may or may not
be true of it. Press a line to turn it on or off.

| Flag | Means |
|------|-------|
| Original | Unmodified transcription from the service |
| Verified | User has verified the transcription is accurate |
| Verbatim | Transcription includes filler words, false starts, etc. |
| Cleaned | Transcription has been cleaned up (remove filler words) |
| Polished | Transcription has been edited for readability |

They are flags rather than one state because any number of them can be true at
once: a Transcription can be verified *and* cleaned, and a verbatim one that is
later tidied stops being verbatim and becomes cleaned. Nothing in the
application sets them for you except *Original*, which is true of every
Transcription until somebody edits it — they are a record of what a person has
changed or checked, kept for the person who reads it next.

The flags are written into the Transcription itself and travel with it, so the
desktop application shows the same five, in the same words, and a Transcription
verified on the phone reads as verified there. (In the database and in the sync
protocol the field is still called `state`, which is what it has always been
called; only what you read calls them flags.)

## Sharing

**Share**, in a Note's **⋮** menu, passes what is in the Note to another
application.

When the Note holds one thing — its text and nothing else, or a single
Recording — it goes straight out, because there is nothing to decide. When it
holds more, the dialog **Share** opens: the Note's text, each Recording, and
each finished Transcription under the Recording it belongs to, so it is clear
what belongs to what. Tick what should go and press **Share**.

Ticking a Recording leaves its Transcriptions unticked, and ticking a
Transcription leaves out the audio: each is a thing of its own. Text goes as
text and Recordings go as attachments, which is what a mail application turns
into a message with files on it. A Recording whose file is not on this phone is
not offered — its Transcription still is, since the words are here.

### Sharing into Voice

**Voice** appears in other applications' share menus for text and for audio
files.

- **Text** (a message, a web page's address, a mail) becomes a new Note holding
  that text, and the Note opens. When the other application also gives a
  subject, such as a page's title, the subject is the Note's first line and
  the text follows after an empty line, unless the text already starts with
  the subject.
- **An audio file** becomes a new Note holding that Recording, exactly as a file
  imported from a folder does (**Import Audio**), and the Note opens. The file
  keeps the name the other application gave it; when that name has no
  extension, the extension of the file's type is added, because the extension
  decides the format. When this account already holds a Recording with the
  same file name and the same bytes, nothing is added: its Note opens, with the
  message "… is already in Voice: this is its note".
- A file in a format Voice does not import is not added, and a message says so.

Images cannot be shared into Voice yet.

## The trash bin

Deleting a Note is reversible: the Note keeps its history and its Recordings,
and the other devices are told it was deleted rather than told to forget it.
**Settings → Trash** lists those Notes, drawn as the Notes list draws them, with
"Deleted \<date>" under each. Tapping one opens it, and a deleted Note shows the
same line at its top.

**Recover** puts a Note back in the list, on this phone and on every device.

**Delete permanently** is the one thing in the application that destroys a Note.
It asks "Remove this Note for good?", and **Delete for good** then removes the
Note, its history, its Tag links, and the Recordings and Transcriptions that
belong to that Note alone, here and on every device this phone syncs with, and
deletes those Recordings' files from this phone's audio folder, each found by
the name its row stores. It
cannot be undone, and a device that has not synced yet cannot bring the Note
back: the removal is written down and travels, and anything that arrives about
a removed Note is dropped. A Recording that another Note still holds is kept.

## Reading and moving about

Voice is laid out left to right on every phone, also when the phone's language
is written right to left, such as Hebrew: buttons, rows and dialogs keep the
places this manual describes. What you write keeps its own direction: a Note in
Hebrew is drawn right to left.

Inside a Note, **Previous note** and **Next note**, beside the back arrow, step
to the previous and the next Note in the list you were looking at, search and
star filter included. They point up and down rather than left and right, because
that is how the list runs. Holding
one shows that Note's row from the list, at the full width of the screen and
with twice as many lines of text as the list itself shows, so a glance is enough
to decide whether to go there.

How many lines the list shows is yours to choose: **Settings → Advanced → Lines
in the Notes list**, from one to six lines of the Note's text and of its
Transcription.

### Time format

**Settings → Advanced → Time format** sets how every date and time in the
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
smaller than the date. A timestamp is read for its day far more often than for
its minute, and the smaller time lets a reader find the day first without hiding
the minute. Which part of the line is the time comes from the format itself, so
it works for a free-form pattern as well, in whatever order that pattern puts
the two.

The thirteenth, **Custom (Advanced date format)**, shows a free-text field,
**Advanced date format**, holding a `java.text.SimpleDateFormat` pattern. The
field is hidden until then, so the screen stays quiet for everybody else, and
the line "Now: " under it shows the pattern rendered as you type it.

#### The pattern

Any `SimpleDateFormat` pattern works. The letters used most here:

| Letter | Means | Example |
|--------|-------|---------|
| `yyyy` / `yy` | Year | 2026 / 26 |
| `MMMM` / `MMM` / `MM` | Month as name, short name, number | September / Sep / 09 |
| `dd` / `d` | Day of the month | 03 / 3 |
| `EEEE` / `EEE` | Day of the week | Thursday / Thu |
| `HH` / `H` | Hour, 24-hour clock | 14 |
| `hh` / `h` | Hour, 12-hour clock | 02 / 2 |
| `mm` | Minute | 05 |
| `ss` | Second | 00 |
| `a` | AM or PM | PM |
| `zzz` / `Z` | Time zone, name or offset | GMT+03:00 / +0300 |
| `'text'` | Text kept as it is | `'at'` → at |

Month and day names follow the phone's language, so `EEEE` reads
`יום חמישי` on a Hebrew phone even though the examples above are English.

#### One token of our own: `-N`

`-N` becomes the number of **whole days ago**, counted in calendar days rather
than in twenty-four-hour periods: something written at 23:50 yesterday reads
`-1` at 00:10 today, which is what a person means by "yesterday". A time still
to come reads `-0` rather than a positive number.

```
yyyy-MM-dd HH:mm EEE -N   →   2026-09-05 14:03 Sat -2
```

That token is the default custom pattern. `SimpleDateFormat` throws an error on
an unknown pattern letter, and `N` is one, so the application rewrites the
pattern with a quoted private-use character (`U+E000`) in its place, formats it,
and puts the day count in afterwards. The count is wrapped in directional
isolates (`U+2066`, `U+2069`) so that the minus stays to the left of the digits
on a Hebrew or Arabic phone, where a lone minus would otherwise be reordered.

To add another token later — `-H` for hours, say — use the same technique with a
different private-use marker.

#### What cannot go wrong

A pattern that `SimpleDateFormat` will not parse (easy to type in a free-text
field) falls back to the default, `d MMM yyyy, HH:mm`, rather than breaking the
screen.

Times are still drawn in the zone they were written in: a Note recorded at 15:20
in Jerusalem reads 15:20 in whatever format you choose, from anywhere. The
days-ago count compares that moment's calendar day, in its own zone, with today
here.

### Playback

**Settings → Playback** holds what happens when a Note is opened and the speed
Recordings play at. **Play the main recording when a Note is opened**, under
**Automatic playback**, starts the Note's main Recording as the Note opens; it
is off by default, since a Note opened in company should not begin talking on
its own. **Speed** sets one speed, from 0.5× to 3×, for every player.

A Recording started in the Notes list **carries on playing** when you open the
Note, from where it had reached, and goes on playing while you visit that
Note's Tags screen. There is one player, so starting something else stops what
was playing rather than playing over it.

Under any waveform: tap to jump, or **drag** to scrub — the position follows
your finger while it moves, so a passage can be found by ear without lifting
and trying again.

### Interface size

**Settings → Advanced → Interface size** has three choices. **Standard** is the
size the application has always had. **Large** draws every text and every icon
one and a half times as large, for reading and tapping while moving, on a horse
or in a vehicle, where the standard size cannot be hit; less fits on the screen,
which is the point. **Standard, with a button to switch** starts standard and
puts a button in the Notes toolbar ("Large size" / "Standard size"), so the
switch is made when it is needed, and it applies to every screen.

### Icon size

**Settings → Advanced → Icon size** sizes the marks on a Note by themselves: the
recording and transcription marks at the left of each row in the Notes list,
the transcribe mark beside a Recording inside a Note, and the two buttons under
a Transcription. **Small** is the size they have always been, **Medium** is one
and a half times as large, and **Large** is one and a half times as large as
Medium. Each choice shows the mark at its own size beside it, so the choice is
made by looking rather than by guessing.

It is a separate setting from the interface size because the two wants are
separate: the marks say what a Note holds and are tapped to open a Recording, so
they are worth enlarging on their own, while the text stays at the size that
fits most Notes on the screen. Choosing a large interface as well multiplies the
two.

## Recording

A Recording is always made inside a Note, and the Note comes first: **New Voice
Recording** creates the Note and opens it with the recorder in it, and **Voice
recording** in any Note's **⋮** menu adds another Recording to a Note that
already exists. There is no separate recorder screen, so a Recording never has
to be moved into a Note afterwards, and the text, Tags and Recordings of a Note
are all edited in one place.

The recorder sits where the player of a Note with Recordings sits, and reads the
same way: the elapsed time in `hh:mm:ss`, a live waveform, a large red
Record/Pause button with a small **Discard** to its left and a small **Save** to
its right, on every phone, like the buttons of a tape recorder. A line under them gives the
recorder's state, which microphone it uses and in which format it records.

The first press of Record asks for permission to use the microphone.

**Discard** asks before throwing anything away, and its question is where
**Restart** lives: **Discard** drops the Recording, **Restart** drops it and
begins again from `00:00:00`, **Cancel** changes nothing. One destructive
button on the screen, not two.

A Note created only to hold a Recording is deleted — it goes to the trash bin —
when the Recording is discarded or never made, so backing out leaves no empty
Notes in the list. A Note that already had text or Recordings is never removed
this way.

Recording carries on when the application is left or the phone is locked, and
a notification shows "Recording" and the elapsed time. Opening the Note again
shows the recorder at the point it has reached. Opening the recorder for a new
Recording always starts from `00:00:00`: what the last Recording left on screen
belongs to that Recording, not to this one. The phone records in one Note at a
time; another Note says "A recording is already in progress in another note."

**Settings → Recorder** holds:

- **The New button creates**: **A new Note** or **A new voice Recording**.
- **Start recording as soon as the screen opens**.
- **During a telephone call**: **Pause, and carry on when the call ends**, or
  **Keep recording (the call is recorded as silence)**.
- **Recording format**: **Opus, 128 kb/s, 48 kHz (.ogg)** (the default),
  **Opus, speech, 32 kb/s (.ogg)**, **AAC, 96 kb/s, 44.1 kHz (.m4a)**, or
  **WAV, 16 kHz, 16-bit mono (.wav)**, each with its size per minute.
- The card **Microphones**.

### Microphones

**Settings → Recorder → Microphones** is a screen of its own. A phone has three
or four microphones and each has a name to give and a level meter to watch,
which took so much room in the recorder settings that everything under them
went unseen. The Recorder screen shows which microphone is chosen and opens this
screen when tapped.

There, each microphone can be named in its **Friendly name** field ("bottom",
"top, near the ear"), one of them chosen for recording, and **Test** pressed
while speaking: the bar shows how loudly that microphone hears you and the peak
shows the loudest point so far. Speak near each edge and corner of the phone to
learn where each one hears best. **System default microphone** leaves the choice
to Android.

### Where Recordings are kept

Recordings are ordinary files, in one folder that holds recordings and nothing
else.

- Once **all files access** is granted (Android 11 and newer), the folder is the
  shared `Recordings/Voice`, which stays when the application is removed.
- Until then, it is the application's own folder under `Android/data`, which
  Android deletes when the application is removed.

Settings → card **Audio Files Storage** shows the folder in use. **Choose
Folder** picks another one; a folder outside the application's own storage needs
all files access, and the phone offers **Open Settings** to grant it. **Reset to
Default** returns to the default folder. The card also says "✓ All files access
granted", or "⚠ All files access not granted" with a **Grant** button. A chosen
folder is used only while all files access is granted and the folder exists.

A file this application records is named by when it started and the end of its
id, for example `2026_09_21_14_30_59-abcdefgh.ogg`. An imported file keeps its
own name. A file already in the folder is never overwritten.

Importing a folder skips a file the account already holds: a recording that is not
deleted, with the same file name and the same bytes. The result says how many
were imported and how many were already imported; the same bytes under another
name are imported as a recording of their own.

## Long recordings

A Note holds a Recording of any length. An eight-hour recording of somebody
sleeping is kept, played, tagged, searched and synced exactly like a two-minute
voice note; nothing about it is a special case.

Two things are **not started without asking** for a long Recording, because both
mean reading the whole of it:

**The waveform.** Drawing one means decoding the entire recording, which a
phone decodes at perhaps fifty to two hundred times real time — so an hour of
audio is tens of seconds of work, and it holds one of the phone's three hardware
decoders for all of it. For a recording past the limits below, the waveform area
reads

> Click to generate waveform
> Resource intensive operation on large file

and the waveform is drawn when you press it. The levels it is drawn from are
then **kept with the Recording and synced**, so no device of the account has to
decode that Recording again, and a Recording whose waveform any device has drawn
shows its waveform at once, without the button.

**Transcription, past ten minutes.** This phone transcribes Recordings of up to
ten minutes. Ask for a longer one and it answers with its length, the limit, and
where the work can be carried out instead:

> This recording is 22:00 long. A phone transcribes up to 10 minutes;
> transcribe it on the desktop after the next sync.

Nothing is lost by asking — the Recording is untouched, and it transcribes
normally on the desktop, which has no limit, once the two have synced. On the
desktop, `transcribe-backlog` finds every Recording the phone passed over and
transcribes them in one run; see *Transcribing what the phone could not* in the
desktop manual.

The reason is memory: Whisper holds the whole recording as it works, about
230 MB an hour on top of a model of one to three gigabytes. Ten minutes is 38 MB
and comfortable; an hour is not, and several hours is beyond any phone.

### What counts as a large recording

| Measure | Limit |
|---------|-------|
| Length | 60 minutes |
| Size | 100 MiB |

Either one is enough. The length is the better measure, because the work tracks
the length of the audio; the size catches a recording whose length is not known
yet, or whose header is wrong. In the Notes list both are checked. In a Note's
player only the size is checked, because the length is not known until the
player has read the file. For reference, 100 MiB is about 52 minutes of 16 kHz
WAV, 1.8 hours of Opus at 128 kb/s, or 2.4 hours of AAC at 96 kb/s.

Both numbers are in `util/MagicNumbers.kt` as `LONG_RECORDING_SECONDS` and
`LARGE_RECORDING_BYTES`, and the desktop application uses the same two.

## On-device transcription

**Settings → Transcription** lists the Whisper models the application can copy
to the phone (ggml files from Hugging Face). "Models are stored in the app's
private storage and removed when the app is uninstalled."

| Model | Size | Notes |
|-------|------|-------|
| Whisper large-v3 (5-bit) | 1.08 GB | The default. Recommended. Hebrew, English, Arabic, Russian and 95 other languages |
| ivrit.ai large-v3-turbo (Hebrew) | 1.62 GB | Fine-tuned on Hebrew; the most accurate choice for Hebrew; Hebrew only |
| ivrit.ai large-v3 (Hebrew, full size) | 3.10 GB | Full-size Hebrew fine-tune; needs about 4 GB of free memory while it runs; Hebrew only |
| Whisper large-v3 (full size) | 3.10 GB | Needs about 4 GB of free memory while it runs |
| Whisper large-v3-turbo (5-bit) | 0.57 GB | Several times faster than large-v3, slightly less accurate on Hebrew |
| Whisper medium (5-bit) | 0.54 GB | Smaller and faster; noticeably weaker than large-v3 on Hebrew |

Each model's card has **Download**, which copies the model file to the phone
with a progress bar; **Cancel**, which stops the copy and keeps what arrived, so
the next **Download** continues from there; and **Delete** once it is on the
phone. A model can be chosen with its radio button once its file is complete.

Under **Decoding**, pick **Beam search (5 beams)** (most accurate, several times
slower) or **Greedy** (fastest). Then open a Note and tap the transcribe mark
("Transcribe \<file name>") at the left of one of its Recordings in the player.
Several Notes can be transcribed at once from the Notes list: long-press to
select them and press **Transcribe**.

The dialog **Transcribe** offers **Model** (only the models on the phone), one
button per language under **Transcribe in**, a **Language** or **Another
language** drop-down, and **Settings**, **Transcribe** and **Cancel**. When the
Recording already has a Transcription, the dialog warns first: transcribing it
again adds another Transcription beside the ones it has.

The result is a normal Transcription record (service `local_whisper`) and syncs
to every other device.

### Languages

No language is built into the application. Settings → Transcription lists every
language it can be told to use, and each one is set to one of three things:

| State | Where it appears |
|-------|------------------|
| **Button** | A button of its own in the Transcribe dialog: one press transcribes in that language |
| **In the list** | Behind the drop-down in that dialog |
| **Not used** | Nowhere |

The list starts with buttons for Hebrew, English and *Detect automatically*, and
Arabic, Russian, Spanish and Greek in the drop-down; everything else is off
until you switch it on. **Show all \<n> languages** shows the whole catalogue.
The language marked with the radio button is the one used when nothing else is
chosen, and it can only be one that is switched on.

### The transcription queue

**Settings → Transcription queue.** Transcription runs one Recording at a time —
Whisper wants about a gigabyte of memory and every core, so two at once are
slower than two in turn — and a long Recording takes minutes, so a queue of a
dozen Notes is an hour of work. This screen shows that work: what is waiting,
what is running, and what the finished work cost.

Three groups, read downwards as time runs forwards:

| Group | What it is |
|---|---|
| Waiting | The Recordings that have not started. Newest first, each saying its real place in the queue and how long until it is finished |
| Processing | The Recording being transcribed now, with the stage it is at |
| Completed | What is finished, newest first, with what the work cost |

Every row names the Note the Recording belongs to. Tapping that line shows the
Note exactly as the Notes list draws it — the same preview a held-down Previous
or Next gives inside a Note — and tapping the preview opens the Note.

A finished row carries the numbers this screen exists for:

| Measure | What it says |
|---|---|
| Text | How many characters the Transcription came to |
| Recording | How long the Recording was |
| Clock time | How long you waited, conversion and model loading included |
| Processor time | How much processor time it used, which is more than the clock time when several cores work at once |
| Cores busy | Roughly how many cores it kept busy |
| Memory, peak | The most memory it held, which is what decides whether a model fits on this phone at all |
| Speed | Seconds of audio per second of work |
| Model | The model it used |

Those numbers are what the waiting estimates are calculated from: the estimate
is the median of what **this phone** has actually finished with that model, not
a specification. Until something has finished here, no estimate is offered,
because a made-up number is worse than none.

**Transcribe this one next.** When you need one Transcription before the rest,
the up arrow on a waiting row ("Transcribe this one next") moves it to the front
of the queue. The Recording being worked on is never interrupted: it is minutes
into work that would have to start again, which would cost more than the wait.
"Next" means next after that one. The X ("Take out of the queue") takes a
waiting Recording out of the queue, and **Stop all** empties the queue and stops
the one running.

The desktop application has the same queue for its own work; see its user
manual.

### While a Transcription is waiting

A Recording whose Transcription has been asked for but has not arrived shows the
transcribe mark with a clock over it, in the Notes list and in the Note. A
Transcription that failed has no clock: waiting will not turn it into a
Transcription, so the error is shown instead.

Transcription text can be selected with a long press, and the copy button under
it copies the whole Transcription.

### While it runs

Transcription continues with the screen off, while you are in another
application, and after the application is swiped out of the recent-apps list. A
twenty-minute recording is minutes of solid work for the phone, so this is the
difference between a Transcription that finishes and one that never finishes.

The notification in the shade names the Recording, says how many are waiting
behind it, and carries a **Stop** button. Stop abandons the whole queue: whisper
is asked to stop between windows of audio and returns within about a second, the
partial text is thrown away, and the pending record of the stopped Recording is
deleted, leaving the Recording as it was. On Android 13 and newer, the
application asks for permission to show notifications each time it opens until
the permission is granted; without it the work still runs, but there is nothing
to see and no Stop button.

Asking for a Transcription while the application is not on screen — from the
ADB tool, with the phone locked — answers "Transcription will start when the app
is open", and the Recording waits in the queue until it is. Nothing is written
down until the work really begins, so an attempt that never started leaves no
"Pending..." row behind.

If the application is killed while a Transcription is running (a reboot, or the
phone reclaiming memory), the record it left says "Error: the app was closed
before the transcription finished". Once that Recording has really been
transcribed, the message is deleted: a Recording keeps its Transcriptions, not
the history of what interrupted them.

## While a recording plays

The Recording being played appears in the notification drawer, with how far
through it is, the Note it belongs to, and three things to press: **Pause**
(which becomes **Play** once paused), **Stop**, and the notification itself,
which opens the Note the Recording is in.

It is not only a convenience. Android stops an ordinary application's work when
it leaves the screen, and this notification belongs to a foreground service,
which is what keeps a Recording playing with the screen off or another
application in front. Stop ends playback and takes the notification down; Pause
keeps both, so playback can be continued from the drawer.

## Calculating missing data

Settings → **Calculate missing data**.

Some facts about a Recording are not known when it arrives, and some were never
calculated: a Recording imported before lengths were recorded has no length, a
Recording copied without its dates has no creation date, and a Note written
before the display caches existed has no cache. None of it is lost — it can be
read back off the file, or computed again — but until it is, the application
shows less than it knows, and the decisions that depend on a length (whether a
waveform is drawn without asking, whether the phone will transcribe a Recording)
have nothing to go on.

The screen counts what is missing first and changes nothing until you tap
**Calculate what can be calculated**, because reading the length of every
Recording is work and you should see what it is for before it starts. The
refresh button at the top right ("Count again") counts again.

What it calculates:

| What | Where it comes from |
|---|---|
| Recordings with no length recorded | the file's header, read without decoding the audio, so an eight-hour recording costs the same as a one-minute one |
| Recordings with no creation date | the file's date, or the date in the name the recording arrived under, by the same rule the desktop uses |
| Notes with no display cache | recomputed from the Note, its Tags and its Recordings |

What it never calculates, and says so instead:

| What | Why not |
|---|---|
| Recordings whose file is not on this phone | there is nothing here to read. Download them first, or run this on the device that has them |
| Recordings with no timezone recorded | a timezone cannot be derived from a file, and writing this phone's offset would state something false about where the recording was made |

Running it twice is harmless: the second run finds nothing left to calculate.
Nothing it writes is an edit by you, so none of it touches a Note's history; the
values reach your other devices as ordinary metadata on the next sync.

The desktop application has the same repair, in the same words, on all four of
its interfaces.

## Where the logs are

Both logs are files in the application's private storage, and both are read
from the buttons at the bottom of Settings.

- **View Log** opens **Application Log**: "Log file: \<path>" and the most recent
  lines of `voice.log`, which records the steps the application takes.
  When the file grows past 1 MB, only its newest 2,500 lines are kept.
- **Critical Log** opens **Critical Log**: "Log file: \<path>" and the most recent
  lines of `critical.log`, or "No critical errors logged". It records a file
  that failed to import, a Recording that failed to save (IMPORT_FAILED), and a
  sync operation, upload or full re-sync that failed (SYNC_ERROR). **Clear Log**
  empties it at once, without asking. When the file grows past 1 MB, only its
  newest 1,000 lines are kept.
