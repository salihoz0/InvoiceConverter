const { exec } = require('child_process');

// Renk kodları
const colors = {
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  reset: '\x1b[0m',
};

// Sistem dilini kontrol et
const isTurkish = Intl.DateTimeFormat().resolvedOptions().locale.startsWith('tr');

function checkCommand(command, displayName) {
  return new Promise((resolve) => {
    exec(`${command} --version`, (err, stdout, stderr) => {
      if (err) {
        console.log(`${colors.red}${displayName}: ${isTurkish ? 'YOK' : 'NOT FOUND'}${colors.reset}`);

        if (displayName === 'Java') {
          console.log(`${colors.yellow}${
            isTurkish
              ? 'Lütfen https://adoptium.net/ veya https://www.oracle.com/java/ adresinden Java JDK 11+ kurun.'
              : 'Please install Java JDK 11+ from https://adoptium.net/ or https://www.oracle.com/java/.'
          }${colors.reset}`);
        } else if (displayName === 'wkhtmltopdf') {
          console.log(`${colors.yellow}${
            isTurkish
              ? 'Lütfen https://wkhtmltopdf.org/downloads.html adresinden uygun sürümü kurun.'
              : 'Please install the appropriate version from https://wkhtmltopdf.org/downloads.html.'
          }${colors.reset}`);
        }
        resolve(false);
      } else {
        console.log(`${colors.green}${displayName}: ${isTurkish ? 'VAR' : 'FOUND'}${colors.reset}`);
        resolve(true);
      }
    });
  });
}

async function main() {
  console.log(isTurkish ? 'Sistem kontrolleri başlatılıyor...\n' : 'Starting system checks...\n');

  await checkCommand('java', 'Java');
  await checkCommand('wkhtmltopdf', 'wkhtmltopdf');

  console.log(isTurkish ? '\nKontroller tamamlandı.' : '\nChecks completed.');
}

main();
