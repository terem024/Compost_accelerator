import { useEffect, useState } from 'react';

const KEY = 'compostAppearance';
let memory = { theme: 'dark', fontSize: 'standard' };
function read() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) || JSON.stringify(memory));
    return { theme: saved.theme === 'light' ? 'light' : 'dark',
      fontSize: ['standard', 'large', 'extra-large'].includes(saved.fontSize) ? saved.fontSize : 'standard' };
  } catch { return memory; }
}

export default function useAppearance() {
  const [appearance, setAppearance] = useState(read);
  useEffect(() => {
    const sync = () => setAppearance(read());
    window.addEventListener('compost-appearance', sync);
    window.addEventListener('storage', sync);
    return () => {
      window.removeEventListener('compost-appearance', sync);
      window.removeEventListener('storage', sync);
    };
  }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = appearance.theme;
    document.documentElement.dataset.fontSize = appearance.fontSize;
  }, [appearance]);
  function update(changes) {
    const next = { ...read(), ...changes };
    memory = next;
    try { localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* Still apply for this page. */ }
    setAppearance(next);
    window.dispatchEvent(new Event('compost-appearance'));
  }
  return [appearance, update];
}
