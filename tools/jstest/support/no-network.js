'use strict';
// Every test file requires this first. Nothing in these tests may open a socket or resolve a host: the pages
// under test are eBay pages and this machine must not send eBay any traffic. jsdom is also configured not to
// load subresources, and page fetch/XMLHttpRequest are replaced by fakes (see harness.js).

const net = require('node:net');
const tls = require('node:tls');
const dns = require('node:dns');
const http = require('node:http');
const https = require('node:https');

function refuse(name) {
  return function refused() {
    throw new Error('Network access is disabled in the kestrel.js tests (' + name + ')');
  };
}

net.connect = refuse('net.connect');
net.createConnection = refuse('net.createConnection');
net.Socket.prototype.connect = refuse('net.Socket.connect');
tls.connect = refuse('tls.connect');
http.request = refuse('http.request');
http.get = refuse('http.get');
https.request = refuse('https.request');
https.get = refuse('https.get');
dns.lookup = refuse('dns.lookup');
dns.resolve = refuse('dns.resolve');
dns.promises.lookup = refuse('dns.promises.lookup');
dns.promises.resolve = refuse('dns.promises.resolve');
globalThis.fetch = refuse('fetch');
