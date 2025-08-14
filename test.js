const fs = require('fs');
const InvoiceConverter = require('./index.js');
const converter = new InvoiceConverter();

(async () => {
  try {
    // Örnek XML içeriği (string olarak)
    const xmlString = fs.readFileSync('invoice.xml', 'utf-8');

    // HTML almak
    const html = await converter.convertToHtml(xmlString);
    console.log('HTML uzunluğu:', html.length);
    // HTML'i dilersen dosyaya yazabilirsin
    fs.writeFileSync('invoice.html', html, 'utf-8');

    // PDF (Base64) almak
    const pdfBase64 = await converter.convertToPdfBase64(xmlString);
    console.log('PDF Base64 uzunluğu:', pdfBase64.length);

    // PDF dosyaya yazmak
    fs.writeFileSync('invoice.pdf', Buffer.from(pdfBase64, 'base64'));
    console.log('invoice.pdf kaydedildi.');
  } catch (err) {
    console.error('Hata oluştu:', err);
  }
})();
