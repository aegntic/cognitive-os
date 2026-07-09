/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        brik: {
          bg: '#0a0a0a',
          panel: '#111',
          border: '#222',
          primary: '#8b5cf6',
          secondary: '#ec4899',
        }
      },
    },
  },
  plugins: [],
}