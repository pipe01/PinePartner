declare interface Watch {
    get address(): string;
    get isConnected(): Boolean;

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

    write(data: ArrayBuffer | Uint8Array | string): void;

    addEventListener(event: "newValue", cb: (value: ArrayBuffer) => void): void;
    removeEventListener(event: "newValue", cb: (value: ArrayBuffer) => void): void;
}

declare interface Notification {
    get packageName(): string;
    get appLabel(): string;
    get title(): string;
    get text(): string;
    get time(): any;
    get isAllowed(): Boolean;
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

declare function require(module: "watches"): Watches;
declare function require(module: "notifications"): Notifications;
declare function require(module: "http"): HTTP;
