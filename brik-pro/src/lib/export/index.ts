// Brik Pro - Export System
import type { Tool, ExportOptions } from '@/lib/types'

export async function exportAsImage(tool: Tool, options: ExportOptions): Promise<Blob> {
  const canvas = document.querySelector(`canvas[data-tool-id="${tool.id}"]`) as HTMLCanvasElement
  if (!canvas) throw new Error('Canvas not found')

  const scale = options.imageScale || 2
  const format = options.imageFormat || 'png'
  const transparent = options.transparentBackground !== false

  const exportCanvas = document.createElement('canvas')
  exportCanvas.width = canvas.width * scale
  exportCanvas.height = canvas.height * scale
  
  const ctx = exportCanvas.getContext('2d')!
  ctx.scale(scale, scale)
  
  if (!transparent) {
    ctx.fillStyle = '#000000'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
  }
  
  ctx.drawImage(canvas, 0, 0)

  return new Promise((resolve) => {
    exportCanvas.toBlob((blob) => {
      if (blob) resolve(blob)
      else throw new Error('Failed to create image blob')
    }, `image/${format}`, 1.0)
  })
}

export async function exportAsVideo(tool: Tool, options: ExportOptions): Promise<Blob> {
  const canvas = document.querySelector(`canvas[data-tool-id="${tool.id}"]`) as HTMLCanvasElement
  if (!canvas) throw new Error('Canvas not found')

  const duration = options.videoDuration || 5 // seconds
  const fps = options.videoFps || 30
  const format = options.videoFormat || 'mp4'
  const quality = options.videoQuality || 'high'

  const stream = canvas.captureStream(fps)
  const recorder = new MediaRecorder(stream, {
    mimeType: format === 'mp4' ? 'video/mp4' : 'video/webm',
    videoBitsPerSecond: quality === 'high' ? 10000000 : quality === 'medium' ? 5000000 : 2500000,
  })

  const chunks: Blob[] = []
  recorder.ondataavailable = (e) => chunks.push(e.data)

  return new Promise((resolve, reject) => {
    recorder.onstop = () => {
      const blob = new Blob(chunks, { type: format === 'mp4' ? 'video/mp4' : 'video/webm' })
      resolve(blob)
    }
    recorder.onerror = reject

    recorder.start()
    setTimeout(() => recorder.stop(), duration * 1000)
  })
}

export function exportAsCode(tool: Tool): string {
  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${tool.name}</title>
  <style>
    body { margin: 0; background: #000; display: flex; justify-content: center; align-items: center; min-height: 100vh; }
    canvas { display: block; }
  </style>
</head>
<body>
  <canvas id="brik-canvas" width="${tool.canvasWidth}" height="${tool.canvasHeight}"></canvas>
  <script>
${tool.code}
  </script>
  <script>
    const canvas = document.getElementById('brik-canvas')
    const controllers = ${JSON.stringify(tool.controllers, null, 2)}
    const toolInstance = new Tool(canvas, controllers)
    toolInstance.init().then(() => {
      function animate(ts) {
        toolInstance.render(ts)
        requestAnimationFrame(animate)
      }
      requestAnimationFrame(animate)
    })
    window.addEventListener('resize', () => {
      toolInstance.resize(canvas.width, canvas.height)
    })
  </script>
</body>
</html>`
  return html
}

export function exportAsEmbed(tool: Tool): string {
  const baseUrl = window.location.origin
  const toolId = tool.id
  return `<iframe src="${baseUrl}/embed/${toolId}" width="${tool.canvasWidth}" height="${tool.canvasHeight}" frameborder="0" allowfullscreen></iframe>`
}

export async function downloadBlob(blob: Blob, filename: string): Promise<void> {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

export async function handleExport(tool: Tool, format: 'image' | 'video' | 'code' | 'embed', options: ExportOptions): Promise<void> {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
  const baseName = tool.name.replace(/[^a-z0-9]/gi, '-').toLowerCase()

  switch (format) {
    case 'image': {
      const blob = await exportAsImage(tool, options)
      const ext = options.imageFormat || 'png'
      await downloadBlob(blob, `${baseName}-${timestamp}.${ext}`)
      break
    }
    case 'video': {
      const blob = await exportAsVideo(tool, options)
      const ext = options.videoFormat || 'mp4'
      await downloadBlob(blob, `${baseName}-${timestamp}.${ext}`)
      break
    }
    case 'code': {
      const html = exportAsCode(tool)
      const blob = new Blob([html], { type: 'text/html' })
      await downloadBlob(blob, `${baseName}-${timestamp}.html`)
      break
    }
    case 'embed': {
      const embedCode = exportAsEmbed(tool)
      await navigator.clipboard.writeText(embedCode)
      // Show toast notification
      console.log('Embed code copied to clipboard')
      break
    }
  }
}