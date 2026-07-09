// Brik Pro - Main Entry Point
import React from 'react'
import ReactDOM from 'react-dom/client'
import { Editor } from '@/components/editor/Editor'
import '@/styles/index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Editor />
  </React.StrictMode>
)