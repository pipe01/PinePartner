declare interface Watch {
    get address(): string;
    get isConnected(): boolean;

    sendNotification(title: string, body: string): void;
    setTime(time: Number | Date): void;
    getService(uuid: string): BLEService | null;
}

declare interface BLEService {
    get uuid(): String;

    getCharacteristic(characteristicUUID: string): BLECharacteristic | null;
}

declare interface BLECharacteristic {
    read(): ArrayBuffer;
    readString(): string;

    write(data: ArrayBuffer | Uint8Array | string | number): void;

    addEventListener(event: "newValue", cb: (value: ArrayBuffer) => void): void;
    removeEventListener(event: "newValue", cb: (value: ArrayBuffer) => void): void;
}

declare interface VolumeStream {
    volume: number;

    adjustVolume(direction: -1 | 0 | 1): void;
}

declare interface Notification {
    get packageName(): string;
    get appLabel(): string;
    get title(): string;
    get text(): string;
    get time(): any;
    get isAllowed(): boolean;
}

declare interface PlaybackState {
    get isPlaying(): boolean;
    get position(): number;
    get duration(): number;
    get artist(): string;
    get title(): string;
    get album(): string;
}

declare interface Watches {
    get all(): Watch[];

    addEventListener(event: "connected", cb: (watch: Watch) => void): void;
    addEventListener(event: "disconnected", cb: (watchAddress: string) => void): void;

    removeEventListener(event: "connected", cb: (watch: Watch) => void): void;
    removeEventListener(event: "disconnected", cb: (watchAddress: string) => void): void;
}

declare interface Notifications {
    addEventListener(event: "received", cb: (notif: Notification) => void): void;
    removeEventListener(event: "received", cb: (notif: Notification) => void): void;
}

declare type HTTPMethod = "GET" | "POST" | "PUT" | "DELETE" | "PATCH" | "HEAD" | "OPTIONS";

declare type HTTPOptions = {
    headers?: Record<string, string>;
}

declare interface HTTP {
    request(method: HTTPMethod, url: string, cb: (response: string) => void): void;
    request(method: HTTPMethod, url: string, options: HTTPOptions, cb: (response: string) => void): void;
}

declare interface Volume {
    get voiceCallStream(): VolumeStream;
    get systemStream(): VolumeStream;
    get ringStream(): VolumeStream;
    get musicStream(): VolumeStream;
    get alarmStream(): VolumeStream;
    get notificationStream(): VolumeStream;
    get accessibilityStream(): VolumeStream;
}

declare interface Media {
    get state(): PlaybackState;

    play(): void;
    pause(): void;
    next(): void;
    previous(): void;
}

declare function require(module: "watches"): Watches;
declare function require(module: "notifications"): Notifications;
declare function require(module: "http"): HTTP;
declare function require(module: "volume"): Volume;
declare function require(module: "media"): Media;
