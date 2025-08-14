const { spawn } = require('child_process');
const path = require('path');

class InvoiceConverter {
  constructor() {
    this.jarPath = path.join(__dirname, 'InvoiceConverter.jar'); // jar dosyası
  }

  /**
   * XML string → HTML
   */
  convertToHtml(xmlContent, isBase64Input = false) {
    return this._runJava(xmlContent, 'html', isBase64Input);
  }

  /**
   * XML string → PDF Base64
   */
  convertToPdfBase64(xmlContent, isBase64Input = false) {
    return this._runJava(xmlContent, 'pdf', isBase64Input);
  }

  /**
   * Java'yı stdin üzerinden çalıştırır
   */
  _runJava(xmlContent, mode, isBase64Input = false) {
    
    return new Promise((resolve, reject) => {
      let output = '';
      let errorOutput = '';

const java = spawn('java', [
  '-cp',
  [
    path.join(__dirname, 'InvoiceConverter.jar'),
    path.join(__dirname, 'lib', '*')
  ].join(':'),
  'InvoiceConverter',
  mode,
  isBase64Input.toString()
]);



      java.stdin.on('error', (err) => {
        if (err.code === 'EPIPE') return;
        reject(err);
      });

      java.stdout.on('data', data => output += data.toString());
      java.stderr.on('data', data => errorOutput += data.toString());

      java.on('close', code => {
        if (code === 0) {
          resolve(output.trim());
        } else {
          reject(new Error(errorOutput || `Java process exited with code ${code}`));
        }
      });

      // XML'i stdin'e gönder ve bitir
      java.stdin.write(xmlContent, (err) => {
        if (err && err.code !== 'EPIPE') reject(err);
        java.stdin.end();
      });
    });
  }
}

module.exports = InvoiceConverter;
