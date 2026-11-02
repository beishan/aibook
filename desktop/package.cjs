// Portable packages reuse Electron's installed distribution; no packaging dependency.
const { cp, mkdir, readFile, writeFile, rename } = require('node:fs/promises');
const path = require('node:path');

async function main() {
  if (!['win32', 'darwin'].includes(process.platform)) throw new Error('请在 Windows 或 macOS 构建');
  const version = JSON.parse(await readFile(path.join(__dirname, '../frontend/package.json'), 'utf8')).version;
  const destination = path.join(__dirname, 'dist', `aibook-${version}-${process.platform}-${process.arch}`);
  // Fail rather than mixing files from two builds.
  await mkdir(destination, { recursive: false });
  const distribution = path.join(path.dirname(require.resolve('electron')), 'dist');
  await cp(distribution, destination, { recursive: true });
  let resources = path.join(destination, 'resources');
  if (process.platform === 'darwin') {
    const bundle = path.join(destination, 'Electron.app');
    const plist = path.join(bundle, 'Contents/Info.plist');
    const source = await readFile(plist, 'utf8');
    await writeFile(plist, source
      .replace(/(<key>CFBundleDisplayName<\/key>\s*<string>)[^<]*/, '$1汗牛充栋')
      .replace(/(<key>CFBundleName<\/key>\s*<string>)[^<]*/, '$1汗牛充栋')
      .replace(/(<key>CFBundleIdentifier<\/key>\s*<string>)[^<]*/, '$1com.aibook.desktop')
      .replace(/(<key>CFBundleShortVersionString<\/key>\s*<string>)[^<]*/, `$1${version}`));
    const targetBundle = path.join(destination, '汗牛充栋.app');
    await rename(bundle, targetBundle);
    resources = path.join(targetBundle, 'Contents/Resources');
  } else {
    await rename(path.join(destination, 'electron.exe'), path.join(destination, '汗牛充栋.exe'));
  }
  const application = path.join(resources, 'app');
  await mkdir(application, { recursive: true });
  for (const name of ['main.cjs', 'preload.cjs', 'policy.cjs', 'icons.cjs', 'drag.cjs', 'launcher.html', 'launcher.css', 'launcher.js']) {
    await cp(path.join(__dirname, name), path.join(application, name));
  }
  await writeFile(path.join(application, 'package.json'), JSON.stringify({
    name: 'aibook-desktop', productName: '汗牛充栋', version, main: 'main.cjs',
  }, null, 2));
  console.log(`便携应用已生成：${destination}`);
}

mkdir(path.join(__dirname, 'dist'), { recursive: true }).then(main).catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
