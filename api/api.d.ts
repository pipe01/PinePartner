declare interface Watch {
    get address(): string;
    get isConnected(): boolean;

    sendNotification(title: string, body: string): void;
    setTime(time: Number | Date): void;
    getService(uuid: string): BLEService | null;
    setCurrentWeather(weather: Weather): void;
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
    get artist(): string | null;
    get title(): string | null;
    get album(): string | null;
}

declare interface Location {
    get latitude(): number;
    get longitude(): number;
    get altitude(): number | null;
    get accuracy(): number | null;
}

declare type Weather = {
    temp: number;
    minTemp: number;
    maxTemp: number;
    location: string;
    icon: "sun" | "fewClouds" | "cloudy" | "heavyClouds" | "cloudsRain" | "rain" | "thunderstorm" | "snow" | "mist";
}

declare interface WatchesService {
    get all(): Watch[];

    addEventListener(event: "connected", cb: (watch: Watch) => void): void;
    addEventListener(event: "disconnected", cb: (watchAddress: string) => void): void;

    removeEventListener(event: "connected", cb: (watch: Watch) => void): void;
    removeEventListener(event: "disconnected", cb: (watchAddress: string) => void): void;
}

declare interface NotificationsService {
    addEventListener(event: "received", cb: (notif: Notification) => void): void;
    removeEventListener(event: "received", cb: (notif: Notification) => void): void;
}

declare type HTTPMethod = "GET" | "POST" | "PUT" | "DELETE" | "PATCH" | "HEAD" | "OPTIONS";

declare type HTTPOptions = {
    headers?: Record<string, string>;
}

declare interface HTTPService {
    request(method: HTTPMethod, url: string): string;
    request(method: HTTPMethod, url: string, options: HTTPOptions): string;
    request(method: HTTPMethod, url: string, cb: (response: string) => void): void;
    request(method: HTTPMethod, url: string, options: HTTPOptions, cb: (response: string) => void): void;
}

declare interface VolumeService {
    get voiceCallStream(): VolumeStream;
    get systemStream(): VolumeStream;
    get ringStream(): VolumeStream;
    get musicStream(): VolumeStream;
    get alarmStream(): VolumeStream;
    get notificationStream(): VolumeStream;
    get accessibilityStream(): VolumeStream;
}

declare interface MediaService {
    get state(): PlaybackState;

    play(): void;
    pause(): void;
    next(): void;
    previous(): void;
}

declare interface LocationService {
    getCurrent(priority?: "highAccuracy" | "balanced" | "lowPower" | "passive"): Location
    getLast(): Location | null
}

declare interface TimerService {
    setInterval(cb: () => void, periodMs: number): number;
    setTimeout(cb: () => void, delayMs: number): number;
    clear(id: number): void;
}

declare function require(module: "watches"): WatchesService;
declare function require(module: "notifications"): NotificationsService;
declare function require(module: "http"): HTTPService;
declare function require(module: "volume"): VolumeService;
declare function require(module: "media"): MediaService;
declare function require(module: "location"): LocationService;

declare const params: Record<string, any>;
declare const timer: TimerService;

declare interface Console {
    assert(condition?: boolean, ...data: any[]): void;
    count(label?: string): void;
    countReset(label?: string): void;
    debug(...data: any[]): void;
    error(...data: any[]): void;
    info(...data: any[]): void;
    log(...data: any[]): void;
    time(label?: string): void;
    timeEnd(label?: string): void;
    timeLog(label?: string, ...data: any[]): void;
    trace(...data: any[]): void;
    warn(...data: any[]): void;
}

declare const console: Console;