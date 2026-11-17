// Control Vehicles · app del conductor.
//
// Dos botones: EMPIEZO y TERMINO. Cada pulsación coge la hora y la ubicación GPS y se pone en una cola en el
// móvil, que se envía a la oficina (Google Apps Script) en cuanto hay conexión: sin cobertura no se pierde nada.
// El enlace personal (?api=<url>&d=<token>) se guarda en el móvil la primera vez.

const STORE = 'cv-mobile';
const RETRY_EVERY_MS = 30000;

let state = load();
let sending = false;

function load() {
    try {
        return Object.assign({queue: [], vehicles: []}, JSON.parse(localStorage.getItem(STORE) || '{}'));
    } catch (e) {
        return {queue: [], vehicles: []};
    }
}

function save() {
    localStorage.setItem(STORE, JSON.stringify(state));
}

const $ = id => document.getElementById(id);

// --- arranque ----------------------------------------------------------------------------------------------------

function init() {
    document.querySelectorAll('[data-text]').forEach(el => {
        el.textContent = t(el.dataset.text);
    });
    document.documentElement.lang = LANGUAGE;

    const params = new URLSearchParams(location.search);
    if (params.get('api') && params.get('d')) {
        if (params.get('d') !== state.token) {
            // Un enlace nuevo: lo pendiente se conserva (cada envío lleva su enlace), lo demás se vuelve a pedir.
            state = {queue: state.queue || [], openTrip: state.openTrip, vehicles: []};
        }
        state.api = params.get('api');
        state.token = params.get('d');
        save();
    }

    $('start').addEventListener('click', startTrip);
    $('end').addEventListener('click', endTrip);
    $('cancel').addEventListener('click', cancelTrip);
    $('vehicle').addEventListener('change', showLastKm);
    window.addEventListener('online', flush);
    setInterval(flush, RETRY_EVERY_MS);

    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('sw.js').catch(() => {});
    }
    render();
    refreshConfig().finally(flush);
}

async function refreshConfig() {
    if (!state.api || !state.token) {
        return;
    }
    try {
        const config = await call('GET', {action: 'config', token: state.token});
        if (!config.ok) {
            if (config.error === 'Unknown link') {
                message(t('unknownLink'), true);
            }
            return;
        }
        state.driver = config.driver;
        state.vehicles = config.vehicles;
        // Un viaje abierto en otro móvil, o antes de borrar los datos del navegador; el local manda si lo hay.
        if (!state.openTrip && config.openTrip && !state.queue.length) {
            state.openTrip = config.openTrip;
        }
        save();
        render();
    } catch (e) {
        // Sin conexión: se sigue con lo guardado.
    }
}

// --- pantallas ---------------------------------------------------------------------------------------------------

function render() {
    const linked = Boolean(state.api && state.token);
    $('screen-link').hidden = linked;
    $('screen-idle').hidden = !linked || Boolean(state.openTrip);
    $('screen-running').hidden = !linked || !state.openTrip;
    $('hello').textContent = state.driver ? t('hello', state.driver.name) : '';

    const select = $('vehicle');
    const chosen = select.value || state.vehicleId;
    select.innerHTML = '';
    const empty = new Option('—', '');
    select.add(empty);
    (state.vehicles || []).forEach(v => select.add(new Option(v.label || v.plate, v.id)));
    if (chosen && (state.vehicles || []).some(v => String(v.id) === String(chosen))) {
        select.value = chosen;
    } else if ((state.vehicles || []).length === 1) {
        select.value = state.vehicles[0].id;
    }
    showLastKm();

    if (state.openTrip) {
        $('running-text').textContent = t('running', state.openTrip.plate, state.openTrip.startAt.slice(11, 16));
    }
    renderSync();
}

function showLastKm() {
    const vehicle = (state.vehicles || []).find(v => String(v.id) === $('vehicle').value);
    $('last-km').textContent = vehicle && vehicle.lastKm != null
        ? t('lastKm', Number(vehicle.lastKm).toLocaleString(LANGUAGE)) : '';
}

function renderSync() {
    const sync = $('sync');
    sync.classList.toggle('pending', state.queue.length > 0);
    sync.textContent = !state.token ? '' : sending ? t('sending')
        : state.queue.length ? t('pending', state.queue.length) : t('saved');
}

function message(text, error = false) {
    const box = $('message');
    box.textContent = text;
    box.classList.toggle('error', error);
    box.hidden = !text;
}

// --- acciones ----------------------------------------------------------------------------------------------------

async function startTrip() {
    const vehicle = (state.vehicles || []).find(v => String(v.id) === $('vehicle').value);
    if (!vehicle) {
        return message(t('chooseVehicle'), true);
    }
    const km = wholeNumber($('start-km').value);
    if (km === null) {
        return message(t('badKm'), true);
    }
    const position = await locate($('start'));
    const id = crypto.randomUUID();
    const at = localNow();
    enqueue({action: 'start', id, vehicleId: vehicle.id, at, km, ...position});
    state.vehicleId = vehicle.id;
    state.openTrip = {id, vehicleId: vehicle.id, plate: vehicle.plate, startAt: at, startKm: km};
    vehicle.lastKm = km;
    save();
    $('start-km').value = '';
    render();
    message(position.lat == null ? t('started') + ' ' + t('noGps') : t('started'));
    flush();
}

async function endTrip() {
    const trip = state.openTrip;
    const endKmText = $('end-km').value.trim();
    const km = endKmText ? wholeNumber(endKmText) : null;
    if (endKmText && km === null) {
        return message(t('badKm'), true);
    }
    if (km !== null && km < trip.startKm) {
        return message(t('badEndKm'), true);
    }
    const tollsText = $('tolls').value.trim().replace(',', '.');
    if (tollsText && !/^\d+(\.\d{1,2})?$/.test(tollsText)) {
        return message(t('badTolls'), true);
    }
    const position = await locate($('end'));
    enqueue({action: 'end', id: trip.id, at: localNow(), km, tolls: tollsText || null, ...position});
    const vehicle = (state.vehicles || []).find(v => String(v.id) === String(trip.vehicleId));
    if (vehicle && km !== null) {
        vehicle.lastKm = km;
    }
    state.openTrip = null;
    save();
    $('end-km').value = '';
    $('tolls').value = '';
    render();
    message(position.lat == null ? t('ended') + ' ' + t('noGps') : t('ended'));
    flush();
}

function cancelTrip() {
    if (!state.openTrip || !confirm(t('confirmCancel'))) {
        return;
    }
    enqueue({action: 'cancel', id: state.openTrip.id});
    state.openTrip = null;
    save();
    render();
    message(t('cancelled'));
    flush();
}

// --- GPS, cola y envío -------------------------------------------------------------------------------------------

/** The position, or empty coordinates if there is no GPS, no permission or no fix in 15 s: the trip is saved anyway. */
function locate(button) {
    button.disabled = true;
    message(t('locating'));
    return new Promise(resolve => {
        const done = position => {
            button.disabled = false;
            resolve(position);
        };
        if (!navigator.geolocation) {
            return done({lat: null, lng: null, accuracy: null});
        }
        navigator.geolocation.getCurrentPosition(
            p => done({lat: round(p.coords.latitude, 6), lng: round(p.coords.longitude, 6),
                accuracy: Math.round(p.coords.accuracy)}),
            () => done({lat: null, lng: null, accuracy: null}),
            {enableHighAccuracy: true, timeout: 15000, maximumAge: 30000});
    });
}

function enqueue(event) {
    state.queue.push(Object.assign({token: state.token}, event));
    save();
}

/** Sends the queue in order; stops at the first event without an answer and keeps it for the next try. */
async function flush() {
    if (sending || !state.queue.length || !state.api) {
        renderSync();
        return;
    }
    sending = true;
    renderSync();
    try {
        while (state.queue.length) {
            let answer;
            try {
                answer = await call('POST', state.queue[0]);
            } catch (e) {
                break;
            }
            const event = state.queue.shift();
            save();
            if (!answer.ok) {
                // La oficina lo rechaza (un enlace revocado, un vehículo retirado): reintentarlo no lo arregla.
                message(answer.error === 'Unknown link' ? t('unknownLink') : t('rejected', answer.error), true);
                if (event.action === 'start' && state.openTrip && state.openTrip.id === event.id) {
                    state.openTrip = null;
                    save();
                    render();
                }
            }
        }
    } finally {
        sending = false;
        renderSync();
    }
}

/** A call to the Apps Script web app. POST goes as text/plain so the browser sends it without a CORS preflight. */
async function call(method, data) {
    const response = method === 'GET'
        ? await fetch(state.api + '?' + new URLSearchParams(data))
        : await fetch(state.api, {method: 'POST', headers: {'Content-Type': 'text/plain;charset=utf-8'},
            body: JSON.stringify(data)});
    if (!response.ok) {
        throw new Error('HTTP ' + response.status);
    }
    return response.json();
}

// --- utilidades --------------------------------------------------------------------------------------------------

function wholeNumber(text) {
    const cleaned = String(text).trim().replace(/[.\s]/g, '');
    return /^\d{1,9}$/.test(cleaned) ? Number(cleaned) : null;
}

/** "2026-11-17T08:15": the phone's local time, as the driver lives it. */
function localNow() {
    const now = new Date();
    const pad = n => String(n).padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:`
        + pad(now.getMinutes());
}

function round(value, decimals) {
    const factor = 10 ** decimals;
    return Math.round(value * factor) / factor;
}

init();
