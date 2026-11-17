// The phone app end to end, in a phone-sized Chromium with a fake GPS, against the fake server:
//   node mobile/test/e2e.mjs [screenshot folder]
// Needs Playwright (require('playwright')) and its Chromium.
import assert from 'node:assert/strict';
import {mkdirSync} from 'node:fs';
import {createRequire} from 'node:module';
import {startFakeServer} from './fakeServer.mjs';

const {chromium} = createRequire(import.meta.url)('playwright');
const shots = process.argv[2];
if (shots) {
    mkdirSync(shots, {recursive: true});
}

const {server, state, link} = await startFakeServer();
const browser = await chromium.launch();
try {
    for (const locale of ['es-ES', 'pt-PT']) {
        const context = await browser.newContext({
            viewport: {width: 390, height: 780}, deviceScaleFactor: 2, isMobile: true, hasTouch: true, locale,
            geolocation: {latitude: 40.416775, longitude: -3.70379, accuracy: 12}, permissions: ['geolocation']
        });
        const page = await context.newPage();
        page.on('dialog', dialog => dialog.accept());
        const spanish = locale === 'es-ES';
        const shot = async name => shots && page.screenshot({path: `${shots}/${locale}-${name}.png`});

        await page.goto(link);
        await page.getByText(spanish ? 'Hola, Ana Pérez' : 'Olá, Ana Pérez').waitFor();
        await page.selectOption('#vehicle', '7');
        await page.getByText(spanish ? 'Último registrado: 120.423 km' : 'Último registado: 120 423 km').waitFor();
        await shot('1-idle');

        // Sin km no empieza.
        await page.click('#start');
        await page.locator('#message.error').waitFor();

        const before = state.posts.length;
        await page.fill('#start-km', '120430');
        await page.click('#start');
        await page.getByText(spanish ? 'Viaje empezado' : 'Viagem iniciada', {exact: false}).waitFor();
        await page.getByText(spanish ? 'Todo guardado ✓' : 'Tudo guardado ✓').waitFor();
        const started = state.posts[before];
        assert.equal(started.action, 'start');
        assert.equal(started.km, 120430);
        assert.equal(started.lat, 40.416775);
        assert.equal(String(started.vehicleId), '7');
        assert.match(started.at, /^\d{4}-\d\d-\d\dT\d\d:\d\d$/);
        await shot('2-running');

        // El viaje en curso sobrevive a cerrar la app.
        await page.reload();
        await page.locator('#screen-running:not([hidden])').waitFor();

        // Sin cobertura al terminar: se queda pendiente y se envía al volver.
        state.offline = true;
        await context.setGeolocation({latitude: 39.8628, longitude: -4.0273, accuracy: 8});
        await page.fill('#end-km', '120503');
        await page.fill('#tolls', '3,10');
        await page.click('#end');
        await page.getByText(spanish ? 'Pendiente de enviar: 1' : 'Por enviar: 1', {exact: false}).waitFor();
        await shot('3-pending');
        state.offline = false;
        await page.evaluate(() => window.dispatchEvent(new Event('online')));
        await page.getByText(spanish ? 'Todo guardado ✓' : 'Tudo guardado ✓').waitFor();
        await page.locator('#screen-idle:not([hidden])').waitFor();
        await shot('4-saved');

        const trip = state.backend.doGet({action: 'export', secret: state.backend.secret}).trips
            .find(t => t.id === started.id);
        assert.equal(trip.status, 'CLOSED');
        assert.equal(trip.endKm, '120503');
        assert.equal(trip.tolls, '3.10');
        assert.equal(trip.endLat, '39.8628');

        // Un viaje empezado por error se cancela.
        await page.fill('#start-km', '120503');
        await page.click('#start');
        await page.locator('#screen-running:not([hidden])').waitFor();
        await page.click('#cancel');
        await page.locator('#screen-idle:not([hidden])').waitFor();
        await page.getByText(spanish ? 'Todo guardado ✓' : 'Tudo guardado ✓').waitFor();
        await context.close();
        console.log(`${locale}: ok`);
    }

    // Sin enlace, la app dice qué hacer; con un enlace revocado, también.
    const context = await browser.newContext({viewport: {width: 390, height: 780}, locale: 'es-ES'});
    const page = await context.newPage();
    await page.goto(link.replace(/\?.*/, ''));
    await page.getByText('Abre el enlace personal').waitFor();
    await page.goto(link.replace('demo-token', 'revoked'));
    await page.getByText('Este enlace ya no es válido').waitFor();
    await context.close();
    console.log('link checks: ok');
} finally {
    await browser.close();
    server.close();
}
