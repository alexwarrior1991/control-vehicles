// The Apps Script backend (mobile/apps-script/Code.gs), run in Node: node --test mobile/test
import test from 'node:test';
import assert from 'node:assert/strict';
import {createBackend} from './appsScript.mjs';

function published() {
    const backend = createBackend();
    const answer = backend.doPost({
        action: 'publish', secret: backend.secret,
        drivers: [{token: 'tok-ana', driverId: 1, name: 'Ana Pérez'}, {token: 'tok-joao', driverId: 2, name: 'João'}],
        vehicles: [{vehicleId: 7, plate: '1234 KLM', label: '1234 KLM · Renault Kangoo', lastKm: 120423}]
    });
    assert.equal(answer.ok, true);
    return backend;
}

const start = (overrides = {}) => ({
    action: 'start', token: 'tok-ana', id: 'trip-1', vehicleId: 7, at: '2026-11-17T08:15', lat: 40.4168,
    lng: -3.7038, accuracy: 12, km: 120430, ...overrides
});

test('the link of a driver gives their name, the vehicles and no open trip', () => {
    const backend = published();
    const config = backend.doGet({action: 'config', token: 'tok-ana'});
    assert.equal(config.ok, true);
    assert.equal(config.driver.name, 'Ana Pérez');
    assert.deepEqual(config.vehicles, [{id: '7', plate: '1234 KLM', label: '1234 KLM · Renault Kangoo', lastKm: 120423}]);
    assert.equal(config.openTrip, null);
    assert.deepEqual(backend.doGet({action: 'config', token: 'nope'}), {ok: false, error: 'Unknown link'});
});

test('a trip starts, is open for its driver and ends; resending changes nothing', () => {
    const backend = published();
    assert.equal(backend.doPost(start()).ok, true);
    assert.equal(backend.doPost(start()).duplicate, true, 'the phone retries: no second row');
    assert.deepEqual(backend.doGet({action: 'config', token: 'tok-ana'}).openTrip,
        {id: 'trip-1', vehicleId: '7', plate: '1234 KLM', startAt: '2026-11-17T08:15', startKm: 120430});
    assert.equal(backend.doGet({action: 'config', token: 'tok-joao'}).openTrip, null);

    const end = {action: 'end', token: 'tok-ana', id: 'trip-1', at: '2026-11-17T09:20', lat: 39.86, lng: -4.02,
        accuracy: 8, km: 120503, tolls: '3.10'};
    assert.equal(backend.doPost(end).ok, true);
    assert.equal(backend.doPost(end).duplicate, true);

    const [trip] = backend.doGet({action: 'export', secret: backend.secret}).trips;
    assert.equal(trip.status, 'CLOSED');
    assert.equal(trip.startAt, '2026-11-17T08:15', 'dates stay text');
    assert.equal(trip.endKm, '120503');
    assert.equal(trip.tolls, '3.10');
    assert.equal(trip.driverName, 'Ana Pérez');
    assert.equal(trip.plate, '1234 KLM');
});

test('another driver cannot end or cancel my trip', () => {
    const backend = published();
    backend.doPost(start());
    assert.deepEqual(backend.doPost({action: 'end', token: 'tok-joao', id: 'trip-1', at: 'x'}),
        {ok: false, error: 'Unknown trip'});
    assert.deepEqual(backend.doPost({action: 'cancel', token: 'tok-joao', id: 'trip-1'}),
        {ok: false, error: 'Unknown trip'});
});

test('a cancelled trip is no longer open and is exported as cancelled', () => {
    const backend = published();
    backend.doPost(start());
    assert.equal(backend.doPost({action: 'cancel', token: 'tok-ana', id: 'trip-1'}).ok, true);
    assert.equal(backend.doGet({action: 'config', token: 'tok-ana'}).openTrip, null);
    assert.equal(backend.doGet({action: 'export', secret: backend.secret}).trips[0].status, 'CANCELLED');
});

test('the desktop needs the secret, and acknowledged trips are not exported again', () => {
    const backend = published();
    backend.doPost(start());
    backend.doPost(start({id: 'trip-2', at: '2026-11-17T10:00'}));
    assert.deepEqual(backend.doGet({action: 'export', secret: 'wrong'}), {ok: false, error: 'Wrong admin secret'});
    assert.deepEqual(backend.doPost({action: 'publish', secret: 'wrong'}), {ok: false, error: 'Wrong admin secret'});
    assert.equal(backend.doPost({action: 'ack', secret: backend.secret, ids: ['trip-1', 'ghost']}).acknowledged, 1);
    assert.deepEqual(backend.doGet({action: 'export', secret: backend.secret}).trips.map(t => t.id), ['trip-2']);
});

test('publishing again replaces drivers and vehicles; an old link stops working', () => {
    const backend = published();
    backend.doPost({action: 'publish', secret: backend.secret, drivers: [{token: 'tok-new', driverId: 1, name: 'Ana'}],
        vehicles: []});
    assert.equal(backend.doGet({action: 'config', token: 'tok-ana'}).ok, false);
    assert.deepEqual(backend.doGet({action: 'config', token: 'tok-new'}).vehicles, []);
    assert.deepEqual(backend.doPost(start({token: 'tok-new'})), {ok: false, error: 'Unknown vehicle'});
});

test('bad requests are answered, never thrown', () => {
    const backend = published();
    assert.deepEqual(backend.doPost('not json'), {ok: false, error: backend.doPost('not json').error});
    assert.equal(backend.doPost('not json').ok, false);
    assert.deepEqual(backend.doGet({action: 'nothing'}), {ok: false, error: 'Unknown action'});
    assert.deepEqual(backend.doPost(start({id: ''})), {ok: false, error: 'Missing trip id'});
});
