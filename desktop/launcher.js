const form = document.querySelector('form');
form.addEventListener('submit', async event => {
  event.preventDefault();
  const button = document.querySelector('#connect');
  const status = document.querySelector('#status');
  button.disabled = true;
  status.textContent = '正在连接…';
  try {
    const result = await window.desktopReader.connect(document.querySelector('input').value.trim());
    status.textContent = result.ok ? '' : result.error;
  } catch {
    status.textContent = '连接失败，请重试。';
  } finally {
    button.disabled = false;
  }
});
document.querySelector('#quit').addEventListener('click', () => window.desktopReader.action('quit'));
