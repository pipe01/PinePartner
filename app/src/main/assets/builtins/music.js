// ==PinePartner Plugin==
// @id music
// @name Music control
// @description Control music playback and volume from your watch
// @permission VOLUME_CONTROL
// @permission MEDIA_CONTROL
// ==/PinePartner Plugin==

const watches = require("watches")
const audio = require("volume")
const media = require("media")

function isPlayingData(playing) {
	const data = new Uint8Array(1);
	data[0] = playing ? 1 : 0;
	return data;
}

function onWatchConnected(watch) {
	const musicService = watch.getService("00000000-78fc-48fe-8e23-433b3a1942d0");
	const events = musicService.getCharacteristic("00000001-78fc-48fe-8e23-433b3a1942d0");
	const status = musicService.getCharacteristic("00000002-78fc-48fe-8e23-433b3a1942d0");
	const artist = musicService.getCharacteristic("00000003-78fc-48fe-8e23-433b3a1942d0");
	const track = musicService.getCharacteristic("00000004-78fc-48fe-8e23-433b3a1942d0");
	const album = musicService.getCharacteristic("00000005-78fc-48fe-8e23-433b3a1942d0");
	const position = musicService.getCharacteristic("00000006-78fc-48fe-8e23-433b3a1942d0");
	const totalLength = musicService.getCharacteristic("00000007-78fc-48fe-8e23-433b3a1942d0");

	function sendState() {
        const state = media.state;

        status.write(isPlayingData(state.isPlaying));
        artist.write(state.artist || "");
        track.write(state.title || "");
        album.write(state.album || "");
        position.write(state.position);
        totalLength.write(state.duration);
    }

	events.addEventListener("newValue", val => {
		const arr = new Uint8Array(val);

		switch (arr[0]) {
			case 0xE0: // App opened
                sendState();
				break;

			case 0x00:
				console.log("Play");
				media.play();
				status.write(isPlayingData(true));
				break;

			case 0x01:
				console.log("Pause");
				media.pause();
				status.write(isPlayingData(false));
				break;

			case 0x03:
				console.log("Next");
				media.next();
				break;

			case 0x04:
				console.log("Previous");
				media.previous();
				break;

			case 0x05:
				console.log("Volume up");
				audio.musicStream.adjustVolume(1);
				break;

			case 0x06:
				console.log("Volume down");
				audio.musicStream.adjustVolume(-1);
				break;
		}
	});
}

watches.addEventListener("connected", onWatchConnected)
watches.all.forEach(onWatchConnected)
