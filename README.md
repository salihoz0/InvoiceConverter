# Invoice XML to PDF Converter

XML faturalarını HTML ve PDF formatlarına dönüştüren Node.js paketi. E-Arşiv, E-Fatura ve UBL formatlarını destekler.

## Özellikler

- XML fatura dosyalarını HTML'ye dönüştürme
- XML fatura dosyalarını PDF'ye dönüştürme (base64 formatında)
- E-Arşiv, E-Fatura ve UBL formatlarını destekleme
- Türkçe karakterler için tam destek
- Base64 input desteği
- Command line interface (CLI) desteği

## Gereksinimler

Bu paket çalışabilmesi için aşağıdaki yazılımların sisteminizde kurulu olması gerekir:

### Java JDK 11+
Java kurulumu için:

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install openjdk-11-jdk
```

**CentOS/RHEL/Fedora:**
```bash
sudo dnf install java-11-openjdk-devel
# veya RHEL/CentOS için: sudo yum install java-11-openjdk-devel
```

**Kurulumu kontrol edin:**
```bash
java -version
javac -version
```

### wkhtmltopdf
PDF çıktısı için wkhtmltopdf gereklidir:
- [wkhtmltopdf İndirme Sayfası](https://wkhtmltopdf.org/downloads.html)

## Kurulum

```bash
npm install invoice-xml-to-pdf-converter
```

Kurulum sonrası gereksinimler otomatik olarak kontrol edilir.

## Kullanım

### Node.js'de Modül Olarak

```javascript
const InvoiceConverter = require('invoice-xml-to-pdf-converter');
const fs = require('fs');

const converter = new InvoiceConverter();

async function convertInvoice() {
  try {
    // XML dosyasını oku
    const xmlContent = fs.readFileSync('fatura.xml', 'utf8');
    
    // HTML'ye dönüştür
    const htmlContent = await converter.convertToHtml(xmlContent);
    console.log('HTML çıktısı:', htmlContent);
    
    // PDF'ye dönüştür (base64 formatında)
    const pdfBase64 = await converter.convertToPdfBase64(xmlContent);
    
    // PDF'yi dosyaya kaydet
    InvoiceConverter.savePdfFromBase64(pdfBase64, 'fatura.pdf');
    console.log('PDF başarıyla oluşturuldu: fatura.pdf');
    
  } catch (error) {
    console.error('Hata:', error.message);
  }
}

convertInvoice();
```

### Base64 XML Girişi İle

```javascript
const InvoiceConverter = require('invoice-xml-to-pdf-converter');
const converter = new InvoiceConverter();

async function convertBase64Invoice() {
  try {
    const xmlBase64 = 'PHhtbCB2ZXJzaW9uPSIxLjAiIC4uLg=='; // Base64 XML
    
    // Base64 XML'i PDF'ye dönüştür
    const pdfBase64 = await converter.convertToPdfBase64(xmlBase64, true);
    
    // PDF'yi kaydet
    InvoiceConverter.savePdfFromBase64(pdfBase64, 'fatura.pdf');
    
  } catch (error) {
    console.error('Hata:', error.message);
  }
}

convertBase64Invoice();
```

### Command Line Interface (CLI)

Paketi global olarak kurarsanız CLI olarak da kullanabilirsiniz:

```bash
npm install -g invoice-xml-to-pdf-converter
invoice-xml-to-pdf-converter
```

## API Referansı

### `new InvoiceConverter()`
Yeni bir dönüştürücü örneği oluşturur.

### `convertToHtml(xmlContent, isBase64Input)`
XML içeriğini HTML formatına dönüştürür.

**Parametreler:**
- `xmlContent` (string): XML içeriği
- `isBase64Input` (boolean, isteğe bağlı): XML'in base64 formatında olup olmadığı

**Döndürür:** Promise<string> - HTML içeriği

### `convertToPdfBase64(xmlContent, isBase64Input)`
XML içeriğini PDF formatına dönüştürür ve base64 string olarak döndürür.

**Parametreler:**
- `xmlContent` (string): XML içeriği
- `isBase64Input` (boolean, isteğe bağlı): XML'in base64 formatında olup olmadığı

**Döndürür:** Promise<string> - Base64 formatında PDF

### `InvoiceConverter.savePdfFromBase64(base64String, outputPath)`
Base64 PDF stringini dosyaya kaydeder.

**Parametreler:**
- `base64String` (string): Base64 formatında PDF içeriği
- `outputPath` (string): Çıktı dosyasının yolu

## Hata Yönetimi

```javascript
try {
  const pdfBase64 = await converter.convertToPdfBase64(xmlContent);
  // Başarılı işlem
} catch (error) {
  if (error.message.includes('Java process')) {
    console.error('Java hatası: Java kurulu mu kontrol edin');
  } else if (error.message.includes('PDF validation')) {
    console.error('PDF oluşturma hatası: wkhtmltopdf kurulu mu kontrol edin');
  } else {
    console.error('Genel hata:', error.message);
  }
}
```

## Desteklenen XML Formatları

- E-Arşiv XML formatı
- E-Fatura UBL formatı
- Diğer Türkçe fatura XML formatları

## Lisans

MIT

## Katkıda Bulunma

1. Fork edin
2. Feature branch oluşturun (`git checkout -b feature/yeni-ozellik`)
3. Değişikliklerinizi commit edin (`git commit -am 'Yeni özellik eklendi'`)
4. Branch'i push edin (`git push origin feature/yeni-ozellik`)
5. Pull Request oluşturun

---

# Invoice XML to PDF Converter (English)

A Node.js package that converts XML invoices to HTML and PDF formats. Supports E-Archive, E-Invoice, and UBL formats.

## Features

- Convert XML invoice files to HTML
- Convert XML invoice files to PDF (base64 format)
- Support for E-Archive, E-Invoice, and UBL formats
- Full support for Turkish characters
- Base64 input support
- Command line interface (CLI) support

## Requirements

This package requires the following software to be installed on your system:

### Java JDK 11+
For Java installation:

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install openjdk-11-jdk
```

**CentOS/RHEL/Fedora:**
```bash
sudo dnf install java-11-openjdk-devel
# or for RHEL/CentOS: sudo yum install java-11-openjdk-devel
```

**Verify installation:**
```bash
java -version
javac -version
```

### wkhtmltopdf
wkhtmltopdf is required for PDF output:
- [wkhtmltopdf Download Page](https://wkhtmltopdf.org/downloads.html)

## Installation

```bash
npm install invoice-xml-to-pdf-converter
```

Requirements are automatically checked after installation.

## Usage

### As a Module in Node.js

```javascript
const InvoiceConverter = require('invoice-xml-to-pdf-converter');
const fs = require('fs');

const converter = new InvoiceConverter();

async function convertInvoice() {
  try {
    // Read XML file
    const xmlContent = fs.readFileSync('invoice.xml', 'utf8');
    
    // Convert to HTML
    const htmlContent = await converter.convertToHtml(xmlContent);
    console.log('HTML output:', htmlContent);
    
    // Convert to PDF (base64 format)
    const pdfBase64 = await converter.convertToPdfBase64(xmlContent);
    
    // Save PDF to file
    InvoiceConverter.savePdfFromBase64(pdfBase64, 'invoice.pdf');
    console.log('PDF successfully created: invoice.pdf');
    
  } catch (error) {
    console.error('Error:', error.message);
  }
}

convertInvoice();
```

### With Base64 XML Input

```javascript
const InvoiceConverter = require('invoice-xml-to-pdf-converter');
const converter = new InvoiceConverter();

async function convertBase64Invoice() {
  try {
    const xmlBase64 = 'PHhtbCB2ZXJzaW9uPSIxLjAiIC4uLg=='; // Base64 XML
    
    // Convert base64 XML to PDF
    const pdfBase64 = await converter.convertToPdfBase64(xmlBase64, true);
    
    // Save PDF
    InvoiceConverter.savePdfFromBase64(pdfBase64, 'invoice.pdf');
    
  } catch (error) {
    console.error('Error:', error.message);
  }
}

convertBase64Invoice();
```

### Command Line Interface (CLI)

If you install the package globally, you can also use it as CLI:

```bash
npm install -g invoice-xml-to-pdf-converter
invoice-xml-to-pdf-converter
```

## API Reference

### `new InvoiceConverter()`
Creates a new converter instance.

### `convertToHtml(xmlContent, isBase64Input)`
Converts XML content to HTML format.

**Parameters:**
- `xmlContent` (string): XML content
- `isBase64Input` (boolean, optional): Whether the XML is in base64 format

**Returns:** Promise<string> - HTML content

### `convertToPdfBase64(xmlContent, isBase64Input)`
Converts XML content to PDF format and returns as base64 string.

**Parameters:**
- `xmlContent` (string): XML content
- `isBase64Input` (boolean, optional): Whether the XML is in base64 format

**Returns:** Promise<string> - PDF in base64 format

### `InvoiceConverter.savePdfFromBase64(base64String, outputPath)`
Saves base64 PDF string to file.

**Parameters:**
- `base64String` (string): PDF content in base64 format
- `outputPath` (string): Output file path

## Error Handling

```javascript
try {
  const pdfBase64 = await converter.convertToPdfBase64(xmlContent);
  // Successful operation
} catch (error) {
  if (error.message.includes('Java process')) {
    console.error('Java error: Check if Java is installed');
  } else if (error.message.includes('PDF validation')) {
    console.error('PDF generation error: Check if wkhtmltopdf is installed');
  } else {
    console.error('General error:', error.message);
  }
}
```

## Supported XML Formats

- E-Archive XML format
- E-Invoice UBL format
- Other Turkish invoice XML formats

## License

MIT

## Contributing

1. Fork it
2. Create your feature branch (`git checkout -b feature/new-feature`)
3. Commit your changes (`git commit -am 'Add new feature'`)
4. Push to the branch (`git push origin feature/new-feature`)
5. Create a Pull Request
