/**
 * Control Vehicles · mobile backend.
 *
 * A Google Sheet is the mailbox between the drivers' phones and the desktop application:
 *  - the phones (mobile/pwa) send «start» and «end» of each trip with the time and the GPS position;
 *  - the desktop publishes its drivers and vehicles here, and pulls the finished trips («export» + «ack»).
 *
 * Install (see mobile/README.md): in the Sheet, Extensions → Apps Script, paste this file, run `setup` once,
 * then Deploy → New deployment → Web app, execute as «Me», access «Anyone».
 *
 * Every answer is JSON: {ok: true, ...} or {ok: false, error: "..."}. Writes from a phone need the driver's
 * token (their personal link); everything the desktop does needs ADMIN_SECRET (Script properties).
 */

var TRIPS = 'Trips';
var DRIVERS = 'Drivers';
var VEHICLES = 'Vehicles';
var TRIP_COLUMNS = ['id', 'driverId', 'driverName', 'vehicleId', 'plate', 'startAt', 'startLat', 'startLng',
  'startAccuracy', 'startKm', 'endAt', 'endLat', 'endLng', 'endAccuracy', 'endKm', 'tolls', 'status', 'imported',
  'updatedAt'];
var DRIVER_COLUMNS = ['token', 'driverId', 'name'];
var VEHICLE_COLUMNS = ['vehicleId', 'plate', 'label', 'lastKm'];

/** Run once from the editor: creates the three tabs and the admin secret, and shows the secret. */
function setup() {
  var book = SpreadsheetApp.getActiveSpreadsheet();
  [[TRIPS, TRIP_COLUMNS], [DRIVERS, DRIVER_COLUMNS], [VEHICLES, VEHICLE_COLUMNS]].forEach(function (tab) {
    var sheet = book.getSheetByName(tab[0]) || book.insertSheet(tab[0]);
    // Todo como texto: si no, la hoja convierte las fechas y los ids a su manera.
    sheet.getRange(1, 1, sheet.getMaxRows(), tab[1].length).setNumberFormat('@');
    sheet.getRange(1, 1, 1, tab[1].length).setValues([tab[1]]);
    sheet.setFrozenRows(1);
  });
  var properties = PropertiesService.getScriptProperties();
  var secret = properties.getProperty('ADMIN_SECRET');
  if (!secret) {
    secret = Utilities.getUuid().replace(/-/g, '') + Utilities.getUuid().replace(/-/g, '');
    properties.setProperty('ADMIN_SECRET', secret);
  }
  Logger.log('Admin secret (paste it in the desktop app, Settings → Mobile): ' + secret);
  return secret;
}

function doGet(e) {
  return respond(function () {
    return handleGet((e && e.parameter) || {});
  });
}

function doPost(e) {
  return respond(function () {
    var body = e && e.postData && e.postData.contents ? JSON.parse(e.postData.contents) : {};
    return handlePost(body);
  });
}

function handleGet(params) {
  switch (params.action) {
    case 'config': {
      var driver = driverOf(params.token);
      return {
        driver: {id: driver.driverId, name: driver.name},
        vehicles: rows(VEHICLES, VEHICLE_COLUMNS).map(function (v) {
          return {id: v.vehicleId, plate: v.plate, label: v.label, lastKm: v.lastKm === '' ? null : Number(v.lastKm)};
        }),
        openTrip: openTripOf(driver.driverId)
      };
    }
    case 'export':
      requireSecret(params.secret);
      return {
        trips: rows(TRIPS, TRIP_COLUMNS).filter(function (t) {
          return t.imported !== 'TRUE';
        })
      };
    default:
      throw new Error('Unknown action');
  }
}

function handlePost(body) {
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    switch (body.action) {
      case 'start':
        return start(driverOf(body.token), body);
      case 'end':
        return end(driverOf(body.token), body);
      case 'cancel':
        return cancel(driverOf(body.token), body);
      case 'publish':
        requireSecret(body.secret);
        replace(DRIVERS, DRIVER_COLUMNS, body.drivers || []);
        replace(VEHICLES, VEHICLE_COLUMNS, body.vehicles || []);
        return {drivers: (body.drivers || []).length, vehicles: (body.vehicles || []).length};
      case 'ack':
        requireSecret(body.secret);
        return {acknowledged: setForIds(body.ids || [], {imported: 'TRUE'})};
      default:
        throw new Error('Unknown action');
    }
  } finally {
    lock.releaseLock();
  }
}

/** A start resent by the phone (it retries until it hears back) finds its row and changes nothing. */
function start(driver, body) {
  requireId(body.id);
  if (findRow(body.id) > 0) {
    return {id: body.id, duplicate: true};
  }
  var vehicle = rows(VEHICLES, VEHICLE_COLUMNS).filter(function (v) {
    return String(v.vehicleId) === String(body.vehicleId);
  })[0];
  if (!vehicle) {
    throw new Error('Unknown vehicle');
  }
  var trip = {
    id: body.id, driverId: driver.driverId, driverName: driver.name, vehicleId: vehicle.vehicleId,
    plate: vehicle.plate, startAt: body.at, startLat: body.lat, startLng: body.lng, startAccuracy: body.accuracy,
    startKm: body.km, status: 'OPEN', imported: '', updatedAt: new Date().toISOString()
  };
  append(TRIPS, TRIP_COLUMNS, trip);
  return {id: body.id};
}

function end(driver, body) {
  var row = ownRow(driver, body.id);
  var trip = rowObject(TRIPS, TRIP_COLUMNS, row);
  if (trip.status !== 'OPEN') {
    return {id: body.id, duplicate: true};
  }
  update(TRIPS, TRIP_COLUMNS, row, {
    endAt: body.at, endLat: body.lat, endLng: body.lng, endAccuracy: body.accuracy, endKm: body.km,
    tolls: body.tolls, status: 'CLOSED', updatedAt: new Date().toISOString()
  });
  return {id: body.id};
}

function cancel(driver, body) {
  var row = ownRow(driver, body.id);
  update(TRIPS, TRIP_COLUMNS, row, {status: 'CANCELLED', updatedAt: new Date().toISOString()});
  return {id: body.id};
}

// --- helpers ---------------------------------------------------------------------------------------------------

function respond(work) {
  var answer;
  try {
    answer = work();
    answer.ok = true;
  } catch (e) {
    answer = {ok: false, error: String(e && e.message ? e.message : e)};
  }
  return ContentService.createTextOutput(JSON.stringify(answer)).setMimeType(ContentService.MimeType.JSON);
}

function requireSecret(secret) {
  var expected = PropertiesService.getScriptProperties().getProperty('ADMIN_SECRET');
  if (!expected || secret !== expected) {
    throw new Error('Wrong admin secret');
  }
}

function requireId(id) {
  if (!id || String(id).length > 64) {
    throw new Error('Missing trip id');
  }
}

function driverOf(token) {
  var driver = token ? rows(DRIVERS, DRIVER_COLUMNS).filter(function (d) {
    return d.token === token;
  })[0] : null;
  if (!driver) {
    throw new Error('Unknown link');
  }
  return driver;
}

function openTripOf(driverId) {
  var open = rows(TRIPS, TRIP_COLUMNS).filter(function (t) {
    return String(t.driverId) === String(driverId) && t.status === 'OPEN';
  });
  if (!open.length) {
    return null;
  }
  var t = open[open.length - 1];
  return {id: t.id, vehicleId: t.vehicleId, plate: t.plate, startAt: t.startAt, startKm: Number(t.startKm)};
}

/** The row of a trip of this driver; another driver's trip is as unknown as one that does not exist. */
function ownRow(driver, id) {
  requireId(id);
  var row = findRow(id);
  if (row < 0 || String(rowObject(TRIPS, TRIP_COLUMNS, row).driverId) !== String(driver.driverId)) {
    throw new Error('Unknown trip');
  }
  return row;
}

function sheet(name) {
  var found = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(name);
  if (!found) {
    throw new Error('Run setup first: there is no ' + name + ' tab');
  }
  return found;
}

function rows(name, columns) {
  var values = sheet(name).getDataRange().getValues();
  return values.slice(1).filter(function (r) {
    return r.join('') !== '';
  }).map(function (r) {
    return toObject(columns, r);
  });
}

function rowObject(name, columns, row) {
  return toObject(columns, sheet(name).getRange(row, 1, 1, columns.length).getValues()[0]);
}

function toObject(columns, values) {
  var object = {};
  columns.forEach(function (column, i) {
    object[column] = values[i] === undefined || values[i] === null ? '' : String(values[i]);
  });
  return object;
}

/** 1-based row of the trip, or -1. */
function findRow(id) {
  var values = sheet(TRIPS).getDataRange().getValues();
  for (var i = 1; i < values.length; i++) {
    if (String(values[i][0]) === String(id)) {
      return i + 1;
    }
  }
  return -1;
}

function append(name, columns, object) {
  var target = sheet(name);
  var row = target.getLastRow() + 1;
  var range = target.getRange(row, 1, 1, columns.length);
  range.setNumberFormat('@');
  range.setValues([columns.map(function (c) {
    return object[c] === undefined || object[c] === null ? '' : String(object[c]);
  })]);
}

function update(name, columns, row, changes) {
  var target = sheet(name);
  Object.keys(changes).forEach(function (key) {
    var column = columns.indexOf(key);
    if (column >= 0) {
      var cell = target.getRange(row, column + 1);
      cell.setNumberFormat('@');
      cell.setValue(changes[key] === undefined || changes[key] === null ? '' : String(changes[key]));
    }
  });
}

function setForIds(ids, changes) {
  var count = 0;
  ids.forEach(function (id) {
    var row = findRow(id);
    if (row > 0) {
      update(TRIPS, TRIP_COLUMNS, row, changes);
      count++;
    }
  });
  return count;
}

function replace(name, columns, objects) {
  var target = sheet(name);
  if (target.getLastRow() > 1) {
    target.getRange(2, 1, target.getLastRow() - 1, columns.length).clearContent();
  }
  if (objects.length) {
    var range = target.getRange(2, 1, objects.length, columns.length);
    range.setNumberFormat('@');
    range.setValues(objects.map(function (o) {
      return columns.map(function (c) {
        return o[c] === undefined || o[c] === null ? '' : String(o[c]);
      });
    }));
  }
}
