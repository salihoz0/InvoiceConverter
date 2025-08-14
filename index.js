const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

class InvoiceConverter {
  constructor() {
    this.jarPath = path.join(__dirname, 'InvoiceConverter.jar');
  }

  convertToHtml(xmlContent, isBase64Input = false) {
    return this._runJava(xmlContent, 'html', isBase64Input);
  }

  async convertToPdfBase64(xmlContent, isBase64Input = false) {
    const result = await this._runJava(xmlContent, 'pdf', isBase64Input);
    return this._validatePdfBase64(result);
  }

  _validatePdfBase64(base64String) {
    try {
      const cleanBase64 = base64String.replace(/\s/g, '');
      const buffer = Buffer.from(cleanBase64, 'base64');

      if (buffer.subarray(0, 4).toString() !== '%PDF') {
        const pdfStartIndex = buffer.indexOf('%PDF');
        if (pdfStartIndex >= 0) {
          return buffer.subarray(pdfStartIndex).toString('base64');
        }
        throw new Error('Invalid PDF content received');
      }

      return cleanBase64;
    } catch (error) {
      throw new Error(`PDF validation failed: ${error.message}`);
    }
  }

  _runJava(xmlContent, mode, isBase64Input = false) {
    return new Promise((resolve, reject) => {
      let output = '';
      let errorOutput = '';

      const java = spawn('java', [
        '-cp',
        [this.jarPath, path.join(__dirname, 'lib', '*')].join(process.platform === 'win32' ? ';' : ':'),
        'InvoiceConverter',
        mode,
        isBase64Input.toString()
      ]);

      java.stdin.on('error', err => {
        if (err.code !== 'EPIPE') reject(err);
      });

      java.stdout.on('data', data => output += data.toString());
      java.stderr.on('data', data => errorOutput += data.toString());

      java.on('close', code => {
        if (code === 0) resolve(output.trim());
        else reject(new Error(errorOutput || `Java process exited with code ${code}`));
      });

      java.on('error', err => reject(new Error(`Failed to start Java process: ${err.message}`)));

      java.stdin.write(xmlContent, 'utf8', () => java.stdin.end());
    });
  }

  static savePdfFromBase64(base64String, outputPath) {
    fs.writeFileSync(outputPath, Buffer.from(base64String, 'base64'));
  }
}

module.exports = InvoiceConverter;
