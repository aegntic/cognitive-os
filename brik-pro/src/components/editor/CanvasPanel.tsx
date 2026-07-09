// Brik Pro - Canvas Panel
import { useRef, useEffect, useState } from 'react'
import type { Tool } from '@/lib/types'

interface CanvasPanelProps {
  tool: Tool | undefined
  onControllerChange: (id: string, value: unknown) => void
  className?: string
}

export function CanvasPanel({ tool, className = '' }: CanvasPanelProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const toolInstanceRef = useRef<any>(null)
  const animationRef = useRef<number>()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!tool || !canvasRef.current || !tool.code) return

    const canvas = canvasRef.current
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    // Set canvas size
    canvas.width = tool.canvasWidth
    canvas.height = tool.canvasHeight
    canvas.style.width = `${tool.canvasWidth}px`
    canvas.style.height = `${tool.canvasHeight}px`

    try {
      // Compile and run the tool code
      const ToolClass = compileTool(tool.code)
      toolInstanceRef.current = new ToolClass(canvas, tool.controllers)
      toolInstanceRef.current.init().then(() => {
        animate()
      }).catch((err: Error) => {
        setError(err.message)
        console.error('Tool init error:', err)
      })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Compilation error')
      console.error('Tool compilation error:', err)
    }

    function animate(timestamp = 0) {
      if (toolInstanceRef.current) {
        try {
          toolInstanceRef.current.render(timestamp)
        } catch (err: unknown) {
          console.error('Render error:', err)
          setError(err instanceof Error ? err.message : 'Render error')
        }
      }
      animationRef.current = requestAnimationFrame(animate)
    }

    return () => {
      if (animationRef.current) cancelAnimationFrame(animationRef.current)
      if (toolInstanceRef.current) toolInstanceRef.current.destroy()
    }
  }, [tool?.id, tool?.code, tool?.controllers, tool?.canvasWidth, tool?.canvasHeight])

  useEffect(() => {
    if (toolInstanceRef.current && tool) {
      toolInstanceRef.current.resize(tool.canvasWidth, tool.canvasHeight)
    }
  }, [tool?.canvasWidth, tool?.canvasHeight])

  if (!tool) {
    return (
      <div className={`${className} flex items-center justify-center bg-[#111] relative`}>
        <div className="text-center text-[#666]">
          <svg className="mx-auto mb-4 w-16 h-16 text-[#333]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01" />
          </svg>
          <p className="text-lg">No tool selected</p>
          <p className="text-sm mt-1">Create a new tool or select one from the list</p>
        </div>
      </div>
    )
  }

  return (
    <div className={`${className} relative bg-[#111] flex items-center justify-center overflow-auto p-4`}>
      {error && (
        <div className="absolute top-4 left-4 right-4 z-10 bg-red-900/50 border border-red-700 text-red-200 px-4 py-2 rounded text-sm">
          {error}
        </div>
      )}
      <div className="relative bg-black rounded shadow-2xl">
        <canvas
          ref={canvasRef}
          className="block"
          style={{
            width: tool.canvasWidth,
            height: tool.canvasHeight,
          }}
        />
      </div>
      <div className="absolute bottom-2 left-2 right-2 text-xs text-[#666] text-center">
        {tool.canvasWidth} × {tool.canvasHeight} • v{tool.version}
      </div>
    </div>
  )
}

function compileTool(code: string): any {
  // Create a safe execution context
  const exports: any = {}
  const module = { exports }
  
  // Wrap the code to capture the Tool class
  const wrappedCode = `
    ${code}
    return typeof Tool !== 'undefined' ? Tool : (typeof module.exports.default !== 'undefined' ? module.exports.default : module.exports)
  `
  
  const fn = new Function('canvas', 'controllers', 'module', 'exports', 'requestAnimationFrame', 'cancelAnimationFrame', 'HTMLCanvasElement', 'CanvasRenderingContext2D', 'Image', 'AudioContext', 'navigator', 'window', 'document', wrappedCode)
  
  return fn(
    null, // canvas - will be set in constructor
    [],   // controllers - will be set in constructor
    module,
    exports,
    requestAnimationFrame,
    cancelAnimationFrame,
    HTMLCanvasElement,
    CanvasRenderingContext2D,
    Image,
    AudioContext,
    navigator,
    window,
    document
  )
}