// The phone app and its backend on one local server, for trying it without Google:
//   node mobile/test/fakeServer.mjs [port]
// It serves mobile/pwa and answers /exec with the real Code.gs over an in-memory Sheet, already published with a
// demo driver (link printed on start) and two vehicles. Imported by the end-to-end test too.
import http from 'node:http';
import {readFile} from 'node:fs/promises';
import {extname, join, normalize} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createBackend} from './appsScript.mjs';

const ROOT = fileURLToPath(new URL('../pwa/', import.meta.url));
const TYPES = {'.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png',
    '.webmanifest': 'application/manifest+json'};

export function startFakeServer(port = 0) {
    const backend = createBackend();
    backend.doPost({
        action: 'publish', secret: backend.secret,
        drivers: [{token: 'demo-token', driverId: 1, name: 'Ana Pérez'}],
        vehicles: [{vehicleId: 7, plate: '1234 KLM', label: '1234 KLM · Renault Kangoo', lastKm: 120423},
            {vehicleId: 8, plate: '5678 XYZ', label: '5678 XYZ · Renault Clio', lastKm: 54039}]
    });
    const state = {backend, offline: false, posts: []};
    const server = http.createServer(async (request, response) => {
        const url = new URL(request.url, 'http://localhost');
        if (url.pathname === '/exec') {
            if (state.offline) {
                request.socket.destroy();
                return;
            }
            let answer;
            if (request.method === 'POST') {
                let body = '';
                for await (const chunk of request) {
                    body += chunk;
                }
                state.posts.push(JSON.parse(body));
                answer = backend.doPost(body);
            } else {
                answer = backend.doGet(Object.fromEntries(url.searchParams));
            }
            response.writeHead(200, {'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*'});
            response.end(JSON.stringify(answer));
            return;
        }
        const path = normalize(join(ROOT, url.pathname === '/' ? 'index.html' : url.pathname));
        if (!path.startsWith(ROOT)) {
            response.writeHead(404).end();
            return;
        }
        try {
            const body = await readFile(path);
            response.writeHead(200, {'Content-Type': TYPES[extname(path)] || 'application/octet-stream'});
            response.end(body);
        } catch (e) {
            response.writeHead(404).end();
        }
    });
    return new Promise(resolve => server.listen(port, '127.0.0.1', () => {
        const base = `http://localhost:${server.address().port}`;
        resolve({server, state, base, link: `${base}/?api=${encodeURIComponent(base + '/exec')}&d=demo-token`});
    }));
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
    const {link, state} = await startFakeServer(Number(process.argv[2] || 8765));
    console.log('Phone app with a demo driver: ' + link);
    console.log('Admin secret of the fake backend: ' + state.backend.secret);
}
