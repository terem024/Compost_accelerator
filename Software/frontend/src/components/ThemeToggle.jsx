import { Moon, Sun } from 'lucide-react';
import useAppearance from '../hooks/useAppearance.js';

export default function ThemeToggle() {
  const [appearance, updateAppearance] = useAppearance();
  const light = appearance.theme === 'light';
  return (
    <button type="button" className="theme-toggle" role="switch" aria-checked={light}
      aria-label="Light mode" title={light ? 'Switch to dark mode' : 'Switch to light mode'}
      onClick={() => updateAppearance({ theme: light ? 'dark' : 'light' })}>
      {light ? <Sun size={20} aria-hidden="true" /> : <Moon size={20} aria-hidden="true" />}
    </button>
  );
}
