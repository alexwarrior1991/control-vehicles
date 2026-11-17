// Runs mobile/apps-script/Code.gs in Node with an in-memory Google Sheet, so that the tests and the local fake
// server use the real backend code. Only the Apps Script services Code.gs calls are imitated.
import {readFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import vm from 'node:vm';

class Sheet {
    constructor() {
        this.cells = [];
    }

    getMaxRows() {
        return 1000;
    }

    getLastRow() {
        let last = this.cells.length;
        while (last > 0 && (this.cells[last - 1] || []).every(v => v === '' || v === undefined)) {
            last--;
        }
        return last;
    }

    setFrozenRows() {
    }

    getDataRange() {
        const width = Math.max(1, ...this.cells.map(r => (r || []).length));
        return this.getRange(1, 1, Math.max(1, this.getLastRow()), width);
    }

    getRange(row, column, rows = 1, columns = 1) {
        const sheet = this;
        return {
            setNumberFormat() {
            },
            getValues() {
                const values = [];
                for (let r = 0; r < rows; r++) {
                    const line = [];
                    for (let c = 0; c < columns; c++) {
                        const v = (sheet.cells[row - 1 + r] || [])[column - 1 + c];
                        line.push(v === undefined ? '' : v);
                    }
                    values.push(line);
                }
                return values;
            },
            setValues(values) {
                values.forEach((line, r) => line.forEach((v, c) => sheet.set(row + r, column + c, v)));
            },
            setValue(v) {
                sheet.set(row, column, v);
            },
            clearContent() {
                for (let r = 0; r < rows; r++) {
                    for (let c = 0; c < columns; c++) {
                        sheet.set(row + r, column + c, '');
                    }
                }
            }
        };
    }

    set(row, column, value) {
        while (this.cells.length < row) {
            this.cells.push([]);
        }
        this.cells[row - 1][column - 1] = value;
    }
}

/** A fresh backend: an empty spreadsheet with `setup` already run. Returns {doGet, doPost, secret, book}. */
export function createBackend() {
    const sheets = new Map();
    const properties = new Map();
    const book = {
        getSheetByName: name => sheets.get(name) || null,
        insertSheet: name => {
            const sheet = new Sheet();
            sheets.set(name, sheet);
            return sheet;
        },
        sheets
    };
    const context = {
        SpreadsheetApp: {getActiveSpreadsheet: () => book},
        PropertiesService: {
            getScriptProperties: () => ({
                getProperty: key => properties.has(key) ? properties.get(key) : null,
                setProperty: (key, value) => properties.set(key, value)
            })
        },
        LockService: {getScriptLock: () => ({waitLock() {}, releaseLock() {}})},
        Utilities: {getUuid: () => randomUUID()},
        Logger: {log() {}},
        ContentService: {
            MimeType: {JSON: 'application/json'},
            createTextOutput: text => ({text, setMimeType() {
                return this;
            }})
        }
    };
    vm.createContext(context);
    vm.runInContext(readFileSync(new URL('../apps-script/Code.gs', import.meta.url), 'utf8'), context);
    const secret = context.setup();
    return {
        secret,
        book,
        doGet: parameter => JSON.parse(context.doGet({parameter}).text),
        doPost: body => JSON.parse(context.doPost({postData: {contents: typeof body === 'string' ? body
                    : JSON.stringify(body)}}).text)
    };
}
