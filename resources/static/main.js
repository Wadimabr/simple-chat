let socket = getWebSocket();
let timerID;

document.forms.publish.onsubmit = function () {
    let outgoingMessage = this.message.value.trim();
    if (socket.readyState === 1 && outgoingMessage) {
        socket.send(outgoingMessage);
        this.message.value = "";
    }
    return false;
};

function reconnect() {
    socket = null;
    socket = getWebSocket();
}

function getWebSocket() {
    let url = new URL("/chat", window.location.href);
    url.protocol = url.protocol.replace("http", "ws");
    let path = url.href;
    let s = new WebSocket(path);
    s.onopen = function (event) {
        clearInterval(timerID);
    }

    s.onmessage = function (event) {
        let message = event.data;

        let messageElem = document.createElement('div');
        messageElem.textContent = message;
        document.getElementById('messages').prepend(messageElem);
    };

    s.onerror = function (event) {
        timerID = setInterval(reconnect, 10 * 1000)
    };

    s.onclose = function (closeEvent) {
        console.log(closeEvent.reason)
        if (closeEvent.code !== 1008) {
            timerID = setInterval(reconnect, 10 * 1000)
        }
    };

    return s;
}