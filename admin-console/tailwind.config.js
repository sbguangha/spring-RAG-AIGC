/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        display: ['"Bricolage Grotesk"', 'sans-serif'],
        body: ['"Spline Sans"', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      },
      colors: {
        ink: {
          50: '#f4f6f8',
          100: '#e8ecf0',
          200: '#c6cdd4',
          300: '#a3aeb8',
          400: '#5f6e7a',
          500: '#2a3844',
          600: '#1e2a33',
          700: '#161f26',
          800: '#0f161b',
          900: '#090d11',
          950: '#040608',
        },
        signal: {
          cyan: '#22d3ee',
          amber: '#fbbf24',
          rose: '#f43f5e',
          emerald: '#10b981',
          violet: '#8b5cf6',
        },
      },
      animation: {
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'scan': 'scan 4s linear infinite',
        'fade-in': 'fadeIn 0.5s ease-out forwards',
      },
      keyframes: {
        scan: {
          '0%': { transform: 'translateY(-100%)' },
          '100%': { transform: 'translateY(100%)' },
        },
        fadeIn: {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
      backgroundImage: {
        'grid-lines':
          "linear-gradient(to right, rgba(34, 211, 238, 0.06) 1px, transparent 1px), linear-gradient(to bottom, rgba(34, 211, 238, 0.06) 1px, transparent 1px)",
      },
    },
  },
  plugins: [],
}
