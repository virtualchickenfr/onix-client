const fs = require('fs');
const path = require('path');

/** Small JSON store for settings and the account. */
class Store {
  constructor(file, defaults) {
    this.file = file;
    this.data = { ...defaults };
    try {
      if (fs.existsSync(file)) {
        Object.assign(this.data, JSON.parse(fs.readFileSync(file, 'utf8')));
      }
    } catch (err) {
      console.warn('Could not read settings:', err);
    }
  }

  get(key) {
    return this.data[key];
  }

  set(key, value) {
    if (value === undefined) delete this.data[key];
    else this.data[key] = value;
    this.save();
  }

  save() {
    fs.mkdirSync(path.dirname(this.file), { recursive: true });
    fs.writeFileSync(this.file, JSON.stringify(this.data, null, 2));
  }
}

module.exports = { Store };
