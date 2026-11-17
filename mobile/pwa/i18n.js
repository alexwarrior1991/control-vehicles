// Textos de la app del conductor: español, o portugués si el móvil está en portugués.
const TEXTS = {
    es: {
        openLink: 'Abre el enlace personal que te ha enviado la oficina para empezar.',
        hello: name => `Hola, ${name}`,
        vehicle: 'Vehículo',
        startKm: 'Km del cuentakilómetros',
        lastKm: km => `Último registrado: ${km} km`,
        start: 'EMPIEZO',
        running: (plate, time) => `En curso desde las ${time} · ${plate}`,
        endKm: 'Km al terminar (opcional)',
        tolls: 'Peajes en € (opcional)',
        end: 'TERMINO',
        cancel: 'Cancelar este viaje (lo empecé por error)',
        confirmCancel: '¿Cancelar este viaje? No se guardará.',
        locating: 'Cogiendo la ubicación…',
        started: 'Viaje empezado. Pulsa TERMINO al llegar.',
        ended: 'Viaje guardado. ¡Gracias!',
        cancelled: 'Viaje cancelado.',
        noGps: 'Sin ubicación GPS: se guarda igualmente.',
        chooseVehicle: 'Elige el vehículo.',
        badKm: 'Escribe los km del cuentakilómetros (solo números).',
        badEndKm: 'Los km al terminar no pueden ser menos que al empezar.',
        badTolls: 'Los peajes deben ser un importe, por ejemplo 3,10.',
        saved: 'Todo guardado ✓',
        pending: n => `Pendiente de enviar: ${n} (sin conexión, se enviará solo)`,
        sending: 'Enviando…',
        rejected: error => `La oficina no ha aceptado un dato: ${error}`,
        unknownLink: 'Este enlace ya no es válido: pide uno nuevo a la oficina.',
        noConnection: 'Sin conexión: puedes seguir, se enviará al volver la cobertura.'
    },
    pt: {
        openLink: 'Abra o link pessoal que o escritório lhe enviou para começar.',
        hello: name => `Olá, ${name}`,
        vehicle: 'Veículo',
        startKm: 'Km do conta-quilómetros',
        lastKm: km => `Último registado: ${km} km`,
        start: 'COMEÇO',
        running: (plate, time) => `Em curso desde as ${time} · ${plate}`,
        endKm: 'Km ao terminar (opcional)',
        tolls: 'Portagens em € (opcional)',
        end: 'TERMINO',
        cancel: 'Cancelar esta viagem (comecei por engano)',
        confirmCancel: 'Cancelar esta viagem? Não será guardada.',
        locating: 'A obter a localização…',
        started: 'Viagem iniciada. Carregue em TERMINO ao chegar.',
        ended: 'Viagem guardada. Obrigado!',
        cancelled: 'Viagem cancelada.',
        noGps: 'Sem localização GPS: é guardada na mesma.',
        chooseVehicle: 'Escolha o veículo.',
        badKm: 'Escreva os km do conta-quilómetros (só números).',
        badEndKm: 'Os km ao terminar não podem ser menos do que ao começar.',
        badTolls: 'As portagens devem ser um valor, por exemplo 3,10.',
        saved: 'Tudo guardado ✓',
        pending: n => `Por enviar: ${n} (sem ligação, será enviado sozinho)`,
        sending: 'A enviar…',
        rejected: error => `O escritório não aceitou um dado: ${error}`,
        unknownLink: 'Este link já não é válido: peça um novo ao escritório.',
        noConnection: 'Sem ligação: pode continuar, será enviado quando houver rede.'
    }
};

const LANGUAGE = (navigator.language || 'es').toLowerCase().startsWith('pt') ? 'pt' : 'es';

function t(key, ...args) {
    const text = TEXTS[LANGUAGE][key];
    return typeof text === 'function' ? text(...args) : text;
}
