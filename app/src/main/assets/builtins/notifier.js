// ==PinePartner Plugin==
// @id notifications-forwarder
// @name Notifications Forwarder
// @description Forwards your phone's notifications to all watches
// @permission RECEIVE_NOTIFICATIONS
// ==/PinePartner Plugin==

const notifs = require("notifications")
const watches = require("watches")

notifs.addEventListener("received", o => {
    console.log("Received notification:", o)

    if (o.isAllowed)
        watches.all.forEach(w => w.sendNotification(o.title + " (" + o.appLabel + ")", o.text))
})